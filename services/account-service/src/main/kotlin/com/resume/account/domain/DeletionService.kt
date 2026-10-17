package com.resume.account.domain

import com.resume.account.config.AccountProperties
import com.resume.account.data.DeletionRequestEntity
import com.resume.account.data.DeletionRequestRepository
import com.resume.account.events.DomainEvent
import com.resume.account.events.EventPublisher
import com.resume.account.api.AccountStatusResponse
import com.resume.account.api.DeleteStatusResponse
import com.resume.account.api.DeletionStatusResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/**
 * Owner-facing deletion lifecycle (feature 02, TL 01 state machine):
 * request -> `grace` (deactivation fan-out) -> `cancelled` (restore) or
 * `purged` (terminal, by the runner). Typed confirmation is the profile
 * username when one exists, else the literal `DELETE` (BR-01) - the request
 * commits only on exact match, and an existing grace row is idempotent
 * (no duplicate, no `purge_due_at` reset). Step-up freshness is enforced by the
 * controller via [com.resume.account.security.StepUpGuard] before this service
 * is reached.
 */
@Service
class DeletionService(
    private val repo: DeletionRequestRepository,
    private val sessions: SessionService,
    private val handoff: ProfileHandoffClient,
    private val events: EventPublisher,
    private val audit: AuditService,
    private val props: AccountProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun request(userId: String, confirmation: String, ipClass: String?): DeleteStatusResponse {
        val existing = repo.findByUserId(userId)
        if (existing?.status == "purged") throw ApiException.purgeCompleted()

        val expected = handoff.usernameOrNull(userId) ?: LITERAL_CONFIRMATION
        if (confirmation != expected) throw ApiException.confirmationMismatch()

        if (existing?.status == "grace") {
            // Idempotent: current status, no duplicate row, no reset, no re-revocation.
            return view(existing)
        }

        val now = Instant.now()
        val row = existing ?: DeletionRequestEntity(
            id = Tokens.newId("del"),
            userId = userId,
            status = "grace",
            requestedAt = now,
            purgeDueAt = now.plus(Duration.ofDays(props.deletion.graceDays)),
        )
        if (existing != null) {
            // Cancel then re-request reuses the row and resets the clock (drill 2).
            row.status = "grace"
            row.requestedAt = now
            row.purgeDueAt = now.plus(Duration.ofDays(props.deletion.graceDays))
            row.purgeStartedAt = null
            row.cancelledAt = null
            row.reminderSentAt = null
            row.certificateRef = null
            row.runId = null
        }
        repo.save(row)

        // BR-02 immediate deactivation: all sessions revoked; consumers follow events.
        sessions.revokeAllForDeletion(userId, ipClass)
        audit.record(userId = userId, action = "deletion_request", target = row.id, ipClass = ipClass)
        events.publishAfterCommit(
            DomainEvent(
                type = EventPublisher.DELETION_REQUESTED_V1,
                aggregateId = row.id,
                userId = userId,
                payload = mapOf("purgeDueAt" to row.purgeDueAt.toString()),
            ),
        )
        log.info("deletion requested user={} due={}", userId, row.purgeDueAt)
        return view(row)
    }

    /** Restore (BR-03): login + confirm, no step-up; billing never auto-resumes. */
    @Transactional
    fun cancel(userId: String, ipClass: String?): DeleteStatusResponse {
        val row = repo.findByUserId(userId) ?: throw ApiException.deleteNotRequested()
        if (row.status == "purged") throw ApiException.purgeCompleted()
        if (row.status != "grace") throw ApiException.deleteNotRequested()

        row.status = "cancelled"
        row.cancelledAt = Instant.now()
        repo.save(row)
        audit.record(userId = userId, action = "deletion_cancel", target = row.id, ipClass = ipClass)
        events.publishAfterCommit(
            DomainEvent(
                type = EventPublisher.DELETION_CANCELLED_V1,
                aggregateId = row.id,
                userId = userId,
                payload = mapOf("purgeDueAt" to row.purgeDueAt.toString()),
            ),
        )
        log.info("deletion cancelled user={}", userId)
        return view(row)
    }

    @Transactional(readOnly = true)
    fun status(userId: String): DeleteStatusResponse {
        val row = repo.findByUserId(userId) ?: return DeleteStatusResponse("none", null, null, null)
        if (row.status == "purged") throw ApiException.purgeCompleted()
        return view(row)
    }

    /** Mesh parity read (S-5): `active | deactivated | purged` + the purge date. */
    @Transactional(readOnly = true)
    fun accountStatus(userId: String): AccountStatusResponse {
        val row = repo.findByUserId(userId) ?: return AccountStatusResponse("active", null)
        return when (row.status) {
            "grace" -> AccountStatusResponse("deactivated", row.purgeDueAt)
            "purged" -> AccountStatusResponse("purged", row.purgeDueAt)
            else -> AccountStatusResponse("active", null) // cancelled: active again, no purge pending
        }
    }

    /** Audited support read (BR-08/S-8): ops scope + actor + target, 404 when absent. */
    @Transactional
    fun opsStatus(userId: String, actor: String, ipClass: String?): DeletionStatusResponse {
        val row = repo.findByUserId(userId) ?: throw ApiException.notFound("deletion_not_found", "No deletion record for that account.")
        audit.record(userId = actor, action = "deletion_status_read", subjectUserId = userId, target = row.id, ipClass = ipClass)
        return DeletionStatusResponse(
            userId = userId,
            status = row.status,
            requestedAt = row.requestedAt,
            purgeDueAt = row.purgeDueAt,
            purgedAt = row.purgedAt,
            certificateRef = row.certificateRef,
        )
    }

    private fun view(row: DeletionRequestEntity): DeleteStatusResponse = DeleteStatusResponse(
        status = row.status,
        requestedAt = row.requestedAt,
        purgeDueAt = row.purgeDueAt,
        certificateRef = row.certificateRef,
    )

    companion object {
        const val LITERAL_CONFIRMATION = "DELETE"
    }
}
