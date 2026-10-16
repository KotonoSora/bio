package com.resume.profile.domain

import com.resume.profile.data.ProfileRepository
import com.resume.profile.events.DomainEvent
import com.resume.profile.events.EventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

const val PROFILE_PUBLISHED_V1 = "profile.published.v1"
const val USERNAME_RENAMED_V1 = "username.renamed.v1"


val RESERVED_SLUGS = setOf("app","api","admin","static","r","cv","shop","login","signup","pricing","blog","go","a")

@Service
class ProfileService(
    private val repo: ProfileRepository,
    private val audit: AuditService,
    private val events: EventPublisher,
) {
    @Transactional(readOnly = true)
    fun byUsername(username: String): Result<Profile> =
        repo.findByUsername(username)?.let { Result.success(it) }
            ?: Result.failure(NoSuchElementException("profile not found"))

    @Transactional
    fun create(userId: String, username: String, displayName: String?): Result<Profile> {
        val slug = username.lowercase()
        if (slug in RESERVED_SLUGS) return Result.failure(IllegalStateException("reserved slug"))
        if (repo.findByUsername(slug) != null) return Result.failure(IllegalStateException("username taken"))
        val p = repo.save(Profile(id = "prf_" + UUID.randomUUID().toString().take(8), userId = userId, username = slug, displayName = displayName))
        audit.append(userId, "profile", "profile.create", p.id, p.username)
        events.publishAfterCommit(DomainEvent(PROFILE_PUBLISHED_V1, p.id, userId, payload = mapOf("username" to p.username)))
        return Result.success(p)
    }

    @Transactional(readOnly = true)
    fun mine(userId: String): List<Profile> = repo.findByUserId(userId)
}
