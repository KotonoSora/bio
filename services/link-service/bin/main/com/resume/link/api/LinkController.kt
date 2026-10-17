package com.resume.link.api

import com.resume.link.domain.LinkService
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.net.URI

data class CreateLinkRequest(
    @field:NotBlank @field:Pattern(regexp = "^[a-zA-Z0-9_-]{3,64}$") val slug: String,
    @field:NotBlank @field:Pattern(regexp = "^https?://.+") val targetUrl: String,
)

@RestController
@RequestMapping("/api/v1/links")
class LinkController(private val links: LinkService) {
    @PostMapping
    fun create(auth: Authentication?, @RequestBody req: CreateLinkRequest): Map<String, String> {
        val userId = auth?.name ?: "dev-user"
        val saved = links.create(userId, req.slug, req.targetUrl).getOrThrow()
        return mapOf("id" to saved.id, "slug" to saved.slug)
    }
}

@RestController
class RedirectController(private val links: LinkService) {
    // Single redirector; cache-first with TTL-only correctness (spec 29-30).
    @GetMapping("/go/{slug}")
    fun redirect(@PathVariable slug: String): org.springframework.http.ResponseEntity<Void> {
        val link = links.bySlug(slug).getOrElse { throw ResponseStatusException(HttpStatus.NOT_FOUND) }
        return org.springframework.http.ResponseEntity.status(HttpStatus.FOUND).location(URI.create(link.targetUrl)).build()
    }
}
