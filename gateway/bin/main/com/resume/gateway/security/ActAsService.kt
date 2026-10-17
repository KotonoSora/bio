package com.resume.gateway.security

import org.springframework.stereotype.Service

/**
 * Act-as-user identity class (feature 45 mechanism). Act-as tokens carry a
 * DISTINCT issuer (act-as) from login sessions (login) plus
 * {act_as:true, actor, org, profile} claims with a short TTL. The gateway
 * passes them as a second identity class; services enforce a per-endpoint
 * allowlist and deny account-level classes by default. Validators key on the
 * issuer and never confuse the classes.
 */
@Service
class ActAsService(private val jwt: InternalJwtService) {

    fun mint(actor: String, org: String, profile: String, audience: String, ttlSeconds: Long = 300): String =
        jwt.issue(
            userId = actor,
            audience = audience,
            issuer = "act-as",
            ttlSeconds = ttlSeconds,
            extraClaims = mapOf("act_as" to "true", "actor" to actor, "org" to org, "profile" to profile),
        )
}
