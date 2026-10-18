package com.resume.profile.integration

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.resume.profile.security.OutboundServiceJwt
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

@JsonIgnoreProperties(ignoreUnknown = true)
data class EntitlementSnapshot(val userId: String, val plan: String, val capabilities: Map<String, String> = emptyMap())

/**
 * Feature 01 snapshot consumption (feature 04 D-3, BR-06): `plan = pro` is the
 * add/verify/serving gate, read from `GET /internal/v1/entitlements/{userId}`
 * with a 30 s TTL cache (NFR-07 bound, `rb:plan:{userId}`). Fail-closed: a
 * miss/error/timeout is `free` and increments `entitlement_snapshot_miss` -
 * never a silent widen. Plan changes self-heal via the TTL; no event exists.
 */
@Service
class EntitlementSnapshotClient(
    private val redis: StringRedisTemplate,
    private val outbound: OutboundServiceJwt,
    meters: MeterRegistry,
    @Value("\${integration.account.base-url:http://account-service:8080}") private val baseUrl: String,
    @Value("\${integration.account.timeout-ms:2000}") timeoutMs: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val misses = meters.counter("entitlement_snapshot_miss")

    fun planOf(userId: String): String {
        redisGet(userId)?.let { return if (it == PRO) PRO else FREE }
        val fetched = try {
            val spec = client.get().uri("$baseUrl/internal/v1/entitlements/{userId}", userId)
            outbound.authorization("account-service")?.let { spec.header(HttpHeaders.AUTHORIZATION, it) }
            spec.retrieve().body(EntitlementSnapshot::class.java)?.plan
        } catch (ex: Exception) {
            log.warn("entitlement snapshot lookup failed user={}: {}", userId, ex.message)
            null
        }
        val normalized = fetched?.let { if (it == PRO) PRO else FREE } ?: run {
            misses.increment()
            FREE // fail-closed clamp (BR-09 of feature 01): free is never invented silently
        }
        if (fetched != null) redisSet(userId, normalized)
        return normalized
    }

    fun isPro(userId: String): Boolean = planOf(userId) == PRO

    private fun redisGet(userId: String): String? = try {
        redis.opsForValue().get(key(userId))
    } catch (_: Exception) {
        null
    }

    private fun redisSet(userId: String, value: String) {
        try {
            redis.opsForValue().set(key(userId), value, Duration.ofSeconds(30))
        } catch (_: Exception) {
            // cache-only; TTL covers
        }
    }

    private fun key(userId: String) = "rb:plan:$userId"

    private val client: RestClient = RestClient.builder()
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
            ).apply { setReadTimeout(Duration.ofMillis(timeoutMs.coerceAtLeast(500))) },
        )
        .build()

    private companion object {
        const val PRO = "pro"
        const val FREE = "free"
    }
}
