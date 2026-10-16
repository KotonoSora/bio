package com.resume.analytics.stream

import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * Beacon queue consumer (Redis Streams + consumer group). Idempotent aggregation
 * into realtime counters (`rt:{username}:{minute}`, TTL 65 min — eviction is a
 * correct zero, never an error path). Pending entries idle > 60 s are reclaimed
 * via XAUTOCLAIM; messages failing past the max-delivery bound park to the
 * dead-letter stream instead of blocking the group. Queue loss is degradable,
 * never silent: DLQ depth is the alert signal.
 */
@Component
class BeaconConsumer(
    private val redis: StringRedisTemplate,
    private val degrade: com.resume.analytics.domain.DegradeState,
    @org.springframework.beans.factory.annotation.Value("\${analytics.stream.consumers:worker-1}")
    private val consumers: List<String>,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val stream = "beacon:q"
    private val group = "rollup-group"
    private val consumer = "worker-1"
    private val dlq = "beacon:dlq"
    private val attemptsKey = "beacon:attempts"
    private val maxDeliveries = 10L
    private val idleReclaimMs = 60_000L

    private fun ops() = redis.opsForStream<String, String>()

    @Scheduled(fixedDelay = 5_000)
    fun poll() {
        ensureGroup()
        reclaimIdle()
        val records = try {
            ops().read(
                Consumer.from(group, consumer),
                StreamReadOptions.empty().count(100).block(Duration.ofSeconds(2)),
                StreamOffset.create(stream, ReadOffset.lastConsumed()),
            )
        } catch (ex: Exception) {
            log.warn("beacon poll degraded: {}", ex.message)
            return
        }
        val pending = records.orEmpty()
        for (consumer in consumers) {
            pending.forEach { record ->
                try {
                    aggregate(record.value)
                    ops().acknowledge(stream, group, record.id.value)
                    redis.opsForHash<String, String>().delete(attemptsKey, record.id.value)
                } catch (ex: Exception) {
                    parkIfPoison(record.id.value)
                }
            }
        }
    }

    private fun aggregate(fields: Map<String, String>) {
        if (degrade.shedRealtime.get()) return // shed: intake preserved, counters go honestly stale
        val username = fields["username"] ?: throw IllegalArgumentException("missing username")
        val minute = Instant.now().epochSecond / 60
        val key = "rt:$username:$minute"
        redis.opsForValue().increment(key)
        redis.expire(key, Duration.ofMinutes(65))
    }

    private fun parkIfPoison(recordId: String) {
        val attempts = redis.opsForHash<String, String>().increment(attemptsKey, recordId, 1) ?: 1
        redis.expire(attemptsKey, Duration.ofHours(1))
        if (attempts > maxDeliveries) {
            try {
                ops().add(MapRecord.create(dlq, mapOf("poison" to recordId)))
                ops().acknowledge(stream, group, recordId)
                log.warn("beacon message parked to DLQ: {}", recordId)
            } catch (ex: Exception) {
                log.warn("beacon DLQ park degraded: {}", ex.message)
            }
        }
    }

    private fun ensureGroup() {
        try {
            ops().createGroup(stream, group)
        } catch (_: Exception) {
            // Exists already, or no stream yet (producers create it on first beacon) — self-heals.
        }
    }

    private fun reclaimIdle() {
        try {
            @Suppress("UNCHECKED_CAST")
            val res = redis.execute { conn ->
                conn.execute(
                    "XAUTOCLAIM",
                    stream.toByteArray(), group.toByteArray(), consumer.toByteArray(),
                    idleReclaimMs.toString().toByteArray(), "0-0".toByteArray(),
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
                try {
                    aggregate(flat.chunked(2).associate { it[0] to it[1] })
                    ops().acknowledge(stream, group, id)
                } catch (ex: Exception) {
                    parkIfPoison(id)
                }
            }
        } catch (ex: Exception) {
            log.warn("beacon reclaim degraded: {}", ex.message)
        }
    }
}
