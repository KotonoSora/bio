package com.resume.profile.domain.domains

import com.resume.profile.data.CustomDomainRepository
import com.resume.profile.security.RlsUserHolder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Feature 04 worker (D-4): pending poll cadence (30 s -> 6 h), 24 h staggered
 * live rechecks, hold expiry (30 d -> unverified + fresh round), and
 * auto-release (14 d failing control). Worker rows are always handled under
 * the server-set `svc` scope - the context alone is never an authorization;
 * the service methods apply explicit predicates (baseline 08). Degrade: a
 * check failure leaves the row in truthful state - verification never
 * succeeds without a TXT match (fail-closed; 88 D-6).
 */
@Component
class DomainScheduler(
    private val repo: CustomDomainRepository,
    private val service: DomainsService,
    @Value("\${domains.worker.enabled:true}") private val enabled: Boolean,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Pending poll sweep every 30 s (the first backoff step; NFR-02). */
    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    fun pollPendingRounds() {
        if (!enabled) return
        runWorker("pending") {
            val now = Instant.now()
            repo.findByStatus(CustomDomain.State.PENDING)
                .filter { service.nextPollDue(it, now) }
                .forEach { service.pollPending(it, now) }
        }
    }

    /** Recheck / hold / auto-release sweeps every 60 s; the stagger gates the rows. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    fun sweep() {
        if (!enabled) return
        runWorker("recheck") {
            val now = Instant.now()
            repo.findByStatus(CustomDomain.State.VERIFIED)
                .filter { service.isRecheckDue(it, now) }
                .forEach { service.workerRecheck(it, now) }
        }
        runWorker("hold-expiry") {
            val now = Instant.now()
            repo.findByStatus(CustomDomain.State.SUSPENDED)
                .any { service.releaseHoldExpired(it, now) != null }
        }
        runWorker("auto-release") {
            val now = Instant.now()
            repo.findByStatus(CustomDomain.State.UNVERIFIED)
                .any { service.autoRelease(it, now) }
        }
    }

    private inline fun runWorker(stage: String, crossinline block: () -> Unit) {
        try {
            RlsUserHolder.asService { block() }
        } catch (ex: Exception) {
            log.warn("domain sweep {} degraded: {}", stage, ex.message)
        }
    }
}
