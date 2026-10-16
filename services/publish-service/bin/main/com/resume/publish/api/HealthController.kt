package com.resume.publish.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class HealthStatus(val status: String, val service: String, val time: Instant)

/**
 * E0 empty-boot health endpoints. Wired to compose healthchecks and
 * actuator /health/liveness + /health/readiness (see application.yml).
 */
@RestController
class HealthController {
    private val serviceName = "publish-service"

    @GetMapping("/health/liveness")
    fun liveness(): ResponseEntity<HealthStatus> =
        ResponseEntity.ok(HealthStatus("UP", serviceName, Instant.now()))
}
