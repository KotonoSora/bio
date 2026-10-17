package com.resume.profile.security

import io.jsonwebtoken.Jwts
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID

/**
 * Mesh credential for outbound internal-API calls (baseline 08), mirroring
 * account-service's implementation. Signed with the platform internal key when
 * configured (`MESH_JWT_PRIVATE_KEY_PEM`, dual-key rotation on the verifier
 * side); in dev the key is blank and no header is sent - the receiving service's
 * empty-boot branch accepts the flow. Raw inbound client headers are never
 * forwarded as identity.
 */
@Service
class OutboundServiceJwt(
    @Value("\${mesh.jwt.private-key-pem:}") private val privateKeyPem: String,
    @Value("\${mesh.jwt.kid:\${INTERNAL_JWT_KID:e0-dev-key}}") private val kid: String,
) {
    private val privateKey: RSAPrivateKey? = if (privateKeyPem.isBlank()) {
        null
    } else {
        val clean = privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\n", "")
            .trim()
        KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(clean)))
            as RSAPrivateKey
    }

    fun authorization(audience: String): String? {
        val key = privateKey ?: return null
        val now = Instant.now()
        val token = Jwts.builder()
            .header().keyId(kid).and()
            .issuer("profile-service")
            .subject("svc:profile-service")
            .audience().add(audience).and()
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(60)))
            .claim("svc", true)
            .signWith(key)
            .compact()
        return "Bearer $token"
    }
}
