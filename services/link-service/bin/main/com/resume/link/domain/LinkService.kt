package com.resume.link.domain

import com.resume.link.events.DomainEvent
import com.resume.link.events.EventPublisher

import com.resume.link.data.ShortLinkRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

const val LINK_CREATED_V1 = "link.created.v1"


@Service
class LinkService(
    private val repo: ShortLinkRepository,
    private val audit: AuditService,
    private val events: EventPublisher,
) {

    @Transactional
    fun create(userId: String, slug: String, targetUrl: String): Result<ShortLink> {
        if (repo.findBySlug(slug) != null) return Result.failure(IllegalStateException("slug taken"))
        val saved = repo.save(ShortLink(id = "lnk_" + UUID.randomUUID().toString().take(8), userId = userId, slug = slug, targetUrl = targetUrl))
        audit.append(userId, "link", "link.create", saved.id, saved.slug)
        events.publishAfterCommit(DomainEvent(LINK_CREATED_V1, saved.id, userId, payload = mapOf("slug" to saved.slug)))
        return Result.success(saved)
    }

    @Transactional(readOnly = true)
    fun bySlug(slug: String): Result<ShortLink> =
        repo.findBySlug(slug)?.let { Result.success(it) }
            ?: Result.failure(NoSuchElementException("link not found"))
}
