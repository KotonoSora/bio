package com.resume.shop.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** Uniform product catalog (7 kinds); money is VND integer (Long), never decimals. */
enum class ProductKind { DIGITAL, PHYSICAL, COURSE, SERVICE, TIP, MEMBERSHIP, BUNDLE }

@Entity
@Table(name = "products")
class Product(
    @Id var id: String = "",
    @Column(name = "user_id", nullable = false) var userId: String = "",
    @Enumerated(EnumType.STRING) var kind: ProductKind = ProductKind.DIGITAL,
    @Column(nullable = false) var title: String = "",
    @Column(name = "price_vnd", nullable = false) var priceVnd: Long = 0,
    @Column(name = "is_public", nullable = false) var isPublic: Boolean = true,
    @Column(name = "created_at") var createdAt: Instant = Instant.now(),
)
