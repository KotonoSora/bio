package com.resume.gateway.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * CSP default-src self enforced at the edge for public pages (baseline
 * 08-security-baseline). API/webhook/health responses are data, not documents,
 * and intentionally carry no CSP.
 */
@Component
@Order(3)
class CspFilter : OncePerRequestFilter() {

    private val singleSegment = Regex("^/[^/]+$")

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if (request.method != "GET") return true
        val p = request.requestURI
        if (p.startsWith("/go/") || p == "/") return false
        if (p.startsWith("/api/") || p.startsWith("/webhooks/") || p.startsWith("/health") ||
            p.startsWith("/actuator") || p.startsWith("/v3/api-docs") ||
            p.startsWith("/swagger") || p.startsWith("/.well-known")
        ) {
            return true
        }
        return !singleSegment.matches(p) // public profile pages only
    }

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        response.setHeader("Content-Security-Policy", "default-src 'self'")
        response.setHeader("X-Content-Type-Options", "nosniff")
        chain.doFilter(request, response)
    }
}
