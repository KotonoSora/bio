package com.resume.account.security

import com.resume.account.domain.ApiException
import io.jsonwebtoken.Claims
import org.springframework.security.core.context.SecurityContextHolder

/**
 * Reads the verified identity claims. The gateway-signed JWT is the only
 * source; request bodies/params never carry `user_id` (BR-08). `auth_time` and
 * `sid` are additive claims emitted by the gateway from the edge key.
 */
object AuthClaims {

    fun claims(): Claims? =
        SecurityContextHolder.getContext().authentication?.details as? Claims

    fun subject(): String? =
        SecurityContextHolder.getContext().authentication?.name?.takeIf { it.isNotBlank() && it != "anonymous" }

    fun sid(): String? = claims()?.get("sid") as? String

    fun authTimeEpochSeconds(): Long? = (claims()?.get("auth_time") as? Number)?.toLong()

    /** Session-authenticated callers only; anything else is `401 unauthenticated`. */
    fun requireSession(): Pair<String, String> {
        val userId = subject() ?: throw ApiException.unauthenticated()
        val sid = sid() ?: throw ApiException.unauthenticated()
        return userId to sid
    }
}
