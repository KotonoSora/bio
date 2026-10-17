package com.resume.billing.domain

import com.resume.billing.data.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Mesh grant reads. Grants are idempotent by design; replay-safe. */
@Service
class GrantService(private val orders: OrderRepository) {

    @Transactional(readOnly = true)
    fun byOrder(orderId: String): Result<Order> =
        orders.findById(orderId).map { Result.success(it) }
            .orElseGet { Result.failure(NoSuchElementException("order not found")) }
}
