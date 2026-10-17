package com.resume.publish.api

import com.resume.publish.domain.PublishScheduleService
import com.resume.publish.domain.ScheduledPost
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Mesh-only scheduled-post read. */
@RestController
@RequestMapping("/internal/v1/posts")
class InternalPublishController(private val publishing: PublishScheduleService) {
    @GetMapping("/{id}")
    fun byId(@PathVariable id: String): ScheduledPost = publishing.byId(id).getOrThrow()
}
