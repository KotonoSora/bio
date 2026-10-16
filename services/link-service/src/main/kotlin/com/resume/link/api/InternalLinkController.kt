package com.resume.link.api

import com.resume.link.domain.LinkService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Mesh-only target lookup. Public redirects go through GET /go/{slug}. */
@RestController
@RequestMapping("/internal/v1/links")
class InternalLinkController(private val links: LinkService) {
    @GetMapping("/by-slug/{slug}")
    fun bySlug(@PathVariable slug: String): Map<String, String> {
        val l = links.bySlug(slug).getOrThrow()
        return mapOf("id" to l.id, "slug" to l.slug, "targetUrl" to l.targetUrl)
    }
}
