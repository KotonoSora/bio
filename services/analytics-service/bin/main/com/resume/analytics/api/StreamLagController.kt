package com.resume.analytics.api

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Queue-lag observability (07-scale-out.md): stream length, pending, and DLQ
 * depth. Sustained queue lag > 60 s on any stream is the scale trigger; DLQ
 * depth is the alert signal. The load gate asserts post-run drain to zero.
 */
@RestController
@RequestMapping("/internal/v1/stream-lag")
class StreamLagController(private val redis: StringRedisTemplate) {

    @GetMapping
    fun lag(): Map<String, Any> {
        val ops = redis.opsForStream<String, String>()
        val len = ops.size("beacon:q") ?: 0
        val pending = try {
            ops.pending("beacon:q", "rollup-group")?.totalPendingMessages ?: 0
        } catch (_: Exception) {
            0L
        }
        val dlq = ops.size("beacon:dlq") ?: 0
        return mapOf("streamLen" to len, "pending" to pending, "dlq" to dlq)
    }
}
