package com.resume.account.domain

import com.resume.account.data.UserRepository
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant

/**
 * Contact email: user-set, code-verified (BR-03). 6 digits, 15 min TTL,
 * 5 attempts per code, resend allowed after 60 s, send cap 5/hour/user
 * (NFR-06, S envelope). An email already verified on another account is
 * refused `409 email_conflict`; the partial unique index is the backstop.
 * Codes and addresses never appear in logs.
 */
@Service
class ContactEmailService(
    private val users: UserRepository,
    private val audit: AuditService,
    private val redis: StringRedisTemplate,
    private val mailer: Mailer,
    meters: MeterRegistry,
) {
    private val rng = SecureRandom()
    private val sent = meters.counter("contact_email.sent")
    private val verified = meters.counter("contact_email.verified")
    private val invalid = meters.counter("contact_email.invalid")

    private companion object {
        const val CODE_TTL_SECONDS = 900L
        const val RESEND_GATE_SECONDS = 60L
        const val HOURLY_CAP = 5L
        const val MAX_ATTEMPTS = 5
    }

    @Transactional(readOnly = true)
    fun send(userId: String, email: String, ipClass: String?) {
        val normalized = email.trim().lowercase()
        if (users.countVerifiedContactOutside(userId, normalized) > 0) {
            throw ApiException(HttpStatus.CONFLICT, "email_conflict", "This email can't be used here. Choose another.")
        }
        val resendKey = "acct:cemail:resend:$userId"
        if (redis.hasKey(resendKey)) {
            val ttl = redis.getExpire(resendKey, java.util.concurrent.TimeUnit.SECONDS)
            throw ApiException.throttled(ttl.coerceAtLeast(1))
        }
        val hourKey = "acct:cemail:hour:$userId"
        val hourCount = redis.opsForValue().increment(hourKey) ?: 1L
        if (hourCount == 1L) redis.expire(hourKey, Duration.ofHours(1))
        if (hourCount > HOURLY_CAP) {
            val ttl = redis.getExpire(hourKey, java.util.concurrent.TimeUnit.SECONDS)
            throw ApiException.throttled(ttl.coerceAtLeast(1))
        }

        val code = "%06d".format(rng.nextInt(1_000_000))
        val key = "acct:cemail:$userId"
        redis.opsForHash<String, String>().putAll(
            key,
            mapOf("email" to normalized, "codeHash" to Tokens.sha256Hex(code), "attempts" to "0"),
        )
        redis.expire(key, Duration.ofSeconds(CODE_TTL_SECONDS))
        redis.opsForValue().set(resendKey, "1", Duration.ofSeconds(RESEND_GATE_SECONDS))
        mailer.sendContactCode(normalized, code)
        sent.increment()
        audit.record(userId = userId, action = "contact_email", target = "code_sent", ipClass = ipClass)
    }

    @Transactional
    fun verify(userId: String, code: String, ipClass: String?): Instant {
        val key = "acct:cemail:$userId"
        val stored = redis.opsForHash<String, String>().entries(key)
        if (stored.isEmpty()) throw ApiException.invalidCode()
        val attempts = stored["attempts"]?.toIntOrNull() ?: 0
        if (attempts >= MAX_ATTEMPTS) throw ApiException.invalidCode()
        val matches = Tokens.constantTimeEquals(Tokens.sha256Hex(code), stored["codeHash"] ?: "")
        if (!matches) {
            invalid.increment()
            redis.opsForHash<String, String>().put(key, "attempts", (attempts + 1).toString())
            throw ApiException.invalidCode()
        }
        val email = stored["email"] ?: throw ApiException.invalidCode()
        if (users.countVerifiedContactOutside(userId, email) > 0) {
            throw ApiException(HttpStatus.CONFLICT, "email_conflict", "This email can't be used here. Choose another.")
        }
        val user = users.findById(userId).orElseThrow { ApiException.unauthenticated() }
        user.contactEmail = email
        user.contactVerifiedAt = Instant.now()
        user.updatedAt = Instant.now()
        try {
            users.saveAndFlush(user)
        } catch (ex: DataIntegrityViolationException) {
            // Partial unique index races with a concurrent verification elsewhere.
            throw ApiException(HttpStatus.CONFLICT, "email_conflict", "This email can't be used here. Choose another.")
        }
        redis.delete(key)
        verified.increment()
        audit.record(userId = userId, action = "contact_email", target = "verified", ipClass = ipClass)
        return user.contactVerifiedAt!!
    }
}
