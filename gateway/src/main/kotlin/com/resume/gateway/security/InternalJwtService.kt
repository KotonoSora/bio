package com.resume.gateway.security

import io.jsonwebtoken.Jwts
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID

/**
 * Signs the internal identity JWT: RS256, aud=<service>, 60s TTL.
 * Dev/E0 auto-generates an ephemeral key when no PEM is configured.
 * Rotation uses a dual-key window (old verifies until expiry, new signs).
 */
@Service
class InternalJwtService(
    @Value("\${internal.jwt.kid:e0-dev-key}") val kid: String,
    @Value("\${internal.jwt.private-key-pem:}") private val privateKeyPem: String,
) {
    /** True when a real signing key is configured (prod path); dev auto-generates. */
    val configured: Boolean get() = privateKeyPem.isNotBlank()

    private val keyPair by lazy {
        if (privateKeyPem.isBlank()) {
            val gen = KeyPairGenerator.getInstance("RSA")
            gen.initialize(2048)
            gen.generateKeyPair()
        } else {
            val clean = privateKeyPem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "").replace("\n", "").trim()
            val spec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(clean))
            val priv = KeyFactory.getInstance("RSA").generatePrivate(spec)
            java.security.KeyPair(null, priv)
        }
    }

    fun issue(
        userId: String,
        audience: String,
        issuer: String = "login",
        ttlSeconds: Long = 60,
        extraClaims: Map<String, String> = emptyMap(),
    ): String {
        val now = Instant.now()
        val builder = Jwts.builder()
            .header().keyId(kid).and()
            .issuer(issuer)
            .subject(userId)
            .audience().add(audience).and()
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(ttlSeconds)))
        extraClaims.forEach { (k, v) -> builder.claim(k, v) }
        return builder.signWith(keyPair.private).compact()
    }

    fun publicJwk(): Map<String, String> {
        val pub = keyPair.public as? java.security.interfaces.RSAPublicKey
            ?: (keyPair.private as RSAPrivateKey).let {
                val kf = KeyFactory.getInstance("RSA")
                kf.generatePublic(java.security.spec.RSAPublicKeySpec(it.modulus, java.math.BigInteger.valueOf(65537))) as java.security.interfaces.RSAPublicKey
            }
        fun b64(n: java.math.BigInteger): String {
            val b = n.toByteArray().let { if (it[0] == 0.toByte()) it.drop(1).toByteArray() else it }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(b)
        }
        return mapOf("kty" to "RSA", "kid" to kid, "use" to "sig", "alg" to "RS256", "n" to b64(pub.modulus), "e" to b64(pub.publicExponent))
    }
}
