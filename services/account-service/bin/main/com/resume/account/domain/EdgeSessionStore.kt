package com.resume.account.domain

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * Edge session keys on Redis db 2. Sessions terminate at the gateway; services
 * never read session cookies — they trust only the internal JWT. TTL-bounded,
 * eviction is a correct zero (re-login), never an error path.
 */
@Service
class EdgeSessionStore(
    @Qualifier("sessionRedisTemplate") private val sessions: StringRedisTemplate,
) {
    fun touch(sessionId: String, userId: String, ttl: Duration = Duration.ofHours(12)) {
        sessions.opsForValue().set("edge:session:$sessionId", userId, ttl)
    }

    fun lookup(sessionId: String): String? = sessions.opsForValue().get("edge:session:$sessionId")

    fun revoke(sessionId: String) {
        sessions.delete("edge:session:$sessionId")
    }
}
