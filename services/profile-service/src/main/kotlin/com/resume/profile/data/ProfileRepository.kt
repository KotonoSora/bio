package com.resume.profile.data

import com.resume.profile.domain.Profile
import org.springframework.data.jpa.repository.JpaRepository

interface ProfileRepository : JpaRepository<Profile, String> {
    fun findByUsername(username: String): Profile?
    fun findByUserId(userId: String): List<Profile>
    fun findByUserIdOrderByCreatedAtAsc(userId: String): List<Profile>
}
