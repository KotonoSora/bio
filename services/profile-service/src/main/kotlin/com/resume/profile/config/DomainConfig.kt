package com.resume.profile.config

import com.resume.profile.domain.domains.DohTxtResolver
import com.resume.profile.domain.domains.TxtLookupPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

/**
 * Feature 04 DNS control-port wiring (D-4): the TXT lookup adapter is the
 * single DNS transport the feature touches; verification logic stays in
 * `ChallengeService`. Fetch failures fail-closed (empty answers, D-6) and the
 * resolver URL is configurable (`DOMAINS_DOH_RESOLVER`) for a self-hosted
 * resolver in production.
 */
@Configuration
class DomainConfig {

    @Bean
    fun txtLookupPort(
        @Value("\${domains.doh.resolver:https://dns.google/resolve}") resolver: String,
        @Value("\${domains.doh.timeout-ms:3000}") timeoutMs: Long,
    ): TxtLookupPort {
        val client = RestClient.builder()
            .requestFactory(
                JdkClientHttpRequestFactory(
                    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                ).apply { setReadTimeout(Duration.ofMillis(timeoutMs.coerceAtLeast(500))) },
            )
            .build()
        return DohTxtResolver(resolver) { url ->
            try {
                client.get().uri(url).retrieve().body(String::class.java)
            } catch (_: Exception) {
                null
            }
        }
    }
}

/** Re-export for config binding clarity; see [DomainConfig]. */
@Component
class DomainDefaults(
    @Value("\${domains.worker.enabled:true}") val workerEnabled: Boolean,
)
