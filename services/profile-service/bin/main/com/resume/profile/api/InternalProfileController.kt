package com.resume.profile.api

import com.resume.profile.domain.ProfileService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Namespace authority read API (consumed by link-service). Mesh-only, service JWT. */
@RestController
@RequestMapping("/internal/v1/profiles")
class InternalProfileController(private val profiles: ProfileService) {
    @GetMapping("/by-username/{username}")
    fun byUsername(@PathVariable username: String): ProfileResponse {
        val p = profiles.byUsername(username.lowercase()).getOrThrow()
        return ProfileResponse(p.id, p.username, p.displayName, p.isPublic)
    }
}
