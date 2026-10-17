package com.resume.account.security

import com.resume.account.domain.SessionService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Coalesced `last_active_at` writer (T-13): any session-authenticated request
 * through this service may slide the idle bound, but a Redis set-if-absent key
 * bounds registry writes to <= 1/hour/session. Cache trouble never fails the
 * request - the idle bound is still enforced by the edge and the sweeper.
 */
class SessionTouchFilter(private val sessions: SessionService) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val userId = AuthClaims.subject()
        val sid = AuthClaims.sid()
        if (userId != null && sid != null) {
            try {
                sessions.touchIfDue(sid, userId)
            } catch (ex: Exception) {
                log.warn("session touch skipped: {}", ex.message)
            }
        }
        chain.doFilter(request, response)
    }
}
