package com.resume.billing.domain

import com.resume.billing.events.DomainEvent
import com.resume.billing.events.EventPublisher

import com.resume.billing.data.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

const val GRANT_ISSUED_V1 = "billing.grant_issued.v1"

@Service
class CheckoutService(
    private val orders: OrderRepository,
    private val audit: AuditService,
    private val events: EventPublisher,
) {

    @Transactional
    fun checkout(userId: String, idempotencyKey: String, provider: PayProvider, amountVnd: Long): Result<Order> {
        // Idempotent grant front door: replay returns the original order, never a duplicate grant.
        orders.findByIdempotencyKey(idempotencyKey)?.let {
            audit.append(userId, "checkout", "order.replay", it.id, it.status)
            return Result.success(it)
        }
        if (amountVnd < 0) return Result.failure(IllegalArgumentException("negative amount"))
        val saved = orders.save(Order(id = "ord_" + UUID.randomUUID().toString().take(8), userId = userId, idempotencyKey = idempotencyKey, provider = provider, amountVnd = amountVnd))
        audit.append(userId, "checkout", "order.create", saved.id, saved.status)
        events.publishAfterCommit(DomainEvent(GRANT_ISSUED_V1, saved.id, userId, payload = mapOf("provider" to saved.provider.name)))
        return Result.success(saved)
    }
}
