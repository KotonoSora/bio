package com.resume.account.events

import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Instant

/**
 * Account events (feature 02): `account.deletion_requested.v1`,
 * `account.deletion_cancelled.v1`, `account.purged.v1` - emitted after commit,
 * at-least-once, idempotent consumers (TL 03 "Events"). Transport is the
 * platform Redis Stream pattern (`events:account`); lost events self-heal via
 * the consumer-side 60 s account-status cache bound.
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
        val record: MapRecord<String, String, String> = MapRecord.create("events:account", fields)
        try {
            redis.opsForStream<String, String>().add(record)
        } catch (ex: Exception) {
            // Consumer caches self-heal within 60 s (S-5); events are best effort.
            org.slf4j.LoggerFactory.getLogger(javaClass).warn("account event publish degraded: {}", ex.message)
        }
    }

    companion object {
        const val DELETION_REQUESTED_V1 = "account.deletion_requested.v1"
        const val DELETION_CANCELLED_V1 = "account.deletion_cancelled.v1"
        const val PURGED_V1 = "account.purged.v1"
        const val STREAM = "events:account"
    }
}
