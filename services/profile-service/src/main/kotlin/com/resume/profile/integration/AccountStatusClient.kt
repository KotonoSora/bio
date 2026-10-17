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
data class AccountStatusBody(val status: String = "active", val purgeDueAt: String? = null)

/**
 * Account-status parity read (feature 02 D-2, S-5): resolve/sitemap consult
 * `profile.acctstat:{userId}` (Redis, 60 s TTL) and refresh via
 * `GET /internal/v1/account/status/{userId}` on miss. Public reads fail open
 * (`active`) on a cold miss with a counter + alert - availability wins for
 * public reads, bounded by the cache window; `account.deletion_requested.v1` /
 * `deletion_cancelled.v1` / `account.purged.v1` invalidate eagerly.
 */
@Service
class AccountStatusClient(
    private val redis: StringRedisTemplate,
    private val outbound: OutboundServiceJwt,
    @Value("\${integration.account.base-url:http://account-service:8080}") private val baseUrl: String,
    @Value("\${integration.account.timeout-ms:2000}") timeoutMs: Long,
    meters: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val misses = meters.counter("profile.acctstat.miss")
    private val client: RestClient = RestClient.builder()
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
            ).apply { setReadTimeout(Duration.ofMillis(timeoutMs.coerceAtLeast(500))) },
        )
        .build()

    fun status(userId: String): String {
        redisGet(userId)?.let { return it }
        val fetched = try {
            val spec = client.get().uri("$baseUrl/internal/v1/account/status/{userId}", userId)
            outbound.authorization("account-service")?.let { spec.header(HttpHeaders.AUTHORIZATION, it) }
            spec.retrieve().body(AccountStatusBody::class.java)?.status
        } catch (ex: Exception) {
            log.warn("account status lookup failed user={}: {}", userId, ex.message)
            null
        }
        if (fetched.isNullOrBlank()) {
            misses.increment()
            return ACTIVE // S-5: cold-miss fail-open, counted; the 60 s bound caps the error window
        }
        redisSet(userId, fetched)
        return fetched
    }

    fun isActive(userId: String): Boolean = status(userId) == ACTIVE

    fun invalidate(userId: String) {
        try {
            redis.delete(key(userId))
        } catch (_: Exception) {
            // TTL heals within 60 s
        }
    }

    private fun redisGet(userId: String): String? = try {
        redis.opsForValue().get(key(userId))
    } catch (_: Exception) {
        null
    }

    private fun redisSet(userId: String, value: String) {
        try {
            redis.opsForValue().set(key(userId), value, Duration.ofSeconds(60))
        } catch (_: Exception) {
            // cache-only
        }
    }

    private fun key(userId: String) = "profile.acctstat:$userId"

    private companion object {
        const val ACTIVE = "active"
    }
}
