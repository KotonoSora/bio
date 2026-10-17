package com.resume.resume.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/** Binds the verified identity (or dev-user) to the RLS holder for this request. */
class RlsUserFilter : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        SecurityContextHolder.getContext().authentication?.name?.let(RlsUserHolder::set)
        try {
            chain.doFilter(request, response)
        } finally {
            RlsUserHolder.clear()
        }
    }
}
