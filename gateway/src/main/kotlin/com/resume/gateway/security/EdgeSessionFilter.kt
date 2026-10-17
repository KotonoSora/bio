package com.resume.gateway.security

import io.micrometer.core.instrument.MeterRegistry
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.time.Instant

/**
 * Edge session validation (feature 01 D-2/T-06): reads the `rb_session` cookie
 * against `edge:session:{sid}` (Redis db 2, hash `{userId, authTime, absExp}` in
 * epoch seconds), refreshes the sliding TTL to remaining min(idle, absolute),
 * and on a miss rebuilds via the account-service introspect path. The registry
 * is truth; the key is cache - a hit is never a grant by itself, and a miss
 * that account-service cannot answer (or answer 404) leaves the caller
 * anonymous (owner routes then fail closed at authorization; session endpoints
 * self-check downstream on the `sid` claim).
 *
 * Registered INSIDE the security filter chain (before AuthorizationFilter) so
 * the resolved identity backs `authenticated()` rules and the internal JWT
 * claims; runs once per request.
 */
class EdgeSessionFilter(
    private val redis: StringRedisTemplate,
    private val introspect: SessionIntrospectClient,
    private val cookieName: String,
    private val idleDays: Long,
    meters: MeterRegistry,
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)
    private val fallback = meters.counter("edge_introspect_fallback")

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val p = request.requestURI
        return p.startsWith("/health") || p.startsWith("/actuator") ||
            p.startsWith("/v3/api-docs") || p.startsWith("/swagger-ui") || p == "/.well-known/jwks.json"
    }

    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        val sid = req.cookies?.firstOrNull { it.name == cookieName }?.value?.takeIf { it.isNotBlank() }
        if (sid == null) {
            chain.doFilter(req, res)
            return
        }
        when (val resolution = resolve(sid, Instant.now())) {
            is Resolution.Resolved -> {
                val auth = UsernamePasswordAuthenticationToken(
                    resolution.identity.userId,
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_USER")),
                )
                auth.details = resolution.identity
                SecurityContextHolder.getContext().authentication = auth
                req.setAttribute(ATTR_EDGE_IDENTITY, resolution.identity)
                chain.doFilter(req, res)
            }
            Resolution.Unavailable -> {
                // Edge store AND account-service unreachable: owner routes fail
                // closed (503). Anonymous surfaces proceed unauthenticated.
                if (req.requestURI.startsWith("/api/v1/")) {
                    problem(res, "Session validation is temporarily unavailable.")
                } else {
                    chain.doFilter(req, res)
                }
            }
            Resolution.NotFound -> chain.doFilter(req, res)
        }
    }

    private fun resolve(sid: String, now: Instant): Resolution {
        edgeHit(sid, now)?.let { return Resolution.Resolved(it) }
        fallback.increment()
        return when (val result = introspect.introspect(sid)) {
            is SessionIntrospectClient.Result.Found -> {
                val identity = EdgeIdentity(result.userId, sid, result.authTimeEpochSeconds, result.absoluteExpiresEpochSeconds)
                rebuild(sid, identity, now)
                Resolution.Resolved(identity)
            }
            SessionIntrospectClient.Result.NotFound -> Resolution.NotFound
            SessionIntrospectClient.Result.Unavailable -> Resolution.Unavailable
        }
    }

    /** Cache hit path: parse the edge hash and slide the TTL (best effort). */
    private fun edgeHit(sid: String, now: Instant): EdgeIdentity? {
        val key = key(sid)
        val entries = try {
            redis.opsForHash<String, String>().entries(key)
        } catch (ex: Exception) {
            log.warn("edge session store degraded: {}", ex.message)
            return null
        }
        val userId = entries["userId"]?.takeIf { it.isNotBlank() } ?: return null
        val authTime = entries["authTime"]?.toLongOrNull() ?: return null
        val absExp = entries["absExp"]?.toLongOrNull() ?: return null
        if (absExp <= now.epochSecond) return null
        try {
            redis.expire(key, ttl(absExp, now))
        } catch (ex: Exception) {
            log.warn("edge ttl refresh skipped: {}", ex.message)
        }
        return EdgeIdentity(userId, sid, authTime, absExp)
    }

    /** Rebuild after introspect (D-2): same hash contract the account-service writes. */
    private fun rebuild(sid: String, identity: EdgeIdentity, now: Instant) {
        val ttl = ttl(identity.absoluteExpiresEpochSeconds, now)
        if (ttl <= Duration.ZERO) return
        try {
            val key = key(sid)
            redis.opsForHash<String, String>().putAll(
                key,
                mapOf(
                    "userId" to identity.userId,
                    "authTime" to identity.authTimeEpochSeconds.toString(),
                    "absExp" to identity.absoluteExpiresEpochSeconds.toString(),
                ),
            )
            redis.expire(key, ttl)
        } catch (ex: Exception) {
            log.warn("edge key rebuild skipped: {}", ex.message)
        }
    }

    private fun ttl(absExpEpochSeconds: Long, now: Instant): Duration {
        val remaining = Duration.ofSeconds(absExpEpochSeconds - now.epochSecond)
        val idle = Duration.ofDays(idleDays)
        return if (remaining < idle) remaining else idle
    }

    private fun key(sid: String) = "edge:session:$sid"

    private fun problem(res: HttpServletResponse, detail: String) {
        if (res.isCommitted) return
        res.status = 503
        res.contentType = "application/problem+json"
        res.writer.write(
            """{"type":"about:blank","title":"auth_unavailable","status":503,"code":"auth_unavailable","detail":"$detail"}""",
        )
    }

    private sealed interface Resolution {
        data class Resolved(val identity: EdgeIdentity) : Resolution
        data object NotFound : Resolution
        data object Unavailable : Resolution
    }

    companion object {
        const val ATTR_EDGE_IDENTITY = "gateway.edgeIdentity"
    }
}
