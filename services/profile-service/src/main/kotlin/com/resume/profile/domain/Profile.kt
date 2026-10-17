package com.resume.profile.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "profiles")
class Profile(
    @Id var id: String = "",
    @Column(name = "user_id", nullable = false) var userId: String = "",
    @Column(nullable = false, unique = true) var username: String = "",
    @Column(name = "display_name") var displayName: String? = null,
    @Column(name = "is_public", nullable = false) var isPublic: Boolean = true,
    @Column(name = "created_at") var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at") var updatedAt: Instant = Instant.now(),
)
