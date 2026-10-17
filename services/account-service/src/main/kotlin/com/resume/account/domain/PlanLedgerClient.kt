package com.resume.account.domain

import com.resume.account.config.AccountProperties
import com.resume.account.security.OutboundServiceJwt
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import org.springframework.http.client.JdkClientHttpRequestFactory
import java.time.Duration

/** Subset of feature 35's `EffectivePlanView` this service consumes. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class LedgerPlan(val plan: String = "free", val capabilities: Map<String, String> = emptyMap())

/**
 * Feature 35's ledger read `GET /internal/v1/billing/plan/{userId}` (D-5).
 * The ledger materializes the free floor structurally; any failure here is a
 * miss and the caller clamps to free - free is never invented (BR-09).
 */
@Service
class PlanLedgerClient(
    private val props: AccountProperties,
    private val outbound: OutboundServiceJwt,
    meters: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val client: RestClient = RestClient.builder()
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
            ).apply { setReadTimeout(Duration.ofMillis(props.services.timeoutMs.coerceAtLeast(500))) },
        )
        .build()

    fun fetch(userId: String): LedgerPlan? = try {
        val spec = client.get().uri("${props.services.billingBaseUrl}/internal/v1/billing/plan/{userId}", userId)
        outbound.authorization("billing-service")?.let { spec.header(HttpHeaders.AUTHORIZATION, it) }
        val body = spec.retrieve().body(LedgerPlan::class.java)
        body?.takeIf { it.plan == "free" || it.plan == "pro" }
    } catch (ex: Exception) {
        log.warn("ledger plan read failed for user ({}); clamping to free", ex.message)
        null
    }
}
