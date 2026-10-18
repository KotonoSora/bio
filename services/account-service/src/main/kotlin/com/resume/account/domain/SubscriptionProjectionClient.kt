package com.resume.account.domain

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.resume.account.config.AccountProperties
import com.resume.account.security.OutboundServiceJwt
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration
import java.time.Instant

/** Feature 36's lifecycle row as projected to this service (TL 02/03, D-1). */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SubscriptionLifecycleView(
    val plan: String = "free",
    val status: String = "",
    val term: String = "",
    val periodStart: Instant? = null,
    val periodEnd: Instant? = null,
    val graceEndsAt: Instant? = null,
    val nextAmountVnd: Long? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SubscriptionEnvelope(val subscription: SubscriptionLifecycleView? = null) // null = no pro row (TL 36)

/**
 * Forward read of feature 36's subscription projection
 * (`GET /internal/v1/billing/subscriptions/{userId}`). Pre-36 the endpoint is
 * not served: a 404, timeout, or incomplete row degrades to tier-only - absence
 * stays the design, no invented amounts (BR-03, S-9). Every degradation is
 * counted (`plan_projection_miss`, alertable per TL 05).
 */
@Service
class SubscriptionProjectionClient(
    private val props: AccountProperties,
    private val outbound: OutboundServiceJwt,
    meters: MeterRegistry,
) : SubscriptionProjectionSource {
    private val log = LoggerFactory.getLogger(javaClass)
    private val miss = meters.counter("plan_projection_miss")

    private val client: RestClient = RestClient.builder()
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
            ).apply { setReadTimeout(Duration.ofMillis(props.services.timeoutMs.coerceAtLeast(500))) },
        )
        .build()

    /** The projected lifecycle row, or null when there is nothing to show. */
    override fun fetch(userId: String): SubscriptionLifecycleView? = try {
        val spec = client.get()
            .uri("${props.services.billingBaseUrl}/internal/v1/subscriptions/{userId}", userId)
        outbound.authorization("billing-service")?.let { spec.header(HttpHeaders.AUTHORIZATION, it) }
        val body = spec.retrieve().body(SubscriptionEnvelope::class.java)
        body?.subscription?.takeIf { it.periodEnd != null && it.nextAmountVnd != null }
    } catch (ex: Exception) {
        log.warn("subscription projection read failed for user ({}); rendering tier only", ex.message)
        miss.increment()
        null
    }
}
