package com.resume.billing.api

import com.resume.billing.security.WebhookVerifier
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

/** Rail callbacks: signature/token-verified per rail, idempotency-keyed. Uniform 202. */
@RestController
@RequestMapping("/webhooks")
class RailWebhookController(private val verifier: WebhookVerifier) {
    @PostMapping("/{provider}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun webhook(
        @PathVariable provider: String,
        @RequestHeader(value = "X-Signature", required = false) signature: String?,
        @RequestHeader(value = "X-Timestamp", required = false) timestamp: String?,
        @RequestBody(required = false) rawBody: String?,
        @RequestHeader(value = "X-Forwarded-For", required = false) forwardedFor: String?,
        exchange: jakarta.servlet.http.HttpServletRequest,
    ): Map<String, Boolean> {
        val remoteIp = forwardedFor?.split(",")?.firstOrNull()?.trim() ?: exchange.remoteAddr
        if (!verifier.verify(provider, signature, timestamp, rawBody ?: "", remoteIp)) {
            throw org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "bad webhook signature")
        }
        return mapOf("accepted" to true)
    }
}
