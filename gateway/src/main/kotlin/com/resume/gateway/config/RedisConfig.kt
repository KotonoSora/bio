package com.resume.gateway.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * One platform Redis instance; this service touches ONLY logical db 1
 * (allocation table in 01-architecture-and-service-topology.md).
 * Key namespaces start with the service name; cross-service keys are forbidden.
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
        cfg.database = 1
        return LettuceConnectionFactory(cfg)
    }

    @Bean
    fun stringRedisTemplate(factory: LettuceConnectionFactory): StringRedisTemplate =
        StringRedisTemplate(factory)
}
