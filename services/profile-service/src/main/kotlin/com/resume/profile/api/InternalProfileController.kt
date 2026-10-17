package com.resume.profile.api

import com.resume.profile.domain.ProfileService
import com.resume.profile.security.RlsUserHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class ProfileByUserResponse(val username: String)

/** Mesh read: no profile for the account yet (feature 01 first-login handoff). */
class ProfileAbsentException : NoSuchElementException("No profile for that account.")

/**
 * Namespace authority read API (consumed by link-service; feature 01 T-10
 * handoff read). Mesh-only, service JWT.
 */
@RestController
@RequestMapping("/internal/v1/profiles")
class InternalProfileController(private val profiles: ProfileService) {
    @GetMapping("/by-username/{username}")
    fun byUsername(@PathVariable username: String): ProfileResponse {
        val p = profiles.byUsername(username.lowercase()).getOrThrow()
        return ProfileResponse(p.id, p.username, p.displayName, p.isPublic)
    }

    /** First-login handoff (BR-14): `svc` RLS scope + explicit user predicate. */
    @GetMapping("/by-user/{userId}")
    fun byUser(@PathVariable userId: String): ProfileByUserResponse = RlsUserHolder.asService {
        val p = profiles.byUser(userId).getOrElse { throw ProfileAbsentException() }
        ProfileByUserResponse(p.username)
    }
}
