package com.resume.profile.security

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Feature 04 owner envelopes (NFR-08): add 10/day/user; verify+recheck
 * 10/min/user; list 60/min/user; reclaim/ops 30/min/operator. Breach answers
 * `429 throttled` + `Retry-After` (remaining window) and rides the abuse
 * counters (BR-09); the per-service backstop filter stays authoritative
 * beyond these feature class limits.
 */
@Service
class DomainRateLimiter(private val redis: StringRedisTemplate) {

    fun consumeAddDaily(userId: String): RetryState = consumeDay(key("add:$userId", LocalDate.now(ZoneOffset.UTC).toEpochDay()))

    fun consumeVerifyPerMinute(userId: String): RetryState =
        consumeMinute(keyUnit("verify", userId, minuteBucket()), LIMIT_VERIFY_PER_MINUTE)

    fun consumeListPerMinute(userId: String): RetryState =
        consumeMinute(keyUnit("list", userId, minuteBucket()), LIMIT_LIST_PER_MINUTE)

    fun consumeOps(operator: String): RetryState =
        consumeMinute(keyUnit("ops", operator, minuteBucket()), LIMIT_OPS_PER_MINUTE)

    private fun key(prefix: String, dayEpoch: Long) = "rb:domain:rate:$prefix:$dayEpoch"

    private fun keyUnit(action: String, entity: String, minute: Long) =
        "rb:domain:rate:$action:$entity:$minute"

    private fun minuteBucket(): Long = System.currentTimeMillis() / 60_000

    private fun consumeDay(key: String): RetryState {
        val value = increment(key, Duration.ofHours(25))
        return if (value > LIMIT_ADD_DAILY) RetryState(false, retryRemaining(key, 60.0 * 60 * 24)) else RetryState(true, null)
    }

    private fun consumeMinute(key: String, limit: Long): RetryState {
        val value = increment(key, Duration.ofMinutes(2))
        return if (value > limit) RetryState(false, retryRemaining(key, 60.0)) else RetryState(true, null)
    }

    private fun increment(key: String, expireAt: Duration): Long = try {
        val value = redis.opsForValue().increment(key) ?: 1L
        if (value == 1L) redis.expire(key, expireAt)
        value
    } catch (_: Exception) {
        // Cache down: deny nothing here; the service backstop filter still applies.
        1L
    }

    private fun retryRemaining(key: String, windowSeconds: Double): Long = try {
        val ttl = redis.getExpire(key) // seconds; -1 none, -2 key missing
        ((if (ttl < 0) windowSeconds else minOf(ttl.toDouble(), windowSeconds)).toLong()).coerceAtLeast(1)
    } catch (_: Exception) {
        windowSeconds.toLong().coerceAtLeast(1)
    }

    data class RetryState(val allowed: Boolean, val seconds: Long?)

    /** 429 `throttled` + `Retry-After` (remaining window; BR-09's keyed surface). */
    class ThrottleException(val seconds: Long) :
        RuntimeException("Too many attempts. Try again in $seconds seconds.")

    companion object {
        const val LIMIT_ADD_DAILY = 10L
        const val LIMIT_VERIFY_PER_MINUTE = 10L
        const val LIMIT_LIST_PER_MINUTE = 60L
        const val LIMIT_OPS_PER_MINUTE = 30L
    }
}
