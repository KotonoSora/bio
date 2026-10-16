package com.resume.gateway.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.csrf.CookieCsrfTokenRepository

/**
 * Edge termination model (01-architecture-and-service-topology.md):
 * - Gateway terminates SSO sessions (Google, Apple, Facebook, LinkedIn, generic OIDC).
 * - The internal mesh namespace is denied from the public edge -> denyAll here.
 * - The session API requires the edge session; services downstream trust ONLY the
 *   gateway-signed internal JWT, never raw client headers or cookies.
 * - Anonymous write surfaces (beacon, lead forms, partner/rail/provider webhooks,
 *   publish OAuth callbacks) pass through; verification is signature/Turnstile-based
 *   downstream, never session-based. CSRF double-submit enforced on session mutations.
 */
@Configuration
class SecurityConfig {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf {
                it.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                it.ignoringRequestMatchers(
                    "/health/**",
                    "/actuator/health/**",
                    "/.well-known/jwks.json",
                    "/api/t/**",
                    "/api/merch/partner-callback",
                    "/webhooks/**",
                    "/api/v1/social/*/callback",
                )
            }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/health/**",
                    "/actuator/health/**",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/.well-known/jwks.json",
                ).permitAll()
                // Mesh-only namespace: never served from the public edge.
                it.requestMatchers("/internal/v1/**").denyAll()
                // Anonymous write surfaces: signature/Turnstile-verified downstream.
                it.requestMatchers(
                    "/api/t/**",
                    "/api/merch/partner-callback",
                    "/webhooks/**",
                    "/api/v1/social/*/callback",
                ).permitAll()
                // Public reads: SSR profiles, redirects, resume reads, micro-script.
                it.requestMatchers(HttpMethod.GET, "/go/**", "/", "/{username}", "/cv/**", "/s/ms.js").permitAll()
                // SSO entry points redirect to the provider (no session required).
                it.requestMatchers("/api/v1/auth/**").permitAll()
                // Session API.
                it.requestMatchers("/api/v1/**").authenticated()
                it.anyRequest().authenticated()
            }
            .oauth2Login { }
        return http.build()
    }
}
