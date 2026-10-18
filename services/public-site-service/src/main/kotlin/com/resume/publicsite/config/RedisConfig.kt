package com.resume.publicsite.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * One platform Redis instance; this service owns logical db 5 (allocation
 * table in 01-architecture-and-service-topology.md) and reads one foreign
 * stream:
 *  - db 5: `ssr:{host}:{slug}` render keys + purge tags (ownership: this
 *    service);
 *  - db 4: the `events:profile` stream (ownership: profile-service -
 *    read-only here, feature 04's domain.state_changed.v1 consumer group).
 * Streams are the one cross-service Redis object (baseline: "Queues use Redis
 * Streams with consumer groups" - consumers live in other services by design),
 * so DEVIATION FLAGGED (stream db): the event consumer reads from the
 * producer's logical db; render caches stay strictly on db 5.
 */
@Configuration
class RedisConfig {
    @Bean
    @Primary
    fun redisConnectionFactory(
        @Value("\${spring.data.redis.host:localhost}")
        host: String,
        @Value("\${spring.data.redis.port:6379}")
        port: Int,
    ): LettuceConnectionFactory {
        val cfg = RedisStandaloneConfiguration(host, port)
        cfg.database = 5
        return LettuceConnectionFactory(cfg)
    }

    @Bean
    @Primary
    fun stringRedisTemplate(
        @Qualifier("redisConnectionFactory") factory: LettuceConnectionFactory,
    ): StringRedisTemplate = StringRedisTemplate(factory)

    /** db 4: `events:profile` stream reads (feature 04 state-change consumer). */
    @Bean
    fun domainEventsRedisConnectionFactory(
        @Value("\${spring.data.redis.host:localhost}")
        host: String,
        @Value("\${spring.data.redis.port:6379}")
        port: Int,
        @Value("\${integration.profile-events.database:4}")
        database: Int,
    ): LettuceConnectionFactory {
        val cfg = RedisStandaloneConfiguration(host, port)
        cfg.database = database
        return LettuceConnectionFactory(cfg)
    }

    @Bean
    fun domainEventsRedisTemplate(
        @Qualifier("domainEventsRedisConnectionFactory") factory: LettuceConnectionFactory,
    ): StringRedisTemplate = StringRedisTemplate(factory)
}
