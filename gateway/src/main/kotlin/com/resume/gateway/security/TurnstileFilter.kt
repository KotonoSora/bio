package com.resume.gateway.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/** Enforces the Turnstile-class challenge on abuse-prone anonymous POST surfaces. */
@Component
@Order(2)
class TurnstileFilter(private val turnstile: TurnstileService) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.method != "POST" || !request.requestURI.startsWith("/api/t/")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val token = request.getHeader("X-Turnstile-Token") ?: request.getParameter("cf-turnstile-response")
        if (!turnstile.verify(token, request.remoteAddr)) {
            response.sendError(422, "challenge required")
            return
        }
        chain.doFilter(request, response)
    }
}
