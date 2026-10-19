package com.resume.account.domain.concierge

import com.resume.account.api.ApprovalResponse
import com.resume.account.config.ConciergeProperties
import com.resume.account.data.ConciergeApprovalEntity
import com.resume.account.data.ConciergeApprovalRepository
import com.resume.account.data.ConciergeContractRepository
import com.resume.account.data.ConciergeTaskRepository
import com.resume.account.data.UserRepository
import com.resume.account.domain.ApiException
import com.resume.account.domain.AuditService
import com.resume.account.domain.Mailer
import com.resume.account.domain.Tokens
import com.resume.account.security.RlsContext
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey

/**
 * Approval ledger (TL 01 `ApprovalService`). Records are append-only: the
 * dashboard path and the signed-email path only ever insert, and a newer record
 * for the same task/contract is what supersedes an older one (BA 05). Signed
 * links are 15-minute JWTs with a single-use `jti` in Redis db 7 (S-9); expiry,
 * forgery, and replay all answer `422 signature_invalid`.
 */
@Service
class ConciergeApprovalService(
    private val approvals: ConciergeApprovalRepository,
    private val contracts: ConciergeContractRepository,
    private val tasks: ConciergeTaskRepository,
    private val users: UserRepository,
    private val staffing: ConciergeStaffing,
    private val props: ConciergeProperties,
    private val limiter: ConciergeRateLimiter,
    private val audit: AuditService,
    private val mailer: Mailer,
    private val redis: StringRedisTemplate,
) {

    /** Setup-plan approval (BA 03 step 2, BA 04): draft-only, immutable, idempotent. */
    @Transactional
    fun approveSetupPlan(actor: String, scope: String): ApprovalResponse {
        val contract = contracts.findLive(actor) ?: throw ConciergeErrors.contractNotFound()
        approvals.listSetup(contract.id).firstOrNull()?.let { return it.toResponse() }
        if (contract.status != "draft") {
            throw ConciergeErrors.approvalsClosed()
        }
        val clean = scope.trim()
        if (clean.isEmpty()) throw ApiException.validation("Record what exactly is approved.")
        val recorded = approvals.save(
            ConciergeApprovalEntity(
                id = Tokens.newId("cnc_apr"),
                contractId = contract.id,
                taskId = null,
                userId = contract.userId,
                scope = clean.take(500),
                channel = "dashboard",
                approvedBy = actor,
            ),
        )
        audit.record(actor, "concierge.setup_plan_approved", target = contract.id, subjectUserId = contract.userId)
        return recorded.toResponse()
    }

    /**
     * Signed-email approval link (S-9): issued by an operator/owner for a
     * requested item, 15-minute TTL, single-use jti. The link travels by mail
     * (log transport in dev); the mesh route also returns it so agency tooling
     * can re-deliver it - the caller is gated and audited either way.
     */
    @Transactional
    fun issueSignedLink(actor: String?, taskId: String): Pair<String, Instant> = RlsContext.asService {
        staffing.requireOwner(actor)
        limiter.ops(actor ?: "svc")
        val task = tasks.findById(taskId).orElse(null) ?: throw ConciergeErrors.taskNotFound()
        val contract = contracts.findById(task.contractId).orElse(null)
            ?: throw ConciergeErrors.contractNotFound()
        if (contract.status != "active" || contract.pendingTransition != null) {
            throw ConciergeErrors.approvalsClosed()
        }
        if (task.status != "requested") {
            throw ConciergeErrors.transitionConflict("Only a requested item can be approved.")
        }
        val expiresAt = Instant.now().plusSeconds(props.linkTtlMinutes * 60)
        val jti = Tokens.random(16)
        val token = Jwts.builder()
            .subject(task.userId)
            .claim("taskId", task.id)
            .claim("contractId", contract.id)
            .claim("scope", "Approve item: ${task.kind}")
            .id(jti)
            .expiration(Date.from(expiresAt))
            .signWith(linkKey())
            .compact()
        val email = users.findById(task.userId).orElse(null)?.email
        mailer.sendConciergeApprovalLink(email, task.kind)
        audit.record(actor ?: "svc", "concierge.signed_link_issued", target = task.id, subjectUserId = task.userId)
        token to expiresAt
    }

    /** Callback confirmation: signature + expiry + single-use jti, then an appended record. */
    @Transactional
    fun confirmSignedLink(token: String): ApprovalResponse {
        val claims = try {
            Jwts.parser().verifyWith(linkKey()).build().parseSignedClaims(token).payload
        } catch (_: Exception) {
            throw ConciergeErrors.signatureInvalid()
        }
        val taskId = claims["taskId"] as? String
        val contractId = claims["contractId"] as? String
        val jti = claims.id
        val subject = claims.subject
        if (taskId.isNullOrBlank() || contractId.isNullOrBlank() || jti.isNullOrBlank() || subject.isNullOrBlank()) {
            throw ConciergeErrors.signatureInvalid()
        }
        val task = RlsContext.asService { tasks.findById(taskId).orElse(null) }
            ?: throw ConciergeErrors.taskNotFound()
        val contract = RlsContext.asService { contracts.findById(contractId).orElse(null) }
            ?: throw ConciergeErrors.contractNotFound()
        if (contract.status != "active" || contract.pendingTransition != null) {
            throw ConciergeErrors.approvalsClosed()
        }
        // Single-use jti first: a replay of the link must be rejected even when
        // its first use already produced the approval - the idempotent return
        // below would otherwise mask the replay as a 200 (S-9/BR-04).
        val consumed = try {
            redis.opsForValue().setIfAbsent(
                "concierge:jti:$jti",
                "1",
                Duration.ofMinutes(props.linkTtlMinutes + 5),
            )
        } catch (_: Exception) {
            null
        }
        if (consumed == false) throw ConciergeErrors.signatureInvalid()
        RlsContext.asService { approvals.listByTask(task.id).firstOrNull() }?.let { return it.toResponse() }
        if (task.status != "requested") {
            throw ConciergeErrors.transitionConflict("Only a requested item can be approved.")
        }

        val scope = (claims["scope"] as? String)?.takeIf { it.isNotBlank() } ?: "Approve item: ${task.kind}"
        val recorded = RlsContext.asService {
            val saved = approvals.save(
                ConciergeApprovalEntity(
                    id = Tokens.newId("cnc_apr"),
                    contractId = contract.id,
                    taskId = task.id,
                    userId = task.userId,
                    scope = scope.take(500),
                    channel = "signed_email",
                    approvedBy = subject,
                    jti = jti,
                ),
            )
            task.status = "approved"
            task.approvedAt = Instant.now()
            task.updatedAt = Instant.now()
            tasks.save(task)
            audit.record(subject, "concierge.task_approved", target = task.id, subjectUserId = task.userId)
            saved
        }
        return recorded.toResponse()
    }

    @Transactional(readOnly = true)
    fun approvalsFor(taskId: String): List<ConciergeApprovalEntity> = approvals.listByTask(taskId)

    /** Recorded setup-plan approval (BA 04 read-only history), if any. */
    @Transactional(readOnly = true)
    fun setupApprovalFor(contractId: String): ConciergeApprovalEntity? =
        approvals.listSetup(contractId).firstOrNull()

    /** Dev fallback keeps links stable within a dev run; production sets CONCIERGE_LINK_SIGNING_KEY. */
    private fun linkKey(): SecretKey =
        Keys.hmacShaKeyFor(
            (props.linkSigningKey.ifBlank { "resume-builder-dev-concierge-link" }).toByteArray(),
        )
}
