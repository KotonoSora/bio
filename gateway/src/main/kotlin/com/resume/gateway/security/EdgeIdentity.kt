package com.resume.gateway.security

/**
 * Resolved session identity for the current request (feature 01 D-2/D-6):
 * sourced from the `edge:session:{sid}` key (Redis db 2) or the introspect
 * rebuild. Never a grant on its own - the registry is truth; this is the edge
 * cache view that feeds the internal JWT claims (`sub`, `sid`, `auth_time`).
 */
data class EdgeIdentity(
    val userId: String,
    val sid: String,
    val authTimeEpochSeconds: Long,
    val absoluteExpiresEpochSeconds: Long,
)
