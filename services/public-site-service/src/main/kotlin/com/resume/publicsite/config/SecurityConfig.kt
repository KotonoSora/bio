package com.resume.publicsite.config

import com.resume.publicsite.security.ActAsPolicy
import com.resume.publicsite.security.InternalJwtAuthFilter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * Deny-by-default. Public list is enumerated per service spec; everything else
 * requires the gateway-signed internal JWT (RS256, aud=<service>, 60s TTL).
 * Raw client headers are never trusted. Services never read session cookies.
 *
 * Filters are declared here exactly once and run only inside the chain
 * (the IP-keyed service backstop stays container-level: this surface is
 * anonymous SSR, so principal keying does not apply).
 */
@Configuration
class SecurityConfig(
    private val actAsPolicy: ActAsPolicy,
    private val meters: MeterRegistry,
    @Value("\${internal.jwt.audience:public-site-service}")
    private val audience: String,
    @Value("\${internal.jwt.public-key-pem:}")
    private val publicKeyPem: String,
    @Value("\${internal.jwt.previous-public-key-pem:}")
    private val previousPublicKeyPem: String,
) {

    @Bean
    fun internalJwtAuthFilter(): InternalJwtAuthFilter =
        InternalJwtAuthFilter(audience, publicKeyPem, previousPublicKeyPem, actAsPolicy, meters)

    @Bean
    fun filterChain(http: HttpSecurity, internalJwtAuthFilter: InternalJwtAuthFilter): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/health/**",
                    "/actuator/health/**",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/.well-known/jwks.json",
                    "/error", // sendError error-dispatch: render the status, never re-authorize
                ).permitAll()
                it.requestMatchers(*publicPaths()).permitAll()
                it.anyRequest().authenticated()
            }
            .addFilterBefore(internalJwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }

    /** Stateless SSR surface: public pages are public by design; only GET endpoints exist. */
    fun publicPaths(): Array<String> = arrayOf("/**")
}
