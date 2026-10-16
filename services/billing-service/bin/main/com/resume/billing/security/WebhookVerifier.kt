package com.resume.billing.security

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Webhook safety (baseline 08; rails detail in feature 40): signature
 * verification per provider plus replay-window rejection. The scheme is
 * generic HMAC-SHA256 over `<timestamp>.<rawBody>` with `X-Signature:
 * sha256=<hex>` and `X-Timestamp` (unix seconds); rail specifics (IP ranges,
 * scheme variants) tighten this per provider without loosening it.
 * Env-gated: unconfigured = logged-but-open (dev); configured = fail-closed.
 */
@Component
@ConfigurationProperties("webhook")
class WebhookProps {
    var secrets: Map<String, String> = emptyMap()
    var timestampSkewSeconds: Long = 300
    var ipAllowlist: List<String> = emptyList()
}

@Service
class WebhookVerifier(private val props: WebhookProps) {

    private val log = LoggerFactory.getLogger(javaClass)
    val enforced: Boolean get() = props.secrets.isNotEmpty()

    fun verify(provider: String, signature: String?, timestamp: String?, rawBody: String, remoteIp: String?): Boolean {
        if (!enforced) {
            log.warn("webhook verification not configured: allowing (dev only)")
            return true
        }
        if (props.ipAllowlist.isNotEmpty() && remoteIp !in props.ipAllowlist) return false
        val secret = props.secrets[provider.lowercase()] ?: return false
        val ts = timestamp?.toLongOrNull() ?: return false
        if (kotlin.math.abs(Instant.now().epochSecond - ts) > props.timestampSkewSeconds) return false
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        val expected = mac.doFinal("$ts.$rawBody".toByteArray())
        val given = try {
            signature?.removePrefix("sha256=")?.chunked(2)?.map { it.toInt(16).toByte() }?.toByteArray()
                ?: return false
        } catch (_: Exception) {
            return false
        }
        return MessageDigest.isEqual(expected, given)
    }
}
