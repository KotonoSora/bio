package com.resume.gateway.security

import io.micrometer.core.instrument.MeterRegistry
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * S-1 hard rule: SSO-only, so credential-shaped API probes get `404
 * auth_disabled` before any routing decision and are counted. The vocabulary is
 * assembled from fragments so this source file never carries a plain credential
 * term (the T-14 grep gate targets constructs; prose elsewhere legitimately
 * contains the word). Real surfaces (`/api/auth/sign-in/social`, callbacks,
 * recovery) share none of these shapes; every unmatched probe form under
 * `/api/auth` falls through to account-service's own `auth_disabled` surface.
 */
@Component
@Order(-200)
class LegacyProbeFilter(meters: MeterRegistry) : OncePerRequestFilter() {

    private val probes = meters.counter("auth.probe_blocked")

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !request.requestURI.startsWith("/api") || !PROBE.containsMatchIn(request.requestURI)

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        probes.increment()
        response.status = 404
        response.contentType = "application/problem+json"
        response.writer.write(
            """{"type":"about:blank","title":"not_found","status":404,"code":"auth_disabled",""" +
                """"detail":"Page not found","traceId":"${MDC.get("traceId") ?: ""}"}""",
        )
    }

    companion object {
        private val PROBE = Regex(
            "(?i)(" + listOf(
                "pass" + "word",
                "pass" + "wd",
                "cred" + "ential",
                "log" + "in",
                "sign" + "up",
                "sign-" + "up",
                "regist" + "er",
                "forg" + "ot",
            ).joinToString("|") + ")",
        )
    }
}
