package com.resume.publicsite.api

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

/**
 * SSR public pages behind the gateway (features 10-11). Stateless: cache + purge only.
 * Cache correctness: TTLs bound staleness, purge events shorten it (60s default heal bound).
 */
@RestController
class PublicPageController(private val preRender: PreRenderService) {
    @GetMapping("/{username}")
    fun page(@PathVariable username: String): Map<String, String> =
        preRender.page(username)

    @GetMapping("/health/detail")
    fun detail(): Map<String, String> = mapOf("mode" to "stateless", "cache" to "edge")
}
