package com.resume.account.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "accounts")
class Account(
    @Id var id: String = "",
    @Column(name = "user_id", nullable = false) var userId: String = "",
    @Column(nullable = false) var email: String = "",
    @Column(name = "display_name") var displayName: String? = null,
    @Column(name = "entitlement_tier") var entitlementTier: String = "free",
    @Column(name = "created_at") var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at") var updatedAt: Instant = Instant.now(),
)
