package com.resume.publish.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "scheduled_posts")
class ScheduledPost(
    @Id var id: String = "",
    @Column(name = "user_id", nullable = false) var userId: String = "",
    @Column(nullable = false) var network: String = "",
    @Column(nullable = false) var body: String = "",
    @Column(name = "scheduled_at", nullable = false) var scheduledAt: Instant = Instant.now(),
    @Column(nullable = false) var status: String = "SCHEDULED",
)
