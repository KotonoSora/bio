package com.resume.publicsite.api

import jakarta.validation.constraints.NotBlank
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class PurgeRequest(
    @field:NotBlank val tag: String,
    val warmSlugs: List<String> = emptyList(),
)

/** Mesh-only cache purge (publish purges by tag pattern; rename/retire purges prior slugs). */
@RestController
@RequestMapping("/internal/v1/purge")
class InternalPurgeController(private val preRender: PreRenderService) {
    @PostMapping
    fun purge(@RequestBody req: PurgeRequest): Map<String, String> {
        val removed = preRender.purgeByTag(req.tag)
        preRender.warm(req.warmSlugs)
        return mapOf("tag" to req.tag, "purged" to "true", "removed" to removed.toString())
    }
}
