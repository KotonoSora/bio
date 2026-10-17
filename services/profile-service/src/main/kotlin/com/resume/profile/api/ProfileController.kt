package com.resume.profile.api

import com.resume.profile.domain.ProfileService
import com.resume.profile.integration.AccountStatusClient
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

data class CreateProfileRequest(
    @field:NotBlank @field:Pattern(regexp = "^[a-z0-9_]{3,30}$") val username: String,
    val displayName: String?,
)
data class ProfileResponse(val id: String, val username: String, val displayName: String?, val isPublic: Boolean)

@RestController
@RequestMapping("/api/v1/profiles")
class ProfileController(private val profiles: ProfileService) {
    @PostMapping
    fun create(auth: Authentication?, @RequestBody req: CreateProfileRequest): ProfileResponse {
        val userId = auth?.name ?: "dev-user"
        val p = profiles.create(userId, req.username, req.displayName)
        return ProfileResponse(p.id, p.username, p.displayName, p.isPublic)
    }

    @GetMapping("/me")
    fun mine(auth: Authentication?): List<ProfileResponse> {
        val userId = auth?.name ?: "dev-user"
        return profiles.mine(userId).map { ProfileResponse(it.id, it.username, it.displayName, it.isPublic) }
    }
}

@RestController
class PublicProfileController(
    private val profiles: ProfileService,
    private val accountStatus: AccountStatusClient,
) {
    /**
     * Public read: username + is_public=1 scoping happens here; dashboard reads
     * scope by server-set user_id. Deactivated accounts (feature 02) resolve as
     * `missing` within the 60 s `profile.acctstat` bound - identical shape to
     * private/unknown (BR-04 parity; feature 06 TL).
     */
    @GetMapping("/{username}")
    fun byUsername(@PathVariable username: String): ProfileResponse {
        val p = profiles.byUsername(username.lowercase()).getOrThrow()
        if (!p.isPublic || !accountStatus.isActive(p.userId)) throw NoSuchElementException("profile not found")
        return ProfileResponse(p.id, p.username, p.displayName, p.isPublic)
    }
}
