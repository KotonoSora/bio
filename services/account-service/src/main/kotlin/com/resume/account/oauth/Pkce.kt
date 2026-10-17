package com.resume.account.oauth

import com.resume.account.domain.Tokens
import java.security.MessageDigest
import java.util.Base64

/** PKCE S256 (BR-04): verifier stays server-side; only the challenge leaves. */
object Pkce {
    fun verifier(): String = Tokens.random(32)

    fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}
