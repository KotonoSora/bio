package com.resume.gateway.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration

/**
 * Route-class rate envelopes (baseline 08-security-baseline). Feature specs may
 * tighten; loosening requires a flagged amendment. 429 + Retry-After on breach.
 */
import org.springframework.core.annotation.Order
import org.slf4j.LoggerFactory

@Component
@Order(1)
class RateLimitFilter(
    private val redis: StringRedisTemplate,
    @Value("\${ratelimit.enabled:true}") private val enabled: Boolean,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    data class Rule(val limit: Int, val window: Duration)

    private fun ruleFor(path: String, method: String): Rule? = when {
        path.startsWith("/api/t/") -> Rule(60, Duration.ofMinutes(1))          // beacon intake burst
        path.startsWith("/api/v1/auth") || path.contains("/login") -> Rule(20, Duration.ofMinutes(1))
        path.startsWith("/webhooks/") -> null // provider IP + signature path instead
        path.startsWith("/api/") && method == "GET" -> Rule(60, Duration.ofMinutes(1)) // owner reads default
        path.startsWith("/api/") -> Rule(60, Duration.ofMinutes(1))
        else -> Rule(120, Duration.ofMinutes(1)) // anonymous public reads
    }

    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        if (!enabled) { chain.doFilter(req, res); return }
        val rule = ruleFor(req.requestURI, req.method) ?: run { chain.doFilter(req, res); return }
        val key = "rl:${req.remoteAddr}:${req.requestURI.hashCode()}:${System.currentTimeMillis() / rule.window.toMillis()}"
        try {
            val count = redis.opsForValue().increment(key) ?: 1
            if (count == 1L) redis.expire(key, rule.window)
            if (count > rule.limit) {
                res.setHeader("Retry-After", rule.window.seconds.toString())
                res.sendError(429, "rate limited")
                return
            }
        } catch (ex: Exception) {
            // Rate limiter unavailable: pass with alert; per-service backstops still hold.
            log.warn("rate limiter degraded: {}", ex.message)
        }
        chain.doFilter(req, res)
    }
}
