package com.resume.profile.domain.domains

import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

data class ChallengeRound(val token: String, val tokenHash: String, val issuedAt: Instant, val expiresAt: Instant)

/**
 * TXT challenge mechanics (feature 04 BR-01, S-2): a random 32-byte token in
 * `_rb-challenge.<host>` (`rb-verify=<token>`); shown once to the owner,
 * STORED HASHED (sha-256), single-use per attempt round, 7-day window.
 * Regeneration issues a fresh round; the old token dies. Tokens never appear
 * in logs or subsequent responses (S-2) - only this call site reveals one.
 */
@Service
class ChallengeService {

    /** Issues a fresh round; returns the one-time token (never persisted, never logged). */
    fun issue(now: Instant): ChallengeRound {
        val bytes = ByteArray(32).also(rng::nextBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return ChallengeRound(
            token = token,
            tokenHash = hash(token),
            issuedAt = now,
            expiresAt = now.plusSeconds(CUSTOM_TTL),
        )
    }

    fun hash(token: String): String = hashBytes(token.toByteArray(Charsets.US_ASCII)).joinToString("") { "%02x".format(it) }

    /** TXT record check: any `rb-verify=<token>` TXT must hash to the stored round hash. */
    fun matches(records: List<String>, storedHash: String): Boolean =
        records.any { record ->
            val candidate = record.trim()
                .removePrefix("$CHALLENGE_ATTR=")
                .takeIf { record.trim().startsWith("$CHALLENGE_ATTR=") }
                ?: return@any false
            MessageDigest.isEqual(hash(candidate).toByteArray(), storedHash.toByteArray())
        }

    companion object {
        const val CHALLENGE_ATTR = Hostname.CHALLENGE_ATTR
        const val CUSTOM_TTL = CustomDomain.ROUND_TTL_SECONDS
        private val rng = SecureRandom()
    }

    private fun hashBytes(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
}
