package com.resume.profile.events

import com.resume.profile.integration.AccountStatusClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * `events:account` consumer (feature 02 D-2): eager invalidation of
 * `profile.acctstat:{userId}` on `deletion_requested` / `deletion_cancelled` /
 * `purged`. At-least-once and replay-safe (invalidation is idempotent); lost
 * events self-heal via the 60 s cache TTL - the consumer is an accelerator,
 * never the correctness boundary (S-5). Consumer-group pattern matches the
 * platform stream convention (XAUTOCLAIM reclaim on the next poll).
 */
@Component
class AccountEventsConsumer(
    private val redis: StringRedisTemplate,
    private val accountStatus: AccountStatusClient,
    @Value("\${integration.account-events.consumer:profile-worker-1}")
    private val consumerName: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun ops() = redis.opsForStream<String, String>()

    @Scheduled(fixedDelay = 5_000)
    fun poll() {
        ensureGroup()
        reclaimIdle()
        val records = try {
            ops().read(
                Consumer.from(GROUP, consumerName),
                StreamReadOptions.empty().count(100).block(Duration.ofSeconds(2)),
                StreamOffset.create(STREAM, ReadOffset.lastConsumed()),
            )
        } catch (ex: Exception) {
            log.warn("account-events poll degraded: {}", ex.message)
            return
        }
        records.orEmpty().forEach { handle(it) }
    }

    private fun handle(record: MapRecord<String, String, String>) {
        val fields = record.value
        val userId = fields["userId"]
        try {
            when (fields["type"]) {
                DELETION_REQUESTED, DELETION_CANCELLED, PURGED -> {
                    if (!userId.isNullOrBlank()) accountStatus.invalidate(userId)
                }
                else -> log.debug("account event not handled: {}", fields["type"])
            }
            ops().acknowledge(STREAM, GROUP, record.id.value)
        } catch (ex: Exception) {
            log.warn("account-events handling degraded id={}: {}", record.id.value, ex.message)
        }
    }

    private fun ensureGroup() {
        try {
            ops().createGroup(STREAM, GROUP)
        } catch (_: Exception) {
            // Exists, or the stream does not yet (producers create it) - self-heals.
        }
    }

    private fun reclaimIdle() {
        try {
            @Suppress("UNCHECKED_CAST")
            val res = redis.execute { conn ->
                conn.execute(
                    "XAUTOCLAIM",
                    STREAM.toByteArray(), GROUP.toByteArray(), consumerName.toByteArray(),
                    "60000".toByteArray(), "0-0".toByteArray(),
                    "COUNT".toByteArray(), "50".toByteArray(),
                )
            } as? List<*> ?: return
            val entries = res.getOrNull(1) as? List<*> ?: return
            entries.forEach { e ->
                val pair = e as? List<*> ?: return@forEach
                val id = (pair.getOrNull(0) as? ByteArray)?.toString(Charsets.UTF_8) ?: return@forEach
                val flat = (pair.getOrNull(1) as? List<*>)?.map {
                    (it as? ByteArray)?.toString(Charsets.UTF_8) ?: ""
                } ?: return@forEach
                handle(
                    MapRecord.create(STREAM, flat.chunked(2).associate { it[0] to it[1] })
                        .withId(org.springframework.data.redis.connection.stream.RecordId.of(id)),
                )
            }
        } catch (ex: Exception) {
            log.warn("account-events reclaim degraded: {}", ex.message)
        }
    }

    private companion object {
        const val STREAM = "events:account"
        const val GROUP = "profile-account-events"
        const val DELETION_REQUESTED = "account.deletion_requested.v1"
        const val DELETION_CANCELLED = "account.deletion_cancelled.v1"
        const val PURGED = "account.purged.v1"
    }
}
