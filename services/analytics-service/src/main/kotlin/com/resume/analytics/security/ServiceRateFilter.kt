package com.resume.analytics.security

import io.micrometer.core.instrument.MeterRegistry
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration

/**
 * Per-service rate backstop (degrade matrix): Redis fixed-window counters hold
 * even when the edge limiter is unavailable. Limit breach answers 429 with
 * Retry-After. If Redis itself is down, anonymous/reads fail OPEN with an
 * alert while owner writes fail CLOSED (503) — never silently unthrottled.
 */
class ServiceRateFilter(
    private val redis: StringRedisTemplate,
    private val perMinute: Int,
    meters: MeterRegistry,
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)
    private val tripped = meters.counter("service.ratelimit.tripped")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val principal = SecurityContextHolder.getContext().authentication?.name ?: request.remoteAddr
        val key = "svc-rl:$principal:${System.currentTimeMillis() / 60_000}"
        try {
            val n = redis.opsForValue().increment(key) ?: 1
            if (n == 1L) redis.expire(key, Duration.ofMinutes(1))
            if (n > perMinute) {
                tripped.increment()
                response.setHeader("Retry-After", "60")
                response.sendError(429, "rate limited")
                return
            }
        } catch (ex: Exception) {
            log.warn("service rate backstop degraded: {}", ex.message)
            val authedWrite = request.method != "GET" &&
                SecurityContextHolder.getContext().authentication != null
            if (authedWrite) {
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "backstop unavailable")
                return
            }
        }
        chain.doFilter(request, response)
    }
}
