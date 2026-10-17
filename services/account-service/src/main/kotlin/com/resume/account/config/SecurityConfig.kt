package com.resume.account.config

import com.resume.account.domain.SessionService
import com.resume.account.security.ActAsPolicy
import com.resume.account.security.InternalJwtAuthFilter
import com.resume.account.security.RlsUserFilter
import com.resume.account.security.ServiceRateFilter
import com.resume.account.security.SessionTouchFilter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * Deny-by-default. Public surface is exactly: SSO initiation/callbacks/providers,
 * recovery landing, health/docs. The gateway is the only caller in production;
 * services never read session cookies and trust only the internal JWT. Session
 * endpoints under the `/api/auth` surface (step-up, link, unlink, sign-out) and
 * the owner API (`/api/v1/me`) additionally require the `sid` claim - enforced in the
 * controllers via AuthClaims/StepUpGuard, so anonymous probes fall through to
 * the `auth_disabled` 404 surface.
 */
@Configuration
class SecurityConfig(
    private val actAsPolicy: ActAsPolicy,
    private val redis: StringRedisTemplate,
    private val meters: MeterRegistry,
    private val sessions: SessionService,
    @Value("\${internal.jwt.audience:account-service}") private val audience: String,
    @Value("\${internal.jwt.public-key-pem:}") private val publicKeyPem: String,
    @Value("\${internal.jwt.previous-public-key-pem:}") private val previousPublicKeyPem: String,
    @Value("\${service.ratelimit.per-minute:120}") private val rateLimitPerMinute: Int,
) {

    @Bean
    fun internalJwtAuthFilter(): InternalJwtAuthFilter =
        InternalJwtAuthFilter(audience, publicKeyPem, previousPublicKeyPem, actAsPolicy, meters)

    @Bean
    fun rlsUserFilter(): RlsUserFilter = RlsUserFilter()

    @Bean
    fun serviceRateFilter(): ServiceRateFilter = ServiceRateFilter(redis, rateLimitPerMinute, meters)

    @Bean
    fun sessionTouchFilter(): SessionTouchFilter = SessionTouchFilter(sessions)

    @Bean
    fun filterChain(
        http: HttpSecurity,
        internalJwtAuthFilter: InternalJwtAuthFilter,
        rlsUserFilter: RlsUserFilter,
        serviceRateFilter: ServiceRateFilter,
        sessionTouchFilter: SessionTouchFilter,
    ): SecurityFilterChain {
        http
            .csrf { it.disable() } // double-submit is enforced at the gateway (S-6)
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint { _, res, _ ->
                    res.status = HttpStatus.UNAUTHORIZED.value()
                    res.contentType = "application/problem+json"
                    res.writer.write(
                        """{"type":"about:blank","title":"unauthenticated","status":401,""" +
                            """"code":"unauthenticated","detail":"Authentication required."}""",
                    )
                }
                it.accessDeniedHandler { _, res, _ ->
                    res.status = HttpStatus.FORBIDDEN.value()
                    res.contentType = "application/problem+json"
                    res.writer.write(
                        """{"type":"about:blank","title":"forbidden","status":403,"code":"forbidden","detail":"Not allowed."}""",
                    )
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
                // SSO entry points, callbacks, recovery landing, provider list, and the
                // `auth_disabled` probe fallback. Session-bound endpoints self-check.
                it.requestMatchers("/api/auth/**", "/api/v1/auth/**").permitAll()
                it.anyRequest().authenticated()
            }
            .addFilterBefore(internalJwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterAfter(rlsUserFilter, InternalJwtAuthFilter::class.java)
            .addFilterAfter(sessionTouchFilter, RlsUserFilter::class.java)
            .addFilterAfter(serviceRateFilter, SessionTouchFilter::class.java)
        return http.build()
    }
}
