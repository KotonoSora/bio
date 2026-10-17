package com.resume.profile.domain

import com.resume.profile.data.ProfileRepository
import com.resume.profile.data.RetiredUsernameRepository
import com.resume.profile.events.DomainEvent
import com.resume.profile.events.EventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

const val PROFILE_PUBLISHED_V1 = "profile.published.v1"
const val USERNAME_RENAMED_V1 = "username.renamed.v1"


val RESERVED_SLUGS = setOf("app","api","admin","static","r","cv","shop","login","signup","pricing","blog","go","a")

@Service
class ProfileService(
    private val repo: ProfileRepository,
    private val retired: RetiredUsernameRepository,
    private val audit: AuditService,
    private val events: EventPublisher,
) {
    @Transactional(readOnly = true)
    fun byUsername(username: String): Result<Profile> =
        repo.findByUsername(username)?.let { Result.success(it) }
            ?: Result.failure(NoSuchElementException("profile not found"))

    /** Mesh handoff read (feature 01 T-10): caller runs under the `svc` RLS scope. */
    @Transactional(readOnly = true)
    fun byUser(userId: String): Result<Profile> =
        repo.findByUserIdOrderByCreatedAtAsc(userId).firstOrNull()?.let { Result.success(it) }
            ?: Result.failure(NoSuchElementException("profile not found"))

    /**
     * Claim (feature 02 BR-07 order): reserved -> retired -> taken. A suspended
     * (grace) profile still owns its name (`username_taken`); a purged one keeps
     * it retired forever (`username_retired`). A raced claim maps the DB unique
     * violation to `username_taken`, never a 500.
     */
    @Transactional
    fun create(userId: String, username: String, displayName: String?): Profile {
        val slug = username.lowercase()
        if (slug in RESERVED_SLUGS) throw UsernameReservedException()
        if (retired.existsByUsername(slug)) throw UsernameRetiredException()
        if (repo.findByUsername(slug) != null) throw UsernameTakenException()
        val p = try {
            repo.saveAndFlush(
                Profile(id = "prf_" + UUID.randomUUID().toString().take(8), userId = userId, username = slug, displayName = displayName),
            )
        } catch (_: DataIntegrityViolationException) {
            throw UsernameTakenException()
        }
        audit.append(userId, "profile", "profile.create", p.id, p.username)
        events.publishAfterCommit(DomainEvent(PROFILE_PUBLISHED_V1, p.id, userId, payload = mapOf("username" to p.username)))
        return p
    }

    @Transactional(readOnly = true)
    fun mine(userId: String): List<Profile> = repo.findByUserId(userId)
}
