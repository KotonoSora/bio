package com.resume.resume.domain

import com.resume.resume.data.ResumeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ResumeService(private val repo: ResumeRepository) {

    @Transactional(readOnly = true)
    fun list(userId: String): List<ResumeDocument> = repo.findByUserId(userId)

    @Transactional(readOnly = true)
    fun byId(id: String): Result<ResumeDocument> =
        repo.findById(id).map { Result.success(it) }.orElseGet { Result.failure(NoSuchElementException("resume not found")) }

    @Transactional
    fun create(userId: String, title: String, bodyJson: String): Result<ResumeDocument> {
        val saved = repo.save(ResumeDocument(id = "rsm_" + UUID.randomUUID().toString().take(8), userId = userId, title = title, bodyJson = bodyJson))
        return Result.success(saved)
    }
}
