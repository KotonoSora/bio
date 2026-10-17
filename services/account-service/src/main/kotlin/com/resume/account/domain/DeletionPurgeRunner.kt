package com.resume.account.domain

import com.resume.account.config.AccountProperties
import com.resume.account.data.DeletionRequestEntity
import com.resume.account.data.DeletionRequestRepository
import com.resume.account.events.DomainEvent
import com.resume.account.events.EventPublisher
import com.resume.account.security.RlsContext
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Day-30 purge runner (TL 01 D-4, T-04/T-05/T-06/T-09). One pass = daily cron
 * or the ops re-run command (same entry, idempotent). Redis single-flight
 * (`acct:job:purge`, SET NX PX + token-checked release): no lock store or held
 * lock means the pass is skipped with a counter - never two runners. Redis down
 * never fails accounts; the delete handlers and the per-account DB claim stay
 * the truth (S-3).
 *
 * Per account: atomic DB claim -> in-process handler -> registered
 * participants (bounded retries, same `runId`) -> certificate (verified
 * `_tmp` -> atomic rename) -> `purged` flip + `account.purged.v1`. Any failure
 * keeps `grace` (no partial purged, no certificate) and the next pass retries;
 * attempts older than 24 h page ops with userId/runId/failing participant.
 */
@Component
class DeletionPurgeRunner(
    private val props: AccountProperties,
    private val repo: DeletionRequestRepository,
    private val self: AccountPurgeHandler,
    private val participants: PurgeParticipantClient,
    private val certificates: CertificateStore,
    private val events: EventPublisher,
    private val audit: AuditService,
    private val redis: StringRedisTemplate,
    meters: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val skipped = meters.counter("deletion.purge.skipped")
    private val completed = meters.counter("deletion.purge.completed")
    private val failed = meters.counter("deletion.purge.failed")
    private val stuck = meters.counter("deletion.purge.stuck")
    private val residual = meters.counter("deletion.probe.residual")
    private val probeDegraded = meters.counter("deletion.probe.degraded")

    @Scheduled(cron = "\${account.deletion.purge-cron:0 30 3 * * *}")
    fun scheduled() = run()

    fun run() {
        val runId = Instant.now().truncatedTo(ChronoUnit.MINUTES).toString()
        val acquired = try {
            redis.opsForValue().setIfAbsent(LOCK_KEY, runId, LOCK_TTL) == true
        } catch (ex: Exception) {
            log.warn("purge runner skipped: lock store unavailable ({})", ex.message)
            skipped.increment()
            return
        }
        if (!acquired) {
            log.info("purge runner skipped: another pass holds {}", LOCK_KEY)
            skipped.increment()
            return
        }
        try {
            certificates.sweepTemp(Instant.now().minus(Duration.ofHours(24)))
            val due = RlsContext.asService { repo.findDue(Instant.now()) }
            log.info("purge runner start run={} due={}", runId, due.size)
            due.forEach { attempt(it, runId) }
            alertStuck()
        } finally {
            releaseLock(runId)
        }
    }

    private fun attempt(row: DeletionRequestEntity, runId: String) {
        val now = Instant.now()
        val claimed = try {
            RlsContext.asService { repo.claim(row.id, runId, now, now.minus(CLAIM_FLOOR)) }
        } catch (ex: Exception) {
            log.warn("purge claim failed user={}: {}", row.userId, ex.message)
            return
        }
        if (claimed != 1) {
            log.info("purge skipped user={}: recent attempt or run in progress", row.userId)
            skipped.increment()
            return
        }

        val tables = linkedMapOf<String, Long>()
        var files = 0L
        try {
            val own = RlsContext.asService { self.purge(row.userId, runId) }
            tables += own.tables
            files += own.files
            for (participant in activeParticipants()) {
                val outcome = participants.purge(participant, row.userId, runId)
                outcome.tables.forEach { (table, count) -> tables.merge(table, count, Long::plus) }
                files += outcome.files
            }
        } catch (ex: Exception) {
            failed.increment()
            rememberFailure(row.userId, runId, ex.message ?: ex::class.simpleName ?: "error")
            audit.record(userId = row.userId, action = "deletion_purge_failed", target = runId)
            log.warn("purge incomplete user={} run={}: {}", row.userId, runId, ex.message)
            return
        }

        val certificate = PurgeCertificate(
            userId = row.userId,
            runId = runId,
            startedAt = now,
            finishedAt = Instant.now(),
            tables = tables.toMap(),
            files = files,
            legalHold = props.deletion.legalHold,
        )
        val ref = try {
            certificates.write(certificate)
        } catch (ex: Exception) {
            failed.increment()
            rememberFailure(row.userId, runId, ex.message ?: "certificate_write_failed")
            audit.record(userId = row.userId, action = "deletion_purge_failed", target = runId)
            log.warn("certificate write failed user={} run={}: {}", row.userId, runId, ex.message)
            return
        }

        val flipped = RlsContext.asService { repo.markPurged(row.id, runId, Instant.now(), ref) }
        if (flipped != 1) {
            log.warn("purge raced a restore user={} run={}; certificate kept at {}", row.userId, runId, ref)
            return
        }
        forgetFailure(row.userId)
        completed.increment()
        audit.record(userId = row.userId, action = "deletion_purge", target = runId)
        events.publish(
            DomainEvent(
                type = EventPublisher.PURGED_V1,
                aggregateId = row.id,
                userId = row.userId,
                payload = mapOf("certificateRef" to ref),
            ),
        )
        log.info("purge complete user={} run={} tables={} files={}", row.userId, runId, tables, files)
    }

    /** Stuck pass (S-9): attempts past the 24 h window page ops with the last failure. */
    private fun alertStuck() {
        val staleBefore = Instant.now().minus(Duration.ofHours(props.deletion.stuckAfterHours))
        val stuckRows = RlsContext.asService { repo.findStuck(staleBefore) }
        for (row in stuckRows) {
            stuck.increment()
            log.warn(
                "purge stuck user={} run={} since={} lastFailure={}",
                row.userId, row.runId, row.purgeStartedAt, readFailure(row.userId),
            )
        }
    }

    /** Weekly residual probe (BR-10, T-09): honest counts, never a delete. */
    @Scheduled(cron = "\${account.deletion.probe-cron:0 0 4 * * 1}")
    fun weeklyProbe() {
        val rows = RlsContext.asService { repo.findByStatus("purged") }
            .filter { !it.certificateRef.isNullOrBlank() }
        for (row in rows) {
            var rowsTotal = 0L
            var filesTotal = 0L
            var degraded = false
            try {
                val own = RlsContext.asService { self.probe(row.userId) }
                rowsTotal += own.residualRows.values.sum()
                filesTotal += own.residualFiles
            } catch (ex: Exception) {
                degraded = true
                log.warn("probe degraded (account-service) user={}: {}", row.userId, ex.message)
            }
            for (participant in activeParticipants()) {
                try {
                    val probe = participants.probe(participant, row.userId)
                    rowsTotal += probe.residualRows.values.sum()
                    filesTotal += probe.residualFiles
                } catch (ex: Exception) {
                    degraded = true
                    log.warn("probe degraded ({}) user={}: {}", participant.name, row.userId, ex.message)
                }
            }
            if (degraded) probeDegraded.increment()
            if (rowsTotal > 0 || filesTotal > 0) {
                residual.increment()
                log.warn(
                    "residual data after purge user={} ref={} rows={} files={}",
                    row.userId, row.certificateRef, rowsTotal, filesTotal,
                )
            }
        }
    }

    private fun activeParticipants(): List<AccountProperties.Participant> =
        props.deletion.participants.filter { it.name.isNotBlank() && it.baseUrl.isNotBlank() }

    private fun rememberFailure(userId: String, runId: String, reason: String) {
        try {
            redis.opsForValue().set(failureKey(userId), "$runId|$reason".take(300), Duration.ofDays(7))
        } catch (_: Exception) {
            // ops memory is best effort; the stuck counter still pages
        }
    }

    private fun readFailure(userId: String): String? = try {
        redis.opsForValue().get(failureKey(userId))
    } catch (_: Exception) {
        null
    }

    private fun forgetFailure(userId: String) {
        try {
            redis.delete(failureKey(userId))
        } catch (_: Exception) {
            // no-op
        }
    }

    private fun releaseLock(runId: String) {
        try {
            redis.execute(
                DefaultRedisScript(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
                    Long::class.java,
                ),
                listOf(LOCK_KEY),
                runId,
            )
        } catch (ex: Exception) {
            log.warn("purge lock release degraded (TTL covers): {}", ex.message)
        }
    }

    private fun failureKey(userId: String) = "acct:purge:lastfail:$userId"

    companion object {
        const val LOCK_KEY = "acct:job:purge"

        /** Longer than any healthy pass (participant timeout x retries x roster), shorter than the day. */
        val LOCK_TTL: Duration = Duration.ofMinutes(55)

        /** A row attempted within this floor loses the claim: another pass is treating it. */
        val CLAIM_FLOOR: Duration = Duration.ofHours(1)
    }
}
