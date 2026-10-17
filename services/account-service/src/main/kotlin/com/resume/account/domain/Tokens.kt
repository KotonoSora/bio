package com.resume.account.domain

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Opaque id/token generation + constant-time comparison. No secret is ever logged. */
object Tokens {
    private val rng = SecureRandom()
    private val url = Base64.getUrlEncoder().withoutPadding()

    fun random(bytes: Int = 32): String = ByteArray(bytes).also(rng::nextBytes).let(url::encodeToString)

    fun newId(prefix: String): String = "${prefix}_${random(12)}"

    /** Session id: opaque, >= 128 bit (TL 02). */
    fun newSid(): String = random(32)

    fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
}
