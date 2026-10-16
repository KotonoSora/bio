package com.resume.link.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "short_links")
class ShortLink(
    @Id var id: String = "",
    @Column(name = "user_id", nullable = false) var userId: String = "",
    @Column(nullable = false, unique = true) var slug: String = "",
    @Column(name = "target_url", nullable = false) var targetUrl: String = "",
    @Column(name = "created_at") var createdAt: Instant = Instant.now(),
)
