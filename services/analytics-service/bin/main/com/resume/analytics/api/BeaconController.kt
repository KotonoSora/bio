package com.resume.analytics.api

import jakarta.validation.constraints.NotBlank
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

data class BeaconRequest(@field:NotBlank val visitorKey: String, val referrer: String? = null)

/**
 * Anonymous write surface (feature 12): uniform 202, dedupe window, silent drop, no oracle.
 * Intake never does per-hit PG writes; a stream consumer rolls up hourly.
 */
@RestController
class BeaconController(private val redis: StringRedisTemplate) {
    @PostMapping("/api/t/{username}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun beacon(@PathVariable username: String, @RequestBody req: BeaconRequest): Map<String, Boolean> {
        val seenKey = "seen:${username.lowercase()}:${req.visitorKey.hashCode()}"
        val first = redis.opsForValue().setIfAbsent(seenKey, "1", java.time.Duration.ofMinutes(10)) ?: true
        if (first) {
            redis.opsForStream<String, String>().add("beacon:q", mapOf("username" to username.lowercase(), "ts" to java.time.Instant.now().toString()))
        }
        return mapOf("accepted" to true)
    }
}
