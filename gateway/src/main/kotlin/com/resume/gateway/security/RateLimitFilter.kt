package com.resume.gateway.security

import io.micrometer.core.instrument.MeterRegistry
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.annotation.Order
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration

/**
 * Route-class rate envelopes (baseline 08; feature 01 numbers in TL 05): SSO
 * initiation/callbacks 20/min/IP, step-up + link/unlink 10/min/user, owner
 * reads 60/min/user, contact-email 5/hour/user, beacon bursts tolerated.
 * User classes key on the session principal (the edge session filter runs
 * first); IP classes key on the first X-Forwarded-For hop the edge set. Breach
 * is `429` + `Retry-After` + stable `throttled` code, counted.
 * Redis trouble never fails requests - per-service backstops still hold.
 */
@Component
@Order(1)
class RateLimitFilter(
    private val redis: StringRedisTemplate,
    @Value("\${ratelimit.enabled:true}") private val enabled: Boolean,
    meters: MeterRegistry,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)
    private val throttled = meters.counter("throttled")

    data class Rule(val bucket: String, val limit: Int, val window: Duration, val perUser: Boolean)

    private fun ruleFor(path: String): Rule? = when {
        path.startsWith("/api/v1/me/contact-email") -> Rule("cemail", 5, Duration.ofHours(1), perUser = true)
        path.startsWith("/api/auth/sign-in") || path.startsWith("/api/auth/callback") ->
            Rule("authip", 20, Duration.ofMinutes(1), perUser = false)
        path.startsWith("/api/auth/step-up") || path.startsWith("/api/auth/link/") ||
            path.startsWith("/api/auth/unlink/") -> Rule("authuser", 10, Duration.ofMinutes(1), perUser = true)
        path.startsWith("/api/v1/me") -> Rule("owner", 60, Duration.ofMinutes(1), perUser = true)
        path.startsWith("/api/t/") -> Rule("beacon", 60, Duration.ofMinutes(1), perUser = false) // tolerated burst
        path.startsWith("/webhooks/") -> null // provider IP + signature path instead
        path.startsWith("/api/") -> Rule("api", 60, Duration.ofMinutes(1), perUser = false)
        else -> Rule("anon", 120, Duration.ofMinutes(1), perUser = false) // anonymous public reads
    }

    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        if (!enabled) {
            chain.doFilter(req, res)
            return
        }
        val rule = ruleFor(req.requestURI) ?: run {
            chain.doFilter(req, res)
            return
        }
        val who = if (rule.perUser) principal() ?: clientIp(req) else clientIp(req)
        val windowIndex = System.currentTimeMillis() / rule.window.toMillis()
        val key = "rl:${rule.bucket}:$who:$windowIndex"
        try {
            val count = redis.opsForValue().increment(key) ?: 1
            if (count == 1L) redis.expire(key, rule.window)
            if (count > rule.limit) {
                throttled.increment()
                breach(res, rule)
                return
            }
        } catch (ex: Exception) {
            // Rate limiter unavailable: pass with alert; per-service backstops still hold.
            log.warn("rate limiter degraded: {}", ex.message)
        }
        chain.doFilter(req, res)
    }

    private fun breach(res: HttpServletResponse, rule: Rule) {
        if (res.isCommitted) return
        res.status = 429
        res.contentType = "application/problem+json"
        res.setHeader("Retry-After", rule.window.seconds.toString())
        res.writer.write(
            """{"type":"about:blank","title":"throttled","status":429,"code":"throttled",""" +
                """"detail":"Too many requests. Try again later.","traceId":"${MDC.get("traceId") ?: ""}"}""",
        )
    }

    private fun principal(): String? =
        SecurityContextHolder.getContext().authentication
            ?.takeIf { it.isAuthenticated && it !is AnonymousAuthenticationToken }
            ?.name
            ?.takeIf { it.isNotBlank() }

    private fun clientIp(req: HttpServletRequest): String =
        req.getHeader("X-Forwarded-For")?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() }
            ?: req.remoteAddr
}
