package com.resume.account.domain.concierge

import com.resume.account.api.ContractResponse
import com.resume.account.api.SowItem
import com.resume.account.config.ConciergeProperties
import com.resume.account.domain.ApiException
import com.resume.account.domain.Tokens
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Feature 05 envelopes (TL 05 "Rate envelopes", NFR-07). Enforced here as the
 * service-side backstop that also holds the contract-scoped envelope the edge
 * cannot see (20 task requests/day/contract); breach answers `429 throttled`
 * with `Retry-After` (ProblemHandler). Probing is counted, never punished.
 */
@Service
class ConciergeRateLimiter(
    private val redis: StringRedisTemplate,
    private val props: ConciergeProperties,
    meters: MeterRegistry,
) {
    private val tripped = meters.counter("throttled")

    /** Client task requests: 20/day/contract (per contract, UTC day). */
    fun contractRequest(contractId: String) {
        val today = LocalDate.now(ZoneOffset.UTC)
        val retryAfter = secondsUntilUtcMidnight()
        val key = "acct:rl:cnc-req:$contractId:$today"
        val count = try {
            redis.opsForValue().increment(key) ?: return
        } catch (_: Exception) {
            return // Redis down: the edge limiter still gates (degrade matrix).
        }
        if (count == 1L) redis.expire(key, Duration.ofSeconds(retryAfter + 120))
        if (count > props.requestsPerDay) {
            tripped.increment()
            throw ApiException.throttled(maxOf(1, retryAfter))
        }
    }

    /** Client approve/pause/exit: 60/min/user. */
    fun clientAction(userId: String) = check("cnc-act", userId, props.clientActionsPerMinute)

    /** Internal ops (task/status/staff/templates): 120/min/operator, audited by the caller. */
    fun ops(actor: String) = check("cnc-ops", actor, props.opsPerMinute)

    private fun check(bucket: String, key: String, limit: Long) {
        val window = Duration.ofMinutes(1)
        val windowKey = "acct:rl:$bucket:$key:${Instant.now().epochSecond / window.seconds}"
        val count = try {
            redis.opsForValue().increment(windowKey) ?: return
        } catch (_: Exception) {
            return
        }
        if (count == 1L) redis.expire(windowKey, window)
        if (count > limit) {
            tripped.increment()
            throw ApiException.throttled(window.seconds)
        }
    }

    private fun secondsUntilUtcMidnight(): Long {
        val now = Instant.now()
        val midnight = LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        return midnight.epochSecond - now.epochSecond
    }
}

/** Request bodies + normalized value types for the contract lifecycle. */
data class ContractPatch(
    val action: String = "",
    val slaTier: String? = null,
    val sow: List<SowItem>? = null,
    val scheduledWork: String? = null,
    val acknowledge: String? = null,
)

/** Result of a client-requested transition: the response view plus whether it committed now. */
data class TransitionOutcome(val contract: ContractResponse, val committed: Boolean)
