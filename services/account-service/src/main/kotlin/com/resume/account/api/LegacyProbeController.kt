package com.resume.account.api

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * SSO-only is a hard rule (S-1): no credential surface exists anywhere. Any
 * unmatched route under the `/api/auth` or legacy `/api/v1/auth` surfaces -
 * known probe shapes included - resolves to `404 auth_disabled`; the probe is
 * counted. Specific mappings always win over these wildcards, so real flows
 * are unaffected.
 */
@RestController
class LegacyProbeController(meters: MeterRegistry) {

    private val probes = meters.counter("auth.probe_blocked")

    @RequestMapping("/api/auth/**", "/api/v1/auth/**")
    fun probe(): ResponseEntity<Map<String, Any>> {
        probes.increment()
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            mapOf(
                "type" to "about:blank",
                "title" to "not_found",
                "status" to 404,
                "code" to "auth_disabled",
                "detail" to "Page not found",
            ),
        )
    }
}
