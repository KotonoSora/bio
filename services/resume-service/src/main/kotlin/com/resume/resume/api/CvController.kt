package com.resume.resume.api

import com.resume.resume.domain.ResumeService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Public read: scoped by id + is_public=1; non-public returns 404 (410/404 parity preserved). */
@RestController
@RequestMapping("/cv")
class CvController(private val resumes: ResumeService) {
    @GetMapping("/{id}")
    fun byId(@PathVariable id: String): ResumeResponse {
        val r = resumes.byId(id).getOrThrow()
        if (!r.isPublic) throw NoSuchElementException("resume not found")
        return ResumeResponse(r.id, r.title, r.bodyJson)
    }
}
