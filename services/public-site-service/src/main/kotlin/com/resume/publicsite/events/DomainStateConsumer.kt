package com.resume.publicsite.events

import com.resume.publicsite.api.PreRenderService
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
 * `domain.state_changed.v1` consumer (feature 04 T-05): purge `ssr:{host}:*`
 * render keys the moment a binding moves state (verified/suspended/unverified/
 * removed). At-least-once, replay-safe (purge is idempotent); an immediate
 * purge via this event is the primary path (TL 01), the 60 s host-key TTL is
 * the backstop. Consumer-group pattern matches the platform stream convention;
 * the stream is read through the domain-events template (the producer's
 * logical db - see `RedisConfig`).
 */
@Component
class DomainStateConsumer(
    @Qualifier("domainEventsRedisTemplate") private val redis: StringRedisTemplate,
    private val preRender: PreRenderService,
    @Value("\${integration.profile-events.consumer:public-site-1}") private val consumerName: String,
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
                fields["attr.hostname"]?.takeIf { it.isNotBlank() }?.let { preRender.purgeHost(it) }
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
        const val GROUP = "public-site-domain-events"
        const val STATE_CHANGED = "domain.state_changed.v1"
    }
}
