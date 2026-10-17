package com.resume.account.config

import com.resume.account.security.ActAsPolicy
import com.resume.account.security.InternalJwtAuthFilter
import com.resume.account.security.RlsUserFilter
import com.resume.account.security.ServiceRateFilter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate
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
 * (never container-registered, so counters fire once per request).
 */
@Configuration
class SecurityConfig(
    private val actAsPolicy: ActAsPolicy,
    private val redis: StringRedisTemplate,
    private val meters: MeterRegistry,
    @Value("\${internal.jwt.audience:account-service}")
    private val audience: String,
    @Value("\${internal.jwt.public-key-pem:}")
    private val publicKeyPem: String,
    @Value("\${internal.jwt.previous-public-key-pem:}")
    private val previousPublicKeyPem: String,
    @Value("\${service.ratelimit.per-minute:120}")
    private val rateLimitPerMinute: Int,
) {

    @Bean
    fun internalJwtAuthFilter(): InternalJwtAuthFilter =
        InternalJwtAuthFilter(audience, publicKeyPem, previousPublicKeyPem, actAsPolicy, meters)

    @Bean
    fun rlsUserFilter(): RlsUserFilter = RlsUserFilter()

    @Bean
    fun serviceRateFilter(): ServiceRateFilter =
        ServiceRateFilter(redis, rateLimitPerMinute, meters)

    @Bean
    fun filterChain(
        http: HttpSecurity,
        internalJwtAuthFilter: InternalJwtAuthFilter,
        rlsUserFilter: RlsUserFilter,
        serviceRateFilter: ServiceRateFilter,
    ): SecurityFilterChain {
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
                ).permitAll()
                it.requestMatchers(*publicPaths()).permitAll()
                it.anyRequest().authenticated()
            }
            .addFilterBefore(internalJwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterAfter(rlsUserFilter, InternalJwtAuthFilter::class.java)
            .addFilterAfter(serviceRateFilter, RlsUserFilter::class.java)
        return http.build()
    }

    fun publicPaths(): Array<String> = arrayOf("/api/v1/auth/**")
}
