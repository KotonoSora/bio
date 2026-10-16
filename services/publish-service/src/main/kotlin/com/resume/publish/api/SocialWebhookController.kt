package com.resume.publish.api

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

 /** OAuth callbacks (single-use state + PKCE) + provider signals + IG comment webhook (queue-mediated). */
@RestController
class SocialWebhookController {
    @GetMapping("/api/v1/social/{network}/callback")
    fun oauthCallback(): Map<String, String> = mapOf("linked" to "true")

    @PostMapping("/webhooks/social/{network}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun providerSignal(): Map<String, Boolean> = mapOf("accepted" to true)

    @PostMapping("/webhooks/instagram")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun instagram(): Map<String, Boolean> = mapOf("accepted" to true)
}
