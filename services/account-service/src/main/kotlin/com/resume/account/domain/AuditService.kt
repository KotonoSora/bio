package com.resume.account.domain

import com.resume.account.data.AuditEntryEntity
import com.resume.account.data.AuditRepository
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Append-only audit surface (BR-11, S-8): login, link, unlink, step_up,
 * session_revoke, recovery, contact_email. The app role has no UPDATE/DELETE
 * grant; retention purge runs on the migrator role after 24 months.
 * Rows carry actor + target only - never emails, names, or tokens.
 */
@Service
class AuditService(private val audit: AuditRepository) {

    fun record(
        userId: String,
        action: String,
        target: String? = null,
        subjectUserId: String? = null,
        ipClass: String? = null,
        at: Instant = Instant.now(),
    ) {
        audit.save(
            AuditEntryEntity(
                id = Tokens.newId("aud"),
                userId = userId,
                subjectUserId = subjectUserId,
                action = action,
                target = target?.take(200),
                at = at,
                ipClass = ipClass,
            ),
        )
    }

    fun recent(userId: String): List<AuditEntryEntity> = audit.findTop50ByUserIdOrderByAtDesc(userId)
}
