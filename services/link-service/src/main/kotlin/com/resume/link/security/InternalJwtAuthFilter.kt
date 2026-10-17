package com.resume.link.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.micrometer.core.instrument.MeterRegistry
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Verifies the gateway-signed internal identity JWT on every request:
 * RS256, aud=<service>, 60s TTL, dual-key rotation window. Identity classes
 * are issuer-keyed: `login` sessions vs `act-as` tokens (feature 45), never
 * confused; act-as principals pass only the per-endpoint allowlist, and
 * account-level classes are denied by default. Fail-closed on any failure;
 * every denial is counted (security.auth.denied) and RFC 9457-shaped.
 *
 * Ownership scoping is server-side downstream: handlers derive user_id from
 * the JWT claims, never from request bodies. Raw client headers are never trusted.
 */
class InternalJwtAuthFilter(
    private val expectedAudience: String,
    private val publicKeyPem: String,
    private val previousPublicKeyPem: String,
    private val actAsPolicy: ActAsPolicy,
    meters: MeterRegistry,
) : OncePerRequestFilter() {

    private val denied = meters.counter("security.auth.denied", "service", "link-service")

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val p = request.requestURI
        return p.startsWith("/health") || p.startsWith("/actuator/health") ||
            p.startsWith("/v3/api-docs") || p.startsWith("/swagger-ui") ||
            p == "/.well-known/jwks.json"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        // Dev/E0 only: ephemeral dev-user identity so empty-boot compose smoke works
        // end to end (RLS holder, server-side scoping, and controller fallbacks agree).
        // Production configures the JWKS-backed key and this branch is unreachable.
        if (publicKeyPem.isBlank()) {
            val dev = UsernamePasswordAuthenticationToken("dev-user", null, listOf(SimpleGrantedAuthority("ROLE_DEV")))
            SecurityContextHolder.getContext().authentication = dev
            try {
                chain.doFilter(request, response)
            } finally {
                SecurityContextHolder.clearContext()
            }
            return
        }
        val header = request.getHeader("Authorization") ?: run {
            deny(response, 401, "missing_identity", "missing internal identity")
            return
        }
        val token = header.removePrefix("Bearer ").trim()
        for (key in publicKeys()) {
            val claims = try {
                Jwts.parser()
                    .verifyWith(key)
                    .requireAudience(expectedAudience)
                    .build()
                    .parseSignedClaims(token)
                    .payload
            } catch (_: Exception) {
                continue // try the next key in the rotation window
            }
            val isActAs = claims.issuer == "act-as"
            if (isActAs && claims["act_as"]?.toString() != "true") {
                continue // malformed second class: keep trying keys, then deny
            }
            if (isActAs && !actAsPolicy.allowed(request.requestURI)) {
                deny(response, 403, "act_as_denied", "act-as not allowed here")
                return
            }
            val userId = claims.subject ?: claims["user_id"]?.toString() ?: "unknown"
            val roles = if (isActAs) {
                listOf(SimpleGrantedAuthority("ROLE_ACT_AS"))
            } else {
                listOf(SimpleGrantedAuthority("ROLE_SERVICE"))
            }
            val auth = UsernamePasswordAuthenticationToken(userId, null, roles)
            auth.details = claims
            SecurityContextHolder.getContext().authentication = auth
            chain.doFilter(request, response)
            return
        }
        deny(response, 401, "invalid_identity", "invalid internal identity")
    }

    private fun deny(response: HttpServletResponse, status: Int, code: String, detail: String) {
        denied.increment()
        SecurityContextHolder.clearContext()
        response.status = status
        response.contentType = "application/problem+json"
        response.writer.write(
            """{"type":"about:blank","title":"denied","status":$status,"detail":"$detail","code":"$code"}""",
        )
    }

    private fun publicKeys(): List<PublicKey> =
        listOf(publicKeyPem, previousPublicKeyPem).filter { it.isNotBlank() }.map { pem ->
            val clean = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\n", "")
                .trim()
            val bytes = Base64.getDecoder().decode(clean)
            KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(bytes))
        }
}
