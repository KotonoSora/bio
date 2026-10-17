package com.resume.account.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Binds the verified identity (or dev-user) to the owner RLS scope for this
 * request. Internal handlers that operate across users set their own service
 * scope explicitly (RlsContext.asService/asOps) before the transaction starts.
 */
class RlsUserFilter : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        SecurityContextHolder.getContext().authentication?.name?.let(RlsContext::setUser)
        try {
            chain.doFilter(request, response)
        } finally {
            RlsContext.clear()
        }
    }
}
