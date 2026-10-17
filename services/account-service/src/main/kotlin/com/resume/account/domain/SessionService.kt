package com.resume.account.domain

import com.resume.account.config.AccountProperties
import com.resume.account.data.SessionEntity
import com.resume.account.data.SessionRepository
import com.resume.account.security.RlsContext
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/** Owner session list projection (keeps the api layer free of entities). */
data class SessionView(
    val sid: String,
    val deviceLabel: String?,
    val ipClass: String?,
    val createdAt: Instant,
    val lastActiveAt: Instant,
)

/** Edge rebuild projection for the gateway introspect contract (TL 03). */
data class SessionIntrospection(
    val userId: String,
    val authTime: Instant,
    val absoluteExpiresAt: Instant,
)

/**
 * Session registry is truth; edge key is cache (D-2). Idle 7 d sliding /
 * absolute 90 d / quota 50 (BR-06, NFR-04, NFR-17); revocations delete the edge
 * key so the gateway stops validating within the 60 s JWT bound (NFR-03).
 * `auth_time` is the step-up freshness source (BR-07) - refreshed only by an
 * SSO re-dance.
 */
@Service
class SessionService(
    private val repo: SessionRepository,
    private val edge: EdgeSessionStore,
    private val audit: AuditService,
    private val touchCoalesce: StringRedisTemplate,
    private val props: AccountProperties,
    meters: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val sweeperRevoked = meters.counter("session.sweeper.revoked")
    private val sweeperExtended = meters.counter("session.sweeper.extended")
    private val quotaEvicted = meters.counter("session.quota.evicted")

    private val idle: Duration get() = Duration.ofDays(props.session.idleDays)
    private val absolute: Duration get() = Duration.ofDays(props.session.absoluteDays)

    @Transactional
    fun issue(userId: String, deviceLabel: String?, ipClass: String?, authTime: Instant = Instant.now()): SessionEntity {
        quotaMakeRoom(userId)
        val sid = Tokens.newSid()
        val now = Instant.now()
        val session = SessionEntity(
            sid = sid,
            userId = userId,
            createdAt = now,
            lastActiveAt = now,
            authTime = authTime,
            idleExpiresAt = now.plus(idle),
            absoluteExpiresAt = now.plus(absolute),
            deviceLabel = deviceLabel,
            ipClass = ipClass,
        )
        repo.save(session)
        edge.write(sid, userId, authTime, session.absoluteExpiresAt, ttlFor(session, now))
        return session
    }

    /** Loads the caller's session; expired or revoked rows resolve to null (fail closed). */
    @Transactional(readOnly = true)
    fun current(sid: String, userId: String): SessionEntity? {
        val session = repo.findById(sid).orElse(null) ?: return null
        if (session.userId != userId || session.revokedAt != null) return null
        val now = Instant.now()
        if (!now.isBefore(session.idleExpiresAt) || !now.isBefore(session.absoluteExpiresAt)) return null
        return session
    }

    /** Edge rebuild read (no owner predicate; caller runs under the `svc` RLS context). */
    @Transactional(readOnly = true)
    fun introspect(sid: String): SessionIntrospection? {
        val session = repo.findById(sid).orElse(null) ?: return null
        if (session.revokedAt != null) return null
        val now = Instant.now()
        if (!now.isBefore(session.idleExpiresAt) || !now.isBefore(session.absoluteExpiresAt)) return null
        return SessionIntrospection(session.userId, session.authTime, session.absoluteExpiresAt)
    }

    @Transactional(readOnly = true)
    fun activeSessions(userId: String): List<SessionView> =
        repo.findByUserIdAndRevokedAtIsNullOrderByCreatedAtAsc(userId)
            .map { SessionView(it.sid, it.deviceLabel, it.ipClass, it.createdAt, it.lastActiveAt) }

    /**
     * Slides `idle_expires_at` (capped by absolute) at most once per hour per
     * session - the `acct:slact:{sid}` coalescing key bounds registry writes (T-13).
     */
    @Transactional
    fun touchIfDue(sid: String, userId: String) {
        val claimed = touchCoalesce.opsForValue()
            .setIfAbsent("acct:slact:$sid", "1", Duration.ofHours(1))
        if (claimed != true) return
        val session = repo.findById(sid).orElse(null) ?: return
        if (session.userId != userId || session.revokedAt != null) return
        val now = Instant.now()
        session.lastActiveAt = now
        session.idleExpiresAt = min(now.plus(idle), session.absoluteExpiresAt)
        repo.save(session)
    }

    /** Step-up result: refreshes the freshness source in registry and edge key (BR-07). */
    @Transactional
    fun refreshAuthTime(sid: String, userId: String): SessionEntity? {
        val session = repo.findById(sid).orElse(null) ?: return null
        if (session.userId != userId || session.revokedAt != null) return null
        val now = Instant.now()
        session.authTime = now
        session.lastActiveAt = now
        repo.save(session)
        edge.refreshAuthTime(sid, now)
        return session
    }

    /** Returns false when the row is absent, foreign, or already revoked (`404 session_not_found`). */
    @Transactional
    fun revoke(sid: String, userId: String, auditAction: String = "session_revoke"): Boolean {
        val session = repo.findById(sid).orElse(null) ?: return false
        if (session.userId != userId || session.revokedAt != null) return false
        revokeInternal(session, auditAction)
        return true
    }

    @Transactional
    fun revokeOthers(userId: String, keepSid: String, ipClass: String?) {
        repo.findByUserIdAndRevokedAtIsNullOrderByCreatedAtAsc(userId)
            .filter { it.sid != keepSid }
            .forEach { revokeInternal(it, "session_revoke", ipClass) }
    }

    @Transactional
    fun logout(sid: String, userId: String, ipClass: String?) {
        val session = repo.findById(sid).orElse(null) ?: return
        if (session.userId == userId) revokeInternal(session, "session_revoke", ipClass)
    }

    private fun revokeInternal(session: SessionEntity, action: String, ipClass: String? = null) {
        if (session.revokedAt == null) {
            session.revokedAt = Instant.now()
            repo.save(session)
        }
        edge.delete(session.sid)
        audit.record(userId = session.userId, action = action, target = session.sid, ipClass = ipClass)
    }

    private fun quotaMakeRoom(userId: String) {
        val active = repo.findByUserIdAndRevokedAtIsNullOrderByCreatedAtAsc(userId)
        if (active.size < props.session.quota) return
        // NFR-17: on excess the oldest non-current session is revoked; audited.
        val excess = active.size - (props.session.quota - 1)
        active.take(excess).forEach {
            quotaEvicted.increment()
            revokeInternal(it, "session_revoke")
        }
    }

    /**
     * Idle/absolute sweeper (T-05). A registry row past its idle bound whose edge
     * key is still alive is activity we have not coalesced yet: extend instead of
     * revoke, so the sliding window honors edge-validated activity (NFR-04).
     */
    @Scheduled(
        fixedDelayString = "\${account.session.sweep-interval-ms:60000}",
        initialDelayString = "\${account.session.sweep-initial-delay-ms:30000}",
    )
    fun sweep() {
        RlsContext.asService {
            val expired = try {
                repoExpired()
            } catch (ex: Exception) {
                log.warn("session sweep degraded: {}", ex.message)
                return@asService
            }
            for (session in expired) {
                try {
                    if (edge.exists(session.sid)) {
                        val now = Instant.now()
                        session.idleExpiresAt = min(now.plus(idle), session.absoluteExpiresAt)
                        repo.save(session)
                        edge.write(session.sid, session.userId, session.authTime, session.absoluteExpiresAt, ttlFor(session, now))
                        sweeperExtended.increment()
                    } else {
                        revokeExpired(session)
                    }
                } catch (ex: Exception) {
                    log.warn("session sweep item failed sid={} ({})", session.sid, ex.message)
                }
            }
        }
    }

    private fun repoExpired(): List<SessionEntity> = repo.findExpired(Instant.now())

    private fun revokeExpired(session: SessionEntity) {
        sweeperRevoked.increment()
        revokeInternal(session, "session_revoke")
    }

    private fun ttlFor(session: SessionEntity, now: Instant): Duration {
        val idleRemaining = Duration.between(now, session.idleExpiresAt)
        val absoluteRemaining = Duration.between(now, session.absoluteExpiresAt)
        return if (idleRemaining < absoluteRemaining) idleRemaining else absoluteRemaining
    }

    private fun min(a: Instant, b: Instant): Instant = if (a < b) a else b

    /** Step-up freshness window (BR-07 / NFR-05). */
    fun stepUpWindow(): Duration = Duration.ofSeconds(props.session.stepUpSeconds)
}
