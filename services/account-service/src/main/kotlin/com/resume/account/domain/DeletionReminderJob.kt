package com.resume.account.domain

import com.resume.account.config.AccountProperties
import com.resume.account.data.DeletionRequestRepository
import com.resume.account.security.RlsContext
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * Day-23 reminder (T-07, NFR-03/Q-04): exactly one transactional email naming
 * the purge date, to the account email only, never marketing. DB
 * `reminder_sent_at` is truth (single-send guard); the `acct:delrem:{userId}`
 * mirror (25 h) bounds same-day re-runs. A failed send is retried by the next
 * pass - the purge date never moves (degrade matrix).
 */
@Component
class DeletionReminderJob(
    private val props: AccountProperties,
    private val repo: DeletionRequestRepository,
    private val identities: IdentityService,
    private val mail: Mailer,
    private val audit: AuditService,
    private val redis: StringRedisTemplate,
    meters: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val sent = meters.counter("deletion.reminder.sent")
    private val failed = meters.counter("deletion.reminder.failed")
    private val skipped = meters.counter("deletion.reminder.skipped")

    @Scheduled(cron = "\${account.deletion.reminder-cron:0 45 3 * * *}")
    fun run() {
        val now = Instant.now()
        val cutoff = now.minus(Duration.ofDays(props.deletion.reminderAfterDays))
        val due = RlsContext.asService { repo.findReminderDue(cutoff, now) }
        for (row in due) {
            if (mirrorHeld(row.userId)) {
                skipped.increment()
                continue
            }
            val email = RlsContext.asService { identities.emailView(row.userId)?.email }
            if (email.isNullOrBlank()) {
                failed.increment()
                log.warn("deletion reminder skipped user={}: no account email", row.userId)
                continue
            }
            val accepted = try {
                mail.sendDeletionReminder(email, row.purgeDueAt)
            } catch (ex: Exception) {
                log.warn("deletion reminder transport failed user={}: {}", row.userId, ex.message)
                false
            }
            if (!accepted) {
                failed.increment()
                continue // next pass retries; purge date never moves
            }
            RlsContext.asService { repo.markReminderSent(row.userId, Instant.now()) }
            holdMirror(row.userId)
            audit.record(userId = row.userId, action = "deletion_reminder")
            sent.increment()
            log.info("deletion reminder sent user={} due={}", row.userId, row.purgeDueAt)
        }
    }

    private fun mirrorHeld(userId: String): Boolean = try {
        redis.hasKey("acct:delrem:$userId")
    } catch (_: Exception) {
        false // mirror only; the DB guard is truth
    }

    private fun holdMirror(userId: String) {
        try {
            redis.opsForValue().set("acct:delrem:$userId", "1", Duration.ofHours(25))
        } catch (_: Exception) {
            // no-op
        }
    }
}
