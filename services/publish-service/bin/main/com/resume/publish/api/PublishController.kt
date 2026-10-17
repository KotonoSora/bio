package com.resume.publish.api

import com.resume.publish.domain.PublishScheduleService
import com.resume.publish.domain.ScheduledPost
import jakarta.validation.constraints.NotBlank
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.Instant

data class ScheduleRequest(@field:NotBlank val network: String, @field:NotBlank val body: String, val scheduledAt: Instant?)

@RestController
@RequestMapping("/api/v1/publish/posts")
class PublishController(private val publishing: PublishScheduleService) {
    @PostMapping
    fun schedule(auth: Authentication?, @RequestBody req: ScheduleRequest): ScheduledPost {
        val userId = auth?.name ?: "dev-user"
        return publishing.schedule(userId, req.network, req.body, req.scheduledAt).getOrThrow()
    }

    @GetMapping
    fun list(auth: Authentication?): List<ScheduledPost> {
        val userId = auth?.name ?: "dev-user"
        return publishing.list(userId)
    }
}
