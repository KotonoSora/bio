package com.resume.account.domain.concierge

import com.resume.account.api.ApprovalResponse
import com.resume.account.api.TaskResponse
import com.resume.account.config.ConciergeProperties
import com.resume.account.data.ConciergeApprovalEntity
import com.resume.account.data.ConciergeApprovalRepository
import com.resume.account.data.ConciergeContractEntity
import com.resume.account.data.ConciergeContractRepository
import com.resume.account.data.ConciergeTaskEntity
import com.resume.account.data.ConciergeTaskRepository
import com.resume.account.domain.ApiException
import com.resume.account.domain.AuditService
import com.resume.account.domain.Tokens
import com.resume.account.security.RlsContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Task board (TL 01 `TaskBoardService`). Two normative gates live here:
 * the closed-menu/volume check before any row is written (BR-01, BR-12) and
 * the dispatch gate's truth - an approval is valid only while its contract is
 * `active` and no transition is pending (BR-03, fail-closed). SLA misses are
 * recorded durably at read/transition time: a miss is never silently cleared
 * (BR-06).
 */
@Service
class ConciergeTaskBoard(
    private val contracts: ConciergeContractRepository,
    private val tasks: ConciergeTaskRepository,
    private val approvals: ConciergeApprovalRepository,
    private val staffing: ConciergeStaffing,
    private val props: ConciergeProperties,
    private val limiter: ConciergeRateLimiter,
    private val audit: AuditService,
) {

    private val halting = setOf("refused", "cancelled")

    /** Client board read: applies SLA clocks, returns the contract's tasks. */
    @Transactional
    fun listForClient(userId: String): List<ConciergeTaskEntity> {
        // Any state: the board keeps rendering the history after exit (BR-10/BR-11).
        val contract = contracts.findFirstByUserIdOrderByCreatedAtDesc(userId) ?: return emptyList()
        val rows = tasks.listByContract(contract.id)
        val now = Instant.now()
        rows.forEach { applySla(it, contract, now) }
        rows.forEach(tasks::save)
        return rows
    }

    @Transactional
    fun request(actor: String, kind: String, notes: String?): TaskResponse {
        val contract = contracts.findLive(actor) ?: throw ConciergeErrors.contractNotFound()
        limiter.contractRequest(contract.id)
        if (contract.status != "active" || contract.pendingTransition != null) {
            throw ConciergeErrors.approvalsClosed()
        }
        val task = writeTask(contract, actor, kind, notes, origin = "client")
        audit.record(actor, "concierge.task_requested", target = task.id, subjectUserId = contract.userId)
        return response(task)
    }

    /** Internal `scheduled`-origin creation (TL 03: assigned operator). */
    @Transactional
    fun scheduled(actor: String?, userId: String?, kind: String, notes: String?): TaskResponse = RlsContext.asService {
        staffing.requireStaff(actor)
        limiter.ops(actor ?: "svc")
        val contract = resolveTarget(userId)
        if (contract.status != "active" || contract.pendingTransition != null) {
            throw ConciergeErrors.approvalsClosed()
        }
        val task = writeTask(contract, contract.userId, kind, notes, origin = "scheduled")
        audit.record(actor ?: "svc", "concierge.task_scheduled", target = task.id, subjectUserId = contract.userId)
        task.toResponse(slaMiss(task))
    }

    /**
     * Mesh tooling names the client explicitly; the `userId` stays optional for
     * agency-wide drills where exactly one live contract exists (0 or >1 live
     * contracts answer `422 validation_failed` - never a guess).
     */
    private fun resolveTarget(userId: String?): ConciergeContractEntity {
        if (!userId.isNullOrBlank()) {
            return contracts.findLive(userId) ?: throw ConciergeErrors.contractNotFound()
        }
        val live = contracts.listLive()
        if (live.size != 1) throw ApiException.validation("userId is required when more than one contract is live.")
        return live.first()
    }

    private fun writeTask(
        contract: ConciergeContractEntity,
        ownerId: String,
        kind: String,
        notes: String?,
        origin: String,
    ): ConciergeTaskEntity {
        val item = contract.sow.firstOrNull { it.kind == kind }
            ?: throw ConciergeErrors.offMenu(props.referralUrl)
        if (item.volume != null) {
            val monthStart = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC)
            val used = tasks.countVolumeThisMonth(contract.id, kind, monthStart)
            if (used >= item.volume) throw ConciergeErrors.volumeExceeded()
        }
        return tasks.save(
            ConciergeTaskEntity(
                id = Tokens.newId("cnc_task"),
                contractId = contract.id,
                userId = ownerId,
                kind = kind,
                status = "requested",
                origin = origin,
                notes = notes?.take(500),
            ),
        )
    }

    /** Dashboard approval (BR-04): one record per item; re-approve returns the record. */
    @Transactional
    fun approve(actor: String, taskId: String, scope: String): ApprovalResponse {
        val task = tasks.findOwned(taskId, actor) ?: throw ConciergeErrors.taskNotFound()
        approvals.listByTask(task.id).firstOrNull()?.let { return it.toResponse() }
        val contract = contracts.findById(task.contractId).orElse(null)
            ?: throw ConciergeErrors.contractNotFound()
        if (contract.status != "active" || contract.pendingTransition != null || contract.handoff != null) {
            throw ConciergeErrors.approvalsClosed()
        }
        if (task.status != "requested") {
            throw ConciergeErrors.transitionConflict("Only a requested item can be approved.")
        }
        val recorded = approvals.save(
            ConciergeApprovalEntity(
                id = Tokens.newId("cnc_apr"),
                contractId = contract.id,
                taskId = task.id,
                userId = task.userId,
                scope = requireScope(scope),
                channel = "dashboard",
                approvedBy = actor,
            ),
        )
        task.status = "approved"
        task.approvedAt = Instant.now()
        task.updatedAt = Instant.now()
        tasks.save(task)
        audit.record(actor, "concierge.task_approved", target = task.id, subjectUserId = task.userId)
        return recorded.toResponse()
    }

    /** Operator transitions (BR-03/BR-10): only approved work starts, work is halted while pending. */
    @Transactional
    fun operatorStatus(
        actor: String?,
        taskId: String,
        status: String,
        workRefs: List<String>,
    ): TaskResponse = RlsContext.asService {
        staffing.requireStaff(actor)
        limiter.ops(actor ?: "svc")
        val task = tasks.findById(taskId).orElse(null) ?: throw ConciergeErrors.taskNotFound()
        val contract = contracts.findById(task.contractId).orElse(null)
            ?: throw ConciergeErrors.contractNotFound()
        // BR-10: work in flight continues while a transition merely *waits* for
        // the counterpart; it halts once the change commits (paused/exited) or
        // once the exit handoff has started (BR-11).
        if (contract.status != "active" || contract.handoff != null) {
            throw ConciergeErrors.transitionConflict(
                "Concierge work is halted while this contract is ${contract.status} or the handoff is underway.",
            )
        }
        when (status) {
            "in_progress" -> {
                if (task.status != "approved") {
                    throw ConciergeErrors.transitionConflict("Work starts only after the client approves the item.")
                }
                task.status = "in_progress"
                task.startedAt = Instant.now()
            }

            "done" -> {
                if (task.status != "in_progress") {
                    throw ConciergeErrors.transitionConflict("Only work in progress can be completed.")
                }
                val refs = workRefs.map(String::trim).filter(String::isNotEmpty)
                if (refs.isEmpty()) {
                    throw ApiException.validation("Attach a work reference before completing this item.")
                }
                task.status = "done"
                task.completedAt = Instant.now()
                task.workRefs = refs.toMutableList()
            }

            else -> throw ApiException.validation("status must be in_progress or done.")
        }
        task.operatorId = actor
        task.updatedAt = Instant.now()
        tasks.save(task)
        audit.record(actor ?: "svc", "concierge.task_$status", target = task.id, subjectUserId = task.userId)
        response(task)
    }

    /** Refusal drill (BR-07/BR-12): reason recorded on the row, referral where applicable. */
    @Transactional
    fun refuse(actor: String?, taskId: String, reason: String, referral: String?): TaskResponse =
        RlsContext.asService {
            staffing.requireStaff(actor)
            limiter.ops(actor ?: "svc")
            if (reason !in REFUSAL_REASONS) {
                throw ApiException.validation("reason must be a boundary, off-menu, or over-volume category.")
            }
            val task = tasks.findById(taskId).orElse(null) ?: throw ConciergeErrors.taskNotFound()
            if (task.status in halting || task.status == "done") {
                throw ConciergeErrors.transitionConflict("This item is already ${task.status}.")
            }
            task.status = "refused"
            task.refusalReason = reason
            task.referral = referral?.take(300)
            task.operatorId = actor
            task.updatedAt = Instant.now()
            tasks.save(task)
            audit.record(actor ?: "svc", "concierge.task_refused", target = task.id, subjectUserId = task.userId)
            task.toResponse(slaMiss(task))
        }

    /** The dispatch gate's truth (TL 03, normative): latest record + active contract only. */
    @Transactional
    fun approvalCheck(approvalId: String): ApprovalCheck {
        val approval = approvals.findById(approvalId).orElse(null)
            ?: throw ApiException.notFound("approval_not_found", "No such approval.")
        val contract = contracts.findById(approval.contractId).orElse(null)
            ?: throw ApiException.notFound("approval_not_found", "No such approval.")
        val latest = latestFor(approval)
        val valid = contract.status == "active" &&
            contract.pendingTransition == null &&
            latest?.id == approval.id
        return ApprovalCheck(valid, approval.taskId, approval.scope, contract.status, approval.approvedAt)
    }

    private fun latestFor(approval: ConciergeApprovalEntity): ConciergeApprovalEntity? =
        if (approval.taskId != null) {
            approvals.listByTask(approval.taskId!!).firstOrNull()
        } else {
            approvals.listSetup(approval.contractId).firstOrNull()
        }

    /** Approval history for a board row (BA 04): newest record first, never edited. */
    @Transactional(readOnly = true)
    fun approvalsFor(taskId: String): List<ConciergeApprovalEntity> =
        RlsContext.asService { approvals.listByTask(taskId) }

    /**
     * BA 05 "Missed SLA window": windows run from `requested_at` (response) and
     * `approved_at` (completion); the records persist so a late fulfilment never
     * erases the miss (no silent remediation, BR-06).
     */
    private fun applySla(task: ConciergeTaskEntity, contract: ConciergeContractEntity, now: Instant) {
        if (task.status in halting) return
        val tier = props.slaTiers[contract.slaTier] ?: return
        tier.responseMinutes?.let { minutes ->
            if (task.missedResponseAt == null && task.approvedAt == null &&
                now > task.requestedAt.plusSeconds(minutes * 60)
            ) {
                task.missedResponseAt = now
            }
        }
        tier.completionMinutes?.let { minutes ->
            val approvedAt = task.approvedAt
            if (task.missedCompletionAt == null && approvedAt != null && task.status != "done" &&
                now > approvedAt.plusSeconds(minutes * 60)
            ) {
                task.missedCompletionAt = now
            }
        }
    }

    /** Board-row view (BA 04 work log): SLA state + operator name + approval history. */
    private fun response(task: ConciergeTaskEntity): TaskResponse =
        task.toResponse(slaMiss(task), staffing.staffName(task.operatorId), approvalsFor(task.id))

    /** `response` | `completion` | null - the latest recorded miss wins. */
    fun slaMiss(task: ConciergeTaskEntity): String? = when {
        task.missedCompletionAt != null -> "completion"
        task.missedResponseAt != null -> "response"
        else -> null
    }

    private fun requireScope(scope: String): String {
        val clean = scope.trim()
        if (clean.isEmpty()) throw ApiException.validation("Record what exactly is approved.")
        return clean.take(500)
    }

    data class ApprovalCheck(
        val valid: Boolean,
        val taskId: String?,
        val scope: String,
        val contractStatus: String,
        val approvedAt: Instant,
    )

    companion object {
        /** Closed refusal set (BA 05): off-menu, over-volume, and the boundary categories. */
        val REFUSAL_REASONS = setOf(
            "off_menu",
            "over_volume",
            "boundary_billing",
            "boundary_policy",
            "boundary_contact",
            "boundary_content",
            "client_cancel",
        )
    }
}
