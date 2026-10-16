package com.resume.link.data

import com.resume.link.domain.ShortLink
import org.springframework.data.jpa.repository.JpaRepository

interface ShortLinkRepository : JpaRepository<ShortLink, String> {
    fun findBySlug(slug: String): ShortLink?
}
