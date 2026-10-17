package com.resume.account.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * One platform Redis instance. This service owns logical db 7 and additionally
 * manages the edge session keys on db 2 (contract in
 * 01-account-and-access/03-api-contracts-and-payloads.md).
 * dbs 0-2 are edge/platform infrastructure; 3+ are per-service.
 * Key namespaces start with the service/domain; cross-service keys are forbidden.
 */
@Configuration
class RedisConfig {
    @Bean
    fun redisConnectionFactory(
        @org.springframework.beans.factory.annotation.Value("\${spring.data.redis.host:localhost}")
        host: String,
        @org.springframework.beans.factory.annotation.Value("\${spring.data.redis.port:6379}")
        port: Int,
    ): LettuceConnectionFactory {
        val cfg = RedisStandaloneConfiguration(host, port)
        cfg.database = 7
        return LettuceConnectionFactory(cfg)
    }

    @Bean
    fun stringRedisTemplate(factory: LettuceConnectionFactory): StringRedisTemplate =
        StringRedisTemplate(factory)

    @Bean
    fun sessionConnectionFactory(
        @org.springframework.beans.factory.annotation.Value("\${spring.data.redis.host:localhost}")
        host: String,
        @org.springframework.beans.factory.annotation.Value("\${spring.data.redis.port:6379}")
        port: Int,
    ): LettuceConnectionFactory {
        val cfg = RedisStandaloneConfiguration(host, port)
        cfg.database = 2 // edge session keys (db 2), managed by account-service
        return LettuceConnectionFactory(cfg)
    }

    @Bean
    fun sessionRedisTemplate(sessionConnectionFactory: LettuceConnectionFactory): StringRedisTemplate =
        StringRedisTemplate(sessionConnectionFactory)
}
