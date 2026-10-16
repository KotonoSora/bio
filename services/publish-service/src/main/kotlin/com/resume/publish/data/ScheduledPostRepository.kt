package com.resume.publish.data

import com.resume.publish.domain.ScheduledPost
import org.springframework.data.jpa.repository.JpaRepository

interface ScheduledPostRepository : JpaRepository<ScheduledPost, String> {
    fun findByUserId(userId: String): List<ScheduledPost>
}
