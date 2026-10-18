package com.resume.publicsite.api

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

/**
 * SSR public pages behind the gateway (features 10-11). Stateless: cache + purge only.
 * Cache correctness: TTLs bound staleness, purge events shorten it (60s default heal bound).
 * Feature 04: the gateway sets `X-RB-Host-Context` only for a resolve-verified
 * custom host; the client's own value never survives the proxy (stripped).
 */
@RestController
class PublicPageController(private val preRender: PreRenderService) {
    @GetMapping("/{username}")
    fun page(
        @PathVariable username: String,
        @RequestHeader(value = "X-RB-Host-Context", required = false) hostContext: String?,
    ): Map<String, String> = preRender.page(username, hostContext)

    @GetMapping("/health/detail")
    fun detail(): Map<String, String> = mapOf("mode" to "stateless", "cache" to "edge")
}
