package com.resume.account.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * Two Redis allocations (01-architecture-and-service-topology.md):
 * - db 7 (primary): account-service's own keys — auth state, contact-email
 *   codes, recovery invitations, touch coalescing, rate backstops.
 * - db 2 (session template): the edge validation keys `edge:session:{sid}`,
 *   managed here and read-only for the gateway (feature 01 D-2).
 */
@Configuration
class RedisConfig {

    @Bean
    @Primary
    fun redisConnectionFactory(
        @Value("\${spring.data.redis.host:localhost}") host: String,
        @Value("\${spring.data.redis.port:6379}") port: Int,
        @Value("\${spring.data.redis.database:7}") database: Int,
    ): LettuceConnectionFactory {
        val cfg = RedisStandaloneConfiguration(host, port)
        cfg.database = database
        return LettuceConnectionFactory(cfg)
    }

    @Bean
    @Primary
    fun stringRedisTemplate(factory: LettuceConnectionFactory): StringRedisTemplate =
        StringRedisTemplate(factory)

    @Bean(name = ["sessionRedisConnectionFactory"])
    fun sessionRedisConnectionFactory(
        @Value("\${spring.data.redis.host:localhost}") host: String,
        @Value("\${spring.data.redis.port:6379}") port: Int,
        @Value("\${spring.data.redis.edge-database:2}") database: Int,
    ): LettuceConnectionFactory {
        val cfg = RedisStandaloneConfiguration(host, port)
        cfg.database = database
        return LettuceConnectionFactory(cfg)
    }

    @Bean(name = ["sessionRedisTemplate"])
    fun sessionRedisTemplate(
        @org.springframework.beans.factory.annotation.Qualifier("sessionRedisConnectionFactory")
        factory: LettuceConnectionFactory,
    ): StringRedisTemplate = StringRedisTemplate(factory)
}
