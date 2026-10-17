package com.resume.account.oauth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** BR-04 PKCE S256; verifiers stay server-side and single-use (state store owns lifetime). */
class PkceTest {

    /** RFC 7636 appendix B vector. */
    @Test
    fun `challenge matches the RFC 7636 S256 vector`() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun `challenge is unpadded base64url`() {
        val challenge = Pkce.challenge("verifier-123")
        assertFalse(challenge.contains('='))
        assertFalse(challenge.contains('+'))
        assertFalse(challenge.contains('/'))
    }

    @Test
    fun `verifiers are random and long enough`() {
        val a = Pkce.verifier()
        val b = Pkce.verifier()
        assertNotEquals(a, b)
        assertTrue(a.length >= 43, "43+ chars for >= 256 bits of entropy: ${a.length}")
        assertNotEquals(Pkce.challenge(a), Pkce.challenge(b))
    }
}
