package com.resume.link.api

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import javax.sql.DataSource

/**
 * Readiness fails closed (503) on unreachable own database. Queue/cache
 * degradation never fails readiness: those paths degrade honestly instead
 * (zeros/partials, DLQ depth as the alert signal).
 */
@RestController
class ReadinessController(private val db: DataSource) {
    @GetMapping("/health/readiness")
    fun readiness(): ResponseEntity<HealthStatus> {
        return try {
            db.connection.use { c -> c.createStatement().use { it.executeQuery("SELECT 1") } }
            ResponseEntity.ok(HealthStatus("UP", "link-service", Instant.now()))
        } catch (ex: Exception) {
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(HealthStatus("DOWN", "link-service", Instant.now()))
        }
    }
}
