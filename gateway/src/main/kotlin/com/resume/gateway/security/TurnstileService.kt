package com.resume.gateway.security

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

/**
 * Turnstile-class challenge wiring at the gateway for abuse-prone routes
 * (beacon bursts, lead submits). The client never holds provider keys.
 * Env-gated: with no secret configured the challenge is logged-but-open (dev);
 * with a secret it verifies against the provider and fails closed on invalid.
 */
@Service
class TurnstileService(
    @Value("\${TURNSTILE_SECRET:}") private val secret: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val client = RestClient.create("https://challenges.cloudflare.com")

    val enforced: Boolean get() = secret.isNotBlank()

    fun verify(token: String?, remoteIp: String?): Boolean {
        if (!enforced) {
            log.warn("turnstile not configured: allowing request (dev only)")
            return true
        }
        if (token.isNullOrBlank()) return false
        return try {
            val res = client.post().uri("/turnstile/v0/siteverify")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("secret=$secret&response=$token&remoteip=${remoteIp ?: ""}")
                .retrieve().body(Map::class.java)
            res?.get("success") == true
        } catch (ex: Exception) {
            log.warn("turnstile verification degraded: {}", ex.message)
            false // fail-closed when enforced
        }
    }
}
