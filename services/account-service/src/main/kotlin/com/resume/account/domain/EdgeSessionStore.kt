package com.resume.account.domain

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

/**
 * Edge validation keys on platform Redis db 2 (feature 01 D-2; contract in
 * `docs/02-architecture-techlead/01-account-and-access/03-api-contracts-and-payloads.md`):
 *
 *   edge:session:{sid}  hash { userId, authTime, absExp }   (epoch seconds)
 *   TTL = remaining min(idle, absolute); the gateway refreshes it on activity.
 *
 * The durable registry (`sessions`) is truth; this key is a cache. Loss is a
 * correct miss (introspect rebuild), never a grant. Account-service owns
 * writes/deletes; the gateway reads only.
 */
@Service
class EdgeSessionStore(
    @Qualifier("sessionRedisTemplate") private val sessions: StringRedisTemplate,
) {
    fun write(sid: String, userId: String, authTime: Instant, absoluteExpiresAt: Instant, ttl: Duration) {
        val key = key(sid)
        sessions.opsForHash<String, String>().putAll(
            key,
            mapOf(
                "userId" to userId,
                "authTime" to authTime.epochSecond.toString(),
                "absExp" to absoluteExpiresAt.epochSecond.toString(),
            ),
        )
        sessions.expire(key, ttl.coerceAtLeast(Duration.ofSeconds(1)))
    }

    fun refreshAuthTime(sid: String, authTime: Instant) {
        sessions.opsForHash<String, String>().put(key(sid), "authTime", authTime.epochSecond.toString())
    }

    fun delete(sid: String) {
        sessions.delete(key(sid))
    }

    fun exists(sid: String): Boolean = sessions.hasKey(key(sid))

    private fun key(sid: String) = "edge:session:$sid"
}
