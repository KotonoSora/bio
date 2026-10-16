package com.resume.resume.api

import com.resume.resume.domain.ResumeService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Mesh-only document read (snapshot versions are immutable; served from the same repo). */
@RestController
@RequestMapping("/internal/v1/resumes")
class InternalResumeController(private val resumes: ResumeService) {
    @GetMapping("/{id}")
    fun byId(@PathVariable id: String): ResumeResponse {
        val r = resumes.byId(id).getOrThrow()
        return ResumeResponse(r.id, r.title, r.bodyJson)
    }
}
