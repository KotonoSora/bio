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
    private val cache = ConcurrentHashMap<String, Pair<HandoffStatus, Instant>>()
    private val client: RestClient = RestClient.builder()
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
            ).apply { setReadTimeout(Duration.ofMillis(props.services.timeoutMs.coerceAtLeast(500))) },
        )
        .build()

    fun lookup(userId: String): HandoffStatus {
        val now = Instant.now()
        cache[userId]?.let {
            if (it.second.plusSeconds(props.services.handoffCacheSeconds).isAfter(now)) return it.first
        }
        val status = try {
            val spec = client.get()
                .uri("${props.services.profileBaseUrl}/internal/v1/profiles/by-user/{userId}", userId)
            outbound.authorization("profile-service")?.let { spec.header(HttpHeaders.AUTHORIZATION, it) }
            val body = spec.retrieve().body(ProfileByUser::class.java)
            if (body?.username.isNullOrBlank()) HandoffStatus.NoProfile else HandoffStatus.HasProfile
        } catch (ex: RestClientResponseException) {
            if (ex.statusCode == HttpStatus.NOT_FOUND) {
                HandoffStatus.NoProfile
            } else {
                log.warn("profile handoff lookup failed: {}", ex.statusCode)
                misses.increment()
                HandoffStatus.Unknown
            }
        } catch (ex: Exception) {
            log.warn("profile handoff lookup failed: {}", ex.message)
            misses.increment()
            HandoffStatus.Unknown
        }
        cache[userId] = status to now
        return status
    }
}
