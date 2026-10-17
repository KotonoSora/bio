package com.resume.resume.data

import com.resume.resume.domain.ResumeDocument
import org.springframework.data.jpa.repository.JpaRepository

interface ResumeRepository : JpaRepository<ResumeDocument, String> {
    fun findByUserId(userId: String): List<ResumeDocument>
}
