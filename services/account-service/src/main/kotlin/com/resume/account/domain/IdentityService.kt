package com.resume.account.domain

import com.resume.account.data.LinkedAccountEntity
import com.resume.account.data.LinkedAccountRepository
import com.resume.account.data.UserEntity
import com.resume.account.data.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Verified IdP profile facts; avatar URLs are never consumed here (BR-15). */
data class IdpProfile(
    val providerId: String,
    val subject: String,
    val email: String?,
    val emailVerified: Boolean,
    val name: String?,
)

/** Owner-API projection of the account row (keeps the api layer free of entities). */
data class UserView(
    val id: String,
    val name: String?,
    val email: String?,
    val emailVerified: Boolean,
    val emailConflict: Boolean,
    val contactEmail: String?,
    val contactVerified: Boolean,
    val imageKey: String?,
    val createdAt: java.time.Instant,
)

/** Mesh consumer projection (identity sync): email facts only. */
data class UserEmailView(val email: String?, val emailVerified: Boolean)

sealed interface LinkOutcome {
    data class Linked(val link: LinkedAccountEntity) : LinkOutcome
    /** Same account already has this provider linked. */
    data object AlreadyLinked : LinkOutcome
    /** The provider subject is linked to a different account. */
    data object ForeignSubject : LinkOutcome
}

sealed interface UnlinkOutcome {
    data object Unlinked : UnlinkOutcome
    data object NotLinked : UnlinkOutcome
    /** BR-05: at least one provider must stay linked. */
    data object LastLink : UnlinkOutcome
}

/**
 * Stable identity by `(provider_id, provider_sub)` only - email never merges,
 * blocks, or forks accounts (BR-02). IdP-verified email syncs when
 * collision-free; a collision raises the notice flag with no cross-user
 * exposure (BR-03, AC-03). Names are sanitized before storage (BR-15).
 * All access paths are cross-user by nature and run under the `svc` context,
 * set by callers (callback flow / internal handlers) before the transaction.
 */
@Service
class IdentityService(
    private val users: UserRepository,
    private val links: LinkedAccountRepository,
    private val audit: AuditService,
    private val sanitizer: Sanitizer,
) {

    /** Find-or-create on login; `created` drives the first-login handoff (BR-14). */
    @Transactional
    fun resolve(profile: IdpProfile, ipClass: String?): Pair<UserEntity, Boolean> {
        val link = links.findByProviderIdAndProviderSub(profile.providerId, profile.subject)
        if (link != null) {
            val user = users.findById(link.userId).orElseThrow()
            syncEmail(user, profile.email, profile.emailVerified)
            prefillName(user, profile.name)
            link.emailAtLink = profile.email ?: link.emailAtLink
            links.save(link)
            users.save(user)
            audit.record(userId = user.id, action = "login", target = profile.providerId, ipClass = ipClass)
            return user to false
        }
        val now = java.time.Instant.now()
        val user = users.save(UserEntity(id = Tokens.newId("usr"), createdAt = now, updatedAt = now))
        syncEmail(user, profile.email, profile.emailVerified)
        prefillName(user, profile.name)
        users.save(user)
        links.save(
            LinkedAccountEntity(
                id = Tokens.newId("lnk"),
                userId = user.id,
                providerId = profile.providerId,
                providerSub = profile.subject,
                emailAtLink = profile.email,
                linkedAt = now,
            ),
        )
        audit.record(userId = user.id, action = "login", target = profile.providerId, ipClass = ipClass)
        return user to true
    }

    @Transactional
    fun link(userId: String, profile: IdpProfile, ipClass: String?): LinkOutcome {
        links.findByProviderIdAndProviderSub(profile.providerId, profile.subject)?.let { existing ->
            if (existing.userId == userId) return LinkOutcome.AlreadyLinked
            return LinkOutcome.ForeignSubject
        }
        val link = links.save(
            LinkedAccountEntity(
                id = Tokens.newId("lnk"),
                userId = userId,
                providerId = profile.providerId,
                providerSub = profile.subject,
                emailAtLink = profile.email,
            ),
        )
        audit.record(userId = userId, action = "link", target = profile.providerId, ipClass = ipClass)
        return LinkOutcome.Linked(link)
    }

    @Transactional
    fun unlink(userId: String, providerId: String, ipClass: String?): UnlinkOutcome {
        val link = links.findByUserIdAndProviderId(userId, providerId) ?: return UnlinkOutcome.NotLinked
        if (links.countByUserId(userId) <= 1) return UnlinkOutcome.LastLink
        links.delete(link)
        audit.record(userId = userId, action = "unlink", target = providerId, ipClass = ipClass)
        return UnlinkOutcome.Unlinked
    }

    @Transactional(readOnly = true)
    fun linkedProviders(userId: String): List<LinkedAccountEntity> = links.findByUserIdOrderByLinkedAtAsc(userId)

    @Transactional(readOnly = true)
    fun user(userId: String): UserEntity? = users.findById(userId).orElse(null)

    @Transactional(readOnly = true)
    fun emailView(userId: String): UserEmailView? = user(userId)?.let { UserEmailView(it.email, it.emailVerified) }

    @Transactional(readOnly = true)
    fun view(userId: String): UserView? = user(userId)?.let {
        UserView(
            id = it.id,
            name = it.name,
            email = it.email,
            emailVerified = it.emailVerified,
            emailConflict = it.emailConflict,
            contactEmail = it.contactEmail,
            contactVerified = it.contactVerifiedAt != null,
            imageKey = it.imageKey,
            createdAt = it.createdAt,
        )
    }

    @Transactional(readOnly = true)
    fun hasVerifiedContactElsewhere(userId: String, email: String): Boolean =
        users.countVerifiedContactOutside(userId, email) > 0

    /**
     * BR-03 email sync. Verified email updates the account only when no other
     * account carries it; a collision flags the notice and leaves the previous
     * value untouched. Unverified/absent (Facebook deny, Apple relay) still
     * creates/keeps the account - never a block.
     */
    private fun syncEmail(user: UserEntity, email: String?, verified: Boolean) {
        if (email.isNullOrBlank() || !verified) return
        val collides = users.byLowerEmail(email).any { it.id != user.id }
        if (collides) {
            user.emailConflict = true
        } else {
            user.email = email
            user.emailVerified = true
            user.emailConflict = false
        }
        user.updatedAt = java.time.Instant.now()
    }

    /** BR-15: IdP text is stripped of markup/bidi/control characters; set only when blank. */
    private fun prefillName(user: UserEntity, name: String?) {
        if (!user.name.isNullOrBlank()) return
        sanitizer.cleanName(name)?.let {
            user.name = it
            user.updatedAt = java.time.Instant.now()
        }
    }
}
