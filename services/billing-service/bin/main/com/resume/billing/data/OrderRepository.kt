package com.resume.billing.data

import com.resume.billing.domain.Order
import org.springframework.data.jpa.repository.JpaRepository

interface OrderRepository : JpaRepository<Order, String> {
    fun findByIdempotencyKey(key: String): Order?
}
