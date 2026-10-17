package com.resume.profile.data

import com.resume.profile.domain.Profile
import com.resume.profile.domain.RetiredUsername
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface ProfileRepository : JpaRepository<Profile, String> {
    fun findByUsername(username: String): Profile?
    fun findByUserId(userId: String): List<Profile>
    fun findByUserIdOrderByCreatedAtAsc(userId: String): List<Profile>
    fun countByUserId(userId: String): Long

    /** Purge path (feature 02): svc-scoped delete, explicit user predicate, returns rows removed. */
    @Modifying
    @Transactional
    @Query("delete from Profile p where p.userId = :userId")
    fun deleteByUserId(@Param("userId") userId: String): Int
}

/** Retirement tombstones (feature 02, V5). */
interface RetiredUsernameRepository : JpaRepository<RetiredUsername, String> {
    fun existsByUsername(username: String): Boolean

    /** Idempotent tombstone: a re-run of the same purge never fails on the PK. */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO retired_usernames (username) VALUES (:username) ON CONFLICT (username) DO NOTHING", nativeQuery = true)
    fun retireIfAbsent(@Param("username") username: String): Int
}
