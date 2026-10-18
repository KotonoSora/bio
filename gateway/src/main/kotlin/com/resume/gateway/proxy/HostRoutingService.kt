package com.resume.gateway.proxy

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.resume.gateway.security.InternalJwtService
import com.resume.gateway.security.PlatformHosts
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.time.Duration

@JsonIgnoreProperties(ignoreUnknown = true)
data class ResolveBody(
    val result: String = "unknown",
    val username: String? = null,
    val canonicalHost: String? = null,
)

/**
 * Feature 04 D-4/T-04: the serving gate for non-platform Hosts. A request
 * arriving on a foreign host never reaches a profile path before this
 * decision: `profile` only when the binding is `verified` + plan pro (the
 * mesh resolve applies the full gate); `blocked` and `unknown` render the
 * same neutral error (NFR-09) and are cached negative for 60 s; resolve
 * failure degrades to the neutral error, never to serving (fail-safe) with a
 * counter. Positive decisions cache 30 s (contract: feature 10 namespace);
 * lost events self-heal inside those TTLs - the decision cache is an
 * accelerator, never the correctness boundary (BR-04).
 */
@Service
class HostRoutingService(
    private val redis: StringRedisTemplate,
    private val jwt: InternalJwtService,
    private val platformHosts: PlatformHosts,
    meters: MeterRegistry,
    @Value("\${edge.profile-service-url:http://profile-service:8080}") private val profileUrl: String,
    @Value("\${edge.host-resolve-timeout-ms:2000}") timeoutMs: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val neutralCount = meters.counter("gateway.host.neutral")
    private val degradeCount = meters.counter("gateway.host.resolve_degrade")

    private val client: RestClient = RestClient.builder()
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
            ).apply { setReadTimeout(Duration.ofMillis(timeoutMs.coerceAtLeast(300))) },
        )
        .build()

    sealed interface Decision {
        /** Platform host: the existing routing applies untouched. */
        data object Platform : Decision

        /** Serve the bound profile's SSR with host context (canonical = this host). */
        data class Serve(val username: String, val canonicalHost: String) : Decision

        /** Neutral error page; `reason` is telemetry only (never rendered). */
        data class Neutral(val reason: String) : Decision
    }

    fun decide(rawHost: String?): Decision {
        val host = PlatformHosts.normalize(rawHost)
        if (platformHosts.isPlatform(host)) return Decision.Platform
        cached(host)?.let { return it }
        val resolved = try {
            resolve(host)
        } catch (ex: Exception) {
            log.warn("host resolve degraded host={}: {}", host, ex.message)
            degradeCount.increment()
            null
        }
        val decision = resolved ?: Decision.Neutral("resolve_degraded")
        cache(host, decision)
        return decision
    }

    /** `domain.state_changed.v1` consumer hook (D-4): drop the cached decision. */
    fun invalidate(host: String) {
        try {
            redis.delete(key(host))
        } catch (_: Exception) {
            // TTL heals inside 60 s
        }
    }

    private fun resolve(host: String): Decision {
        val uri = URI.create("$profileUrl/internal/v1/domains/${URLEncoder.encode(host, Charsets.UTF_8)}")
        val body = client.get().uri(uri)
            .headers { h ->
                if (jwt.configured) {
                    h.set(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.issue(userId = "svc:edge-gateway", audience = "profile-service"))
                }
            }
            .retrieve()
            .body(ResolveBody::class.java)
        return when (body?.result) {
            "profile" -> {
                val username = body.username
                if (username.isNullOrBlank()) Decision.Neutral("resolve_malformed")
                else Decision.Serve(username, body.canonicalHost ?: host)
            }
            "blocked" -> Decision.Neutral("blocked")
            else -> Decision.Neutral("unknown")
        }
    }

    private fun cached(host: String): Decision? {
        val value = try {
            redis.opsForValue().get(key(host))
        } catch (_: Exception) {
            null
        } ?: return null
        return when {
            value == "blocked" -> Decision.Neutral("blocked")
            value == "unknown" -> Decision.Neutral("unknown")
            value.startsWith("p:") -> {
                val parts = value.removePrefix("p:").split('|', limit = 2)
                Decision.Serve(parts[0], parts.getOrElse(1) { host })
            }
            else -> null
        }
    }

    private fun cache(host: String, decision: Decision) {
        val value = when (decision) {
            is Decision.Serve -> "p:${decision.username}|${decision.canonicalHost}"
            is Decision.Neutral -> if (decision.reason == "unknown") "unknown" else "blocked"
            Decision.Platform -> return
        }
        val ttl = if (decision is Decision.Serve) POSITIVE_TTL else NEGATIVE_TTL
        try {
            redis.opsForValue().set(key(host), value, ttl)
        } catch (_: Exception) {
            // cache-only; the resolve repeats next request
        }
        if (decision is Decision.Neutral) neutralCount.increment()
    }

    private fun key(host: String) = "gw:domain:$host"

    companion object {
        private val POSITIVE_TTL = Duration.ofSeconds(30)
        private val NEGATIVE_TTL = Duration.ofSeconds(60)
    }
}
