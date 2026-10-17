package com.resume.gateway.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Edge CSRF double-submit (baseline 08; S-6): mutations must present the
 * `csrf_token` cookie value in the `X-CSRF-Token` header (fetch/JSON ops) or as
 * a form field (navigation posts that hand off to the IdP). Runs before the
 * security chain so the token cookie is issued on every response, including
 * 401s. Anonymous surfaces that never carry a session (SSO initiation,
 * callbacks, recovery, webhooks, beacon, merch callbacks) are exempt from
 * validation - downstream verifies by signature/Turnstile/state.
 *
 * The form check needs the body, and ServiceProxy needs the same bytes again:
 * non-exempt mutations are wrapped in `CachedBodyRequest` exactly once.
 */
@Component
@Order(-150)
class CsrfDoubleSubmitFilter(
    @Value("\${edge.csrf.cookie-name:csrf_token}") private val cookieName: String,
    @Value("\${edge.csrf.cookie-secure:true}") private val secure: Boolean,
) : OncePerRequestFilter() {

    private val rng = SecureRandom()

    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        ensureCookie(req, res)
        if (req.method in SAFE || exempt(req.requestURI)) {
            chain.doFilter(req, res)
            return
        }
        var downstream: HttpServletRequest = req
        val header = req.getHeader(HEADER)?.takeIf { it.isNotBlank() }
        val presented = if (header != null) {
            header
        } else {
            val cached = CachedBodyRequest(req)
            downstream = cached
            if (req.contentType?.startsWith(FORM, ignoreCase = true) == true) formField(cached.body) else null
        }
        val cookie = req.cookies?.firstOrNull { it.name == cookieName }?.value?.takeIf { it.isNotBlank() }
        if (cookie == null || presented == null ||
            !MessageDigest.isEqual(cookie.toByteArray(Charsets.UTF_8), presented.toByteArray(Charsets.UTF_8))
        ) {
            problem(res)
            return
        }
        chain.doFilter(downstream, res)
    }

    /** Session-scoped token cookie, readable by the SPA (never the session itself). */
    private fun ensureCookie(req: HttpServletRequest, res: HttpServletResponse) {
        if (req.cookies?.any { it.name == cookieName } == true) return
        val token = ByteArray(32).also(rng::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        res.addHeader(
            HttpHeaders.SET_COOKIE,
            ResponseCookie.from(cookieName, token)
                .path("/")
                .secure(secure)
                .sameSite("Lax")
                .httpOnly(false)
                .build()
                .toString(),
        )
    }

    private fun formField(body: ByteArray): String? {
        for (pair in String(body, Charsets.UTF_8).split('&')) {
            val eq = pair.indexOf('=')
            if (eq <= 0) continue
            val name = decode(pair.substring(0, eq))
            if (name == "_csrf" || name == "csrf_token") return decode(pair.substring(eq + 1))
        }
        return null
    }

    private fun decode(value: String): String = try {
        URLDecoder.decode(value, Charsets.UTF_8)
    } catch (_: Exception) {
        value
    }

    private fun exempt(path: String): Boolean =
        path.startsWith("/api/auth/sign-in/") || path.startsWith("/api/auth/callback/") ||
            path.startsWith("/api/auth/recovery/") || path.startsWith("/api/t/") ||
            path.startsWith("/api/merch/") || path.startsWith("/webhooks/") ||
            (path.startsWith("/api/v1/social/") && path.endsWith("/callback")) ||
            path.startsWith("/health") || path.startsWith("/actuator") || path == "/.well-known/jwks.json"

    private fun problem(res: HttpServletResponse) {
        if (res.isCommitted) return
        res.status = 403
        res.contentType = "application/problem+json"
        res.writer.write(
            """{"type":"about:blank","title":"forbidden","status":403,"code":"csrf_invalid",""" +
                """"detail":"Invalid or missing CSRF token.","traceId":"${MDC.get("traceId") ?: ""}"}""",
        )
    }

    companion object {
        const val HEADER = "X-CSRF-Token"
        private const val FORM = "application/x-www-form-urlencoded"
        private val SAFE = setOf("GET", "HEAD", "OPTIONS", "TRACE")
    }
}
