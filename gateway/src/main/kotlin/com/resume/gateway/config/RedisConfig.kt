package com.resume.gateway.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * One platform Redis instance; the gateway touches three logical dbs
 * (allocation table in 01-architecture-and-service-topology.md):
 *  - db 1: edge rate-limit counters (ownership: gateway);
 *  - db 2: `edge:session:{sid}` validation keys (ownership: account-service;
 *    read-only here - the gateway writes only the introspect rebuild path);
 *  - db 4: the `events:profile` stream (ownership: profile-service - read-only
 *    here, feature 04's domain.state_changed.v1 consumer group).
 * Streams are the one cross-service Redis object (baseline: "Queues use Redis
 * Streams with consumer groups" - consumers live in other services by design),
 * so DEVIATION FLAGGED (stream db): the event consumer reads from the
 * producer's logical db; caches and counters stay strictly per-service and the
 * primary template is untouched.
 */
@Configuration
class RedisConfig {

    @Bean
    @Primary
    fun redisConnectionFactory(
        @Value("\${spring.data.redis.host:localhost}") host: String,
        @Value("\${spring.data.redis.port:6379}") port: Int,
    ): LettuceConnectionFactory = factory(host, port, database = 1)

    @Bean
    @Primary
    fun stringRedisTemplate(@Qualifier("redisConnectionFactory") factory: LettuceConnectionFactory): StringRedisTemplate =
        StringRedisTemplate(factory)

    /** db 2: session validation keys (feature 01 D-2). */
    @Bean
    fun edgeRedisConnectionFactory(
        @Value("\${spring.data.redis.host:localhost}") host: String,
        @Value("\${spring.data.redis.port:6379}") port: Int,
    ): LettuceConnectionFactory = factory(host, port, database = 2)

    @Bean
    fun edgeRedisTemplate(@Qualifier("edgeRedisConnectionFactory") factory: LettuceConnectionFactory): StringRedisTemplate =
        StringRedisTemplate(factory)

    /** db 4: `events:profile` stream reads (feature 04 state-change consumer). */
    @Bean
    fun domainEventsRedisConnectionFactory(
        @Value("\${spring.data.redis.host:localhost}") host: String,
        @Value("\${spring.data.redis.port:6379}") port: Int,
        @Value("\${edge.domain-events.database:4}") database: Int,
    ): LettuceConnectionFactory = factory(host, port, database = database)

    @Bean
    fun domainEventsRedisTemplate(
        @Qualifier("domainEventsRedisConnectionFactory") factory: LettuceConnectionFactory,
    ): StringRedisTemplate = StringRedisTemplate(factory)

    private fun factory(host: String, port: Int, database: Int): LettuceConnectionFactory {
        val cfg = RedisStandaloneConfiguration(host, port)
        cfg.database = database
        return LettuceConnectionFactory(cfg)
    }
}
