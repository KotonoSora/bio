package com.resume.profile.domain.domains

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * BR-01/S-2 challenge mechanics: a 32-byte url-safe token, stored hashed,
 * matched only through an `rb-verify=<token>` TXT record, dead the moment the
 * round is regenerated.
 */
class ChallengeServiceTest {

    private val service = ChallengeService()
    private val now = Instant.parse("2026-10-18T00:00:00Z")

    @Test
    fun `issued token is url-safe and the record hash-matches`() {
        val round = service.issue(now)
        assertTrue(round.token.matches(Regex("^[A-Za-z0-9_-]{43}$")), round.token)
        assertEquals(now, round.issuedAt)
        assertEquals(now.plusSeconds(CustomDomain.ROUND_TTL_SECONDS), round.expiresAt)
        assertTrue(service.matches(listOf("rb-verify=${round.token}"), round.tokenHash))
    }

    @Test
    fun `unrelated TXT records never verify`() {
        val round = service.issue(now)
        val records = listOf(
            "v=spf1 include:_spf.example.com ~all",
            "google-site-verification=abc",
            "rb-verify=",
            "rb-verify-not-quite=${round.token}",
        )
        assertFalse(service.matches(records, round.tokenHash))
    }

    @Test
    fun `a record carrying a foreign token never verifies`() {
        val stored = service.issue(now)
        val foreign = service.issue(now)
        assertFalse(service.matches(listOf("rb-verify=${foreign.token}"), stored.tokenHash))
    }

    @Test
    fun `a regenerated round kills the previous token`() {
        val old = service.issue(now)
        val fresh = service.issue(now)
        assertNotEquals(old.token, fresh.token)
        assertFalse(service.matches(listOf("rb-verify=${old.token}"), fresh.tokenHash))
        assertTrue(service.matches(listOf("rb-verify=${fresh.token}"), fresh.tokenHash))
    }

    @Test
    fun `hashing is deterministic and never the token itself`() {
        val round = service.issue(now)
        assertEquals(round.tokenHash, service.hash(round.token))
        assertNotEquals(round.token, round.tokenHash)
        assertEquals(64, round.tokenHash.length) // sha-256 hex
    }
}
