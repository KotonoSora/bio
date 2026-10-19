package com.resume.gateway.events

import com.resume.gateway.proxy.HostRoutingService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
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
 * `domain.state_changed.v1` consumer (feature 04 D-4): invalidation of the
 * `gw:domain:{host}` serve-cache on state changes, so an explicit removal
 * halts serving at the delete commit (NFR-04; TL 01: an immediate purge via
 * this event is the primary path, the 30 s/60 s decision TTLs are the
 * backstop). At-least-once and replay-safe (invalidation is idempotent).
 * Consumer-group pattern matches the platform stream convention; the stream is
 * read through the domain-events template (the producer's logical db - see
 * `RedisConfig`), never the service's own key space.
 */
@Component
class DomainStateConsumer(
    @Qualifier("domainEventsRedisTemplate") private val redis: StringRedisTemplate,
    private val hostRouting: HostRoutingService,
    @Value("\${edge.domain-events.consumer:gateway-edge-1}") private val consumerName: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun ops() = redis.opsForStream<String, String>()

    @Scheduled(fixedDelay = 5_000, initialDelay = 15_000)
    fun poll() {
        ensureGroup()
        val records = try {
            ops().read(
                Consumer.from(GROUP, consumerName),
                StreamReadOptions.empty().count(100).block(Duration.ofSeconds(2)),
                StreamOffset.create(STREAM, ReadOffset.lastConsumed()),
            )
        } catch (ex: Exception) {
            log.warn("domain-events poll degraded: {}", ex.message)
            return
        }
        records.orEmpty().forEach { handle(it) }
    }

    private fun handle(record: MapRecord<String, String, String>) {
        val fields = record.value
        try {
            if (fields["type"] == STATE_CHANGED) {
                fields["attr.hostname"]?.takeIf { it.isNotBlank() }?.let(hostRouting::invalidate)
            }
            ops().acknowledge(STREAM, GROUP, record.id.value)
        } catch (ex: Exception) {
            log.warn("domain-events handling degraded id={}: {}", record.id.value, ex.message)
        }
    }

    private fun ensureGroup() {
        try {
            ops().createGroup(STREAM, GROUP)
        } catch (_: Exception) {
            // Exists, or the stream does not yet (the producer creates it) - self-heals.
        }
    }

    private companion object {
        const val STREAM = "events:profile"
        const val GROUP = "gateway-domain-events"
        const val STATE_CHANGED = "domain.state_changed.v1"
    }
}
