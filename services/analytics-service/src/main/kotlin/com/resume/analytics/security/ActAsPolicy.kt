package com.resume.analytics.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Per-endpoint-class allowlist for the act-as identity class. Account-level
 * classes (password/email/plan/delete/billing/roster) are denied by default;
 * concierge surfaces opt in by path prefix. Validators never confuse act-as
 * with login sessions (issuer-keyed).
 */
@Component
class ActAsPolicy(
    @Value("\${act-as.allowed-prefixes:}")
    allowed: String,
) {
    private val prefixes: List<String> = allowed.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun allowed(uri: String): Boolean = prefixes.any { uri.startsWith(it) }
}
