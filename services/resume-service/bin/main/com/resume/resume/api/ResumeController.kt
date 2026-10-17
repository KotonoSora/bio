package com.resume.resume.api

import com.resume.resume.domain.ResumeService
import jakarta.validation.constraints.NotBlank
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

data class UpsertResumeRequest(@field:NotBlank val title: String, @field:NotBlank val bodyJson: String)
data class ResumeResponse(val id: String, val title: String, val bodyJson: String)

@RestController
@RequestMapping("/api/v1/resumes")
class ResumeController(private val resumes: ResumeService) {
    @GetMapping
    fun list(auth: Authentication?): List<ResumeResponse> {
        val userId = auth?.name ?: "dev-user"
        return resumes.list(userId).map { ResumeResponse(it.id, it.title, it.bodyJson) }
    }

    @PostMapping
    fun create(auth: Authentication?, @RequestBody req: UpsertResumeRequest): ResumeResponse {
        val userId = auth?.name ?: "dev-user"
        val saved = resumes.create(userId, req.title, req.bodyJson).getOrThrow()
        return ResumeResponse(saved.id, saved.title, saved.bodyJson)
    }
}
