package com.resume.billing.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** Eight equal rails; explicit provider, no default. VND integers; idempotent grants. */
enum class PayProvider { PAYOS, SEPAY, VNPAY, ZALOPAY, NAPAS, GOOGLE_PAY, APPLE_PAY, STRIPE }

@Entity
@Table(name = "orders")
class Order(
    @Id var id: String = "",
    @Column(name = "user_id", nullable = false) var userId: String = "",
    @Column(name = "idempotency_key", nullable = false, unique = true) var idempotencyKey: String = "",
    @Enumerated(EnumType.STRING) var provider: PayProvider = PayProvider.PAYOS,
    @Column(name = "amount_vnd", nullable = false) var amountVnd: Long = 0,
    @Column(nullable = false) var status: String = "PENDING",
    @Column(name = "created_at") var createdAt: Instant = Instant.now(),
)
