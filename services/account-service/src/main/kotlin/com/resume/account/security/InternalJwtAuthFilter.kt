package com.resume.account.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.micrometer.core.instrument.MeterRegistry
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.OncePerRequestFilter
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Verifies the gateway-signed internal identity JWT on every request:
 * RS256, aud=account-service, 60 s TTL, dual-key rotation window. `sub=anonymous`
 * stays unauthenticated (public initiations/callbacks); identity classes are
 * issuer-keyed (`login` vs `act-as`, feature 45) and never confused. Fail-closed
 * on any failure; denials are counted and RFC 9457-shaped.
 */
class InternalJwtAuthFilter(
    private val expectedAudience: String,
    private val publicKeyPem: String,
    private val previousPublicKeyPem: String,
    private val actAsPolicy: ActAsPolicy,
    meters: MeterRegistry,
    private val publicPaths: Array<String> = emptyArray(),
) : OncePerRequestFilter() {

    private val denied = meters.counter("security.auth.denied", "service", "account-service")
    private val publicMatcher = AntPathMatcher()

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val p = request.requestURI
        return p.startsWith("/health") || p.startsWith("/actuator/health") ||
            p.startsWith("/v3/api-docs") || p.startsWith("/swagger-ui") || p == "/.well-known/jwks.json"
    }

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        // Dev/E0 only: ephemeral dev-user identity so empty-boot compose smoke works end to
        // end. Production configures the platform public key and this branch is unreachable.
        if (publicKeyPem.isBlank()) {
            authenticate("dev-user", listOf(SimpleGrantedAuthority("ROLE_DEV")), null)
            try {
                chain.doFilter(request, response)
            } finally {
                SecurityContextHolder.clearContext()
            }
            return
        }
        val header = request.getHeader("Authorization")
        if (header == null) {
            // Public SSO surfaces answer for themselves (fallback 404 auth_disabled,
            // session self-checks); identity stays required everywhere else.
            if (publicPaths.any { publicMatcher.match(it, request.requestURI) }) {
                try {
                    chain.doFilter(request, response)
                } finally {
                    SecurityContextHolder.clearContext()
                }
                return
            }
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
            if (isActAs && claims["act_as"]?.toString() != "true") continue
            if (isActAs && !actAsPolicy.allowed(request.requestURI)) {
                deny(response, 403, "act_as_denied", "act-as not allowed here")
                return
            }
            val subject = claims.subject
            if (subject.isNullOrBlank() || subject == "anonymous") {
                // Public initiations and callbacks: verified token, no identity.
                try {
                    chain.doFilter(request, response)
                } finally {
                    SecurityContextHolder.clearContext()
                }
                return
            }
            val roles = if (isActAs) {
                listOf(SimpleGrantedAuthority("ROLE_ACT_AS"))
            } else {
                listOf(SimpleGrantedAuthority("ROLE_SERVICE"))
            }
            authenticate(subject, roles, claims)
            try {
                chain.doFilter(request, response)
            } finally {
                SecurityContextHolder.clearContext()
            }
            return
        }
        deny(response, 401, "invalid_identity", "invalid internal identity")
    }

    private fun authenticate(subject: String, roles: List<SimpleGrantedAuthority>, claims: Claims?) {
        val auth = UsernamePasswordAuthenticationToken(subject, null, roles)
        auth.details = claims
        SecurityContextHolder.getContext().authentication = auth
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
