package com.resume.link.data

import com.resume.link.domain.ShortLink
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface ShortLinkRepository : JpaRepository<ShortLink, String> {
    fun findBySlug(slug: String): ShortLink?
    fun findByUserIdOrderByCreatedAtAsc(userId: String): List<ShortLink>
    fun countByUserId(userId: String): Long

    /** Purge path (feature 02): svc-scoped delete, explicit user predicate, returns rows removed. */
    @Modifying
    @Transactional
    @Query("delete from ShortLink s where s.userId = :userId")
    fun deleteByUserId(@Param("userId") userId: String): Int
}
