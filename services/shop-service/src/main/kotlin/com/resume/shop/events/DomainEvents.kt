package com.resume.shop.events

import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Instant

/**
 * Events: `<domain>.<fact>.v<major>` (e.g. `shop.product_published.v1`), emitted after commit,
 * at-least-once, idempotent consumers. Transport is a Redis Stream with consumer
 * groups (XAUTOCLAIM reclaim, max-delivery bound, dead-letter stream); consumers
 * replay-safe. Lost events self-heal via cache TTLs (60 s default bound).
 */
data class DomainEvent(
    val type: String,
    val aggregateId: String,
    val userId: String,
    val occurredAt: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
)

@Component
class EventPublisher(private val redis: StringRedisTemplate) {
    fun publishAfterCommit(event: DomainEvent) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() = publish(event)
                },
            )
        } else {
            publish(event)
        }
    }

    fun publish(event: DomainEvent) {
        val fields = mutableMapOf(
            "type" to event.type,
            "aggregateId" to event.aggregateId,
            "userId" to event.userId,
            "occurredAt" to event.occurredAt.toString(),
        )
        event.payload.forEach { (k, v) -> fields["attr.$k"] = v }
        val record: MapRecord<String, String, String> = MapRecord.create("events:shop", fields)
        redis.opsForStream<String, String>().add(record)
    }
}
