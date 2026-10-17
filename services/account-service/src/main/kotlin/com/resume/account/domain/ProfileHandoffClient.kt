package com.resume.account.domain

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.resume.account.config.AccountProperties
import com.resume.account.security.OutboundServiceJwt
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.net.http.HttpClient
import org.springframework.http.client.JdkClientHttpRequestFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@JsonIgnoreProperties(ignoreUnknown = true)
data class ProfileByUser(val username: String = "")

sealed interface HandoffStatus {
    data object HasProfile : HandoffStatus
    data object NoProfile : HandoffStatus
    /** Lookup failed: the caller defaults to `/app` (BR-14 failure path, counted). */
    data object Unknown : HandoffStatus
}

/**
 * First-login handoff (BR-14, T-10): consumes profile-service's additive read
 * `GET /internal/v1/profiles/by-user/{user_id}` (registered in feature 06's
 * contract). Status-only cache bounded to 60 s; failure is never an error loop -
 * it degrades to `/app` and increments `profile_handoff_miss`.
 */
@Service
class ProfileHandoffClient(
    private val props: AccountProperties,
    private val outbound: OutboundServiceJwt,
    meters: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val misses = meters.counter("profile_handoff_miss")
    private data class Cached(val status: HandoffStatus, val username: String?, val at: Instant)
    private val cache = ConcurrentHashMap<String, Cached>()
    private val client: RestClient = RestClient.builder()
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
            ).apply { setReadTimeout(Duration.ofMillis(props.services.timeoutMs.coerceAtLeast(500))) },
        )
        .build()

    fun lookup(userId: String): HandoffStatus = raw(userId).first

    /**
     * Typed-confirmation expectation (feature 02 BR-01): the profile username
     * when one exists, else null (= the literal `DELETE`). `Unknown` degrades to
     * null - the typed value is a UX guard, never the security boundary
     * (step-up + owner session are).
     */
    fun usernameOrNull(userId: String): String? = raw(userId).second

    private fun raw(userId: String): Pair<HandoffStatus, String?> {
        val now = Instant.now()
        cache[userId]?.let {
            if (it.at.plusSeconds(props.services.handoffCacheSeconds).isAfter(now)) return it.status to it.username
        }
        val status: Pair<HandoffStatus, String?> = try {
            val spec = client.get()
                .uri("${props.services.profileBaseUrl}/internal/v1/profiles/by-user/{userId}", userId)
            outbound.authorization("profile-service")?.let { spec.header(HttpHeaders.AUTHORIZATION, it) }
            val body = spec.retrieve().body(ProfileByUser::class.java)
            if (body?.username.isNullOrBlank()) {
                HandoffStatus.NoProfile to null
            } else {
                HandoffStatus.HasProfile to body.username
            }
        } catch (ex: RestClientResponseException) {
            if (ex.statusCode == HttpStatus.NOT_FOUND) {
                HandoffStatus.NoProfile to null
            } else {
                log.warn("profile handoff lookup failed: {}", ex.statusCode)
                misses.increment()
                HandoffStatus.Unknown to null
            }
        } catch (ex: Exception) {
            log.warn("profile handoff lookup failed: {}", ex.message)
            misses.increment()
            HandoffStatus.Unknown to null
        }
        cache[userId] = Cached(status.first, status.second, now)
        return status
    }
}
