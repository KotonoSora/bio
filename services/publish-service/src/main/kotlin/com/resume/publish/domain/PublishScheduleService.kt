package com.resume.publish.domain

import com.resume.publish.events.DomainEvent
import com.resume.publish.events.EventPublisher

import com.resume.publish.data.ScheduledPostRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

const val POST_SCHEDULED_V1 = "publish.scheduled.v1"

@Service
class PublishScheduleService(
    private val repo: ScheduledPostRepository,
    private val events: EventPublisher,
) {

    @Transactional
    fun schedule(userId: String, network: String, body: String, scheduledAt: Instant?): Result<ScheduledPost> {
        val saved = repo.save(ScheduledPost(id = "pub_" + UUID.randomUUID().toString().take(8), userId = userId, network = network, body = body, scheduledAt = scheduledAt ?: Instant.now()))
        return Result.success(saved)
    }

    @Transactional(readOnly = true)
    fun list(userId: String): List<ScheduledPost> = repo.findByUserId(userId)

    @Transactional(readOnly = true)
    fun byId(id: String): Result<ScheduledPost> =
        repo.findById(id).map { Result.success(it) }.orElseGet { Result.failure(NoSuchElementException("post not found")) }
}
