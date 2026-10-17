package com.resume.resume.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "resume_documents")
class ResumeDocument(
    @Id var id: String = "",
    @Column(name = "user_id", nullable = false) var userId: String = "",
    @Column(nullable = false) var title: String = "",
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "body_json", nullable = false) var bodyJson: String = "{}",
    @Column(name = "is_public", nullable = false) var isPublic: Boolean = false,
    @Column(name = "created_at") var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at") var updatedAt: Instant = Instant.now(),
)
