package com.resume.account.security

import com.resume.account.domain.ApiException
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Service-side backstop counters (degrade matrix): hold even when the edge
 * limiter is unavailable. Envelopes: auth routes 20/min/IP, step-up and
 * link/unlink 10/min/user, owner reads 60/min/user (NFR-09). Breach answers
 * `429 throttled` + `Retry-After`; probing is counted, not punished.
 */
@Component
class AccountRateLimiter(
    private val redis: StringRedisTemplate,
    meters: MeterRegistry,
) {
    private val tripped = meters.counter("throttled")

    fun authIp(ipClass: String?) = check("auth", ipClass ?: "unknown", limit = 20, window = Duration.ofMinutes(1))

    fun userAction(userId: String, bucket: String) =
        check(bucket, userId, limit = 10, window = Duration.ofMinutes(1))

    fun ownerRead(userId: String) = check("me", userId, limit = 60, window = Duration.ofMinutes(1))

    private fun check(bucket: String, key: String, limit: Long, window: Duration) {
        val windowKey = "acct:rl:$bucket:$key:${System.currentTimeMillis() / window.toMillis()}"
        val count = try {
            redis.opsForValue().increment(windowKey) ?: 1L
        } catch (_: Exception) {
            return // Redis down: edge limiter still gates; authenticated writes gated by ServiceRateFilter
        }
        if (count == 1L) redis.expire(windowKey, window)
        if (count > limit) {
            tripped.increment()
            throw ApiException.throttled(window.seconds)
        }
    }
}
