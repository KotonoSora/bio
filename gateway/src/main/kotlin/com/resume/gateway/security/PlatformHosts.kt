package com.resume.gateway.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Platform-host scope (feature 04 D-4): the edge serves the SPA + API only on
 * these Hosts; every other Host is a custom-domain read that must pass the
 * resolve. One parser and one normalizer serve both the routing gate
 * (`HostRoutingService`) and the CSRF read exemptions
 * (`CsrfDoubleSubmitFilter`), so the two decision points can never drift apart.
 *
 * Config entries are trimmed/lowercased as given (`127.0.0.1`, bare `::1`);
 * request Host values are normalized with [normalize] first (port, brackets
 * and trailing root dot stripped). A missing Host counts as platform: legacy
 * routing applies and the CSRF cookie is issued.
 */
@Component
class PlatformHosts(
    @Value("\${edge.platform-hosts:localhost,127.0.0.1,::1}") raw: String,
) {
    private val hosts: Set<String> = raw.split(',')
        .map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

    /** True for a normalized platform host (or a missing Host). */
    fun isPlatform(normalized: String): Boolean = normalized.isEmpty() || normalized in hosts

    companion object {
        /** Host header normalization: lowercase, no port, no trailing dot (IPv6-aware). */
        fun normalize(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            val trimmed = raw.trim().lowercase()
            val host = if (trimmed.startsWith("[")) {
                trimmed.substringAfter('[').substringBefore(']')
            } else {
                trimmed.substringBefore(':')
            }
            return host.trimEnd('.')
        }
    }
}
