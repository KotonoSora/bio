package com.resume.account.domain

import com.fasterxml.jackson.databind.ObjectMapper
import com.resume.account.config.AccountProperties
import com.resume.account.data.UserRepository
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class RelinkInvitation(val userId: String, val evidenceRef: String)

/**
 * Lockout recovery (BR-12, S-9): ops-triggered, signed, single-use re-link
 * invitation. 15 min TTL in Redis db 7 (`acct:relink:{token}`), HMAC-suffixed
 * token so a forged value never passes landing validation. No credential is
 * ever set, displayed, or reset - the user completes a normal SSO dance that
 * only inserts a link row. Every invitation is audited with the evidence
 * reference; replay fails closed.
 */
@Service
class RecoveryService(
    private val users: UserRepository,
    private val audit: AuditService,
    private val redis: StringRedisTemplate,
    private val mapper: ObjectMapper,
    private val props: AccountProperties,
    meters: MeterRegistry,
) {
    private val invocations = meters.counter("recovery.invocation")
    private val rejected = meters.counter("recovery.rejected")

    /** Dev/fallback key keeps tokens stable within a dev run; prod sets RECOVERY_SIGNING_KEY. */
    private val signingKey: ByteArray
        get() = (props.auth.recoverySigningKey.ifBlank { "resume-builder-dev-recovery" }).toByteArray()

    @Transactional(readOnly = true)
    fun create(userId: String, evidenceRef: String, ipClass: String?): Pair<String, Instant> {
        val evidence = evidenceRef.trim()
        if (evidence.isEmpty()) {
            throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "evidence_required", "Evidence reference is required.")
        }
        if (users.findById(userId).isEmpty) {
            throw ApiException.notFound("user_absent", "No such account.")
        }
        val nonce = Tokens.random(24)
        val token = "$nonce.${hmac(nonce)}"
        val ttl = Duration.ofSeconds(props.auth.recoveryTtlSeconds)
        val payload = mapper.writeValueAsString(RelinkInvitation(userId, evidence.take(200)))
        redis.opsForValue().set("acct:relink:$token", payload, ttl)
        audit.record(userId = userId, action = "recovery", target = evidence.take(200), ipClass = ipClass)
        invocations.increment()
        return token to Instant.now().plus(ttl)
    }

    fun inspect(token: String): RelinkInvitation? {
        if (!signatureValid(token)) {
            rejected.increment()
            return null
        }
        val raw = redis.opsForValue().get("acct:relink:$token") ?: return null
        return runCatching { mapper.readValue(raw, RelinkInvitation::class.java) }.getOrNull()
    }

    /** Single-use consume (GETDEL); the caller links the provider only on success. */
    fun consume(token: String): RelinkInvitation? {
        if (!signatureValid(token)) {
            rejected.increment()
            return null
        }
        val raw = redis.opsForValue().getAndDelete("acct:relink:$token") ?: run {
            rejected.increment()
            return null
        }
        return runCatching { mapper.readValue(raw, RelinkInvitation::class.java) }.getOrNull()
    }

    private fun signatureValid(token: String): Boolean {
        val parts = token.split(".")
        if (parts.size != 2) return false
        return Tokens.constantTimeEquals(parts[1], hmac(parts[0]))
    }

    private fun hmac(nonce: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(signingKey, "HmacSHA256"))
        return java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mac.doFinal(nonce.toByteArray()))
    }
}
