package com.resume.gateway.config

import com.resume.gateway.security.EdgeSessionFilter
import com.resume.gateway.security.SessionIntrospectClient
import io.micrometer.core.instrument.MeterRegistry
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.intercept.AuthorizationFilter

/**
 * Edge termination model (01-architecture-and-service-topology.md, feature 01
 * D-6): the gateway stops at the edge session and mints the internal identity
 * JWT; services downstream trust ONLY that JWT. SSO runs behind account-service
 * (the `/api/auth` surface), so no OAuth2 login filter exists here.
 * Deny-by-default: mesh namespace denied, anonymous write surfaces pass
 * (verified downstream by signature/Turnstile/state), owner API (`/api/v1/me`
 * and `/api/v1` surfaces) requires the edge session. CSRF is the custom
 * double-submit filter (S-6); the edge session filter is registered inside this
 * chain so the resolved identity backs `authenticated()` rules.
 */
@Configuration
class SecurityConfig(
    @Qualifier("edgeRedisTemplate") private val edgeRedis: StringRedisTemplate,
    private val introspect: SessionIntrospectClient,
    private val meters: MeterRegistry,
    @Value("\${edge.session.cookie-name:rb_session}") private val edgeCookieName: String,
    @Value("\${edge.session.idle-days:7}") private val edgeIdleDays: Long,
) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() } // CSRF is the gateway double-submit filter (S-6)
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint { _, res, _ ->
                    problem(res, 401, "unauthenticated", "Authentication required.")
                }
                it.accessDeniedHandler { _, res, _ ->
                    problem(res, 403, "forbidden", "Not allowed.")
                }
            }
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
                // Anonymous write surfaces: signature/Turnstile/state-verified downstream.
                it.requestMatchers("/api/t/**", "/api/merch/partner-callback", "/webhooks/**").permitAll()
                it.requestMatchers("/api/v1/social/*/callback").permitAll()
                // Public reads: SSR profiles, redirects, resume reads, micro-script.
                it.requestMatchers(HttpMethod.GET, "/go/**", "/", "/{username}", "/cv/**", "/s/ms.js").permitAll()
                // SSO surfaces (feature 01): initiation, callbacks, recovery, provider list.
                // Session-bound endpoints self-check on the `sid` claim, so anonymous
                // probes fall through to the `auth_disabled` 404 surface.
                it.requestMatchers("/api/auth/**", "/api/v1/auth/**").permitAll()
                // Session/owner API.
                it.requestMatchers("/api/v1/**").authenticated()
                it.anyRequest().authenticated()
            }
            .addFilterBefore(
                EdgeSessionFilter(edgeRedis, introspect, edgeCookieName, edgeIdleDays, meters),
                AuthorizationFilter::class.java,
            )
        return http.build()
    }

    private fun problem(res: HttpServletResponse, status: Int, code: String, detail: String) {
        if (res.isCommitted) return
        res.status = status
        res.contentType = "application/problem+json"
        res.writer.write(
            """{"type":"about:blank","title":"$code","status":$status,"code":"$code","detail":"$detail"}""",
        )
    }
}
