package com.resume.account

import com.resume.account.data.SessionEntity
import com.resume.account.data.SessionRepository
import com.resume.account.data.UserEntity
import com.resume.account.data.UserRepository
import com.resume.account.domain.EdgeSessionStore
import com.resume.account.domain.SessionService
import com.resume.account.domain.Tokens
import com.resume.account.security.RlsContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.time.Instant

/**
 * Session lifecycle on real PG + Redis: the registry is truth, the edge key is
 * cache (D-2). Covers issue -> edge write, the introspect rebuild read, revoke ->
 * edge delete, NFR-17 quota eviction, and the T-05 sweeper both ways (revoke
 * when the edge key is gone; extend when activity outran the registry).
 */
@SpringBootTest
@Testcontainers
class SessionLifecycleIT {

    companion object {
        @Container
        @JvmStatic
        val pg: PostgreSQLContainer<Nothing> =
            PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:18.6@sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336").asCompatibleSubstituteFor("postgres"))
                .withInitScript("account-it-init.sql")

        @Container
        @JvmStatic
        val redis: GenericContainer<Nothing> =
            GenericContainer<Nothing>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379)

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { pg.jdbcUrl }
            registry.add("spring.datasource.username") { "account_app" }
            registry.add("spring.datasource.password") { "account_app_it" }
            registry.add("spring.flyway.user") { "account_migrator" }
            registry.add("spring.flyway.password") { "account_migrator_it" }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
            // Deterministic tests: keep the background sweeper out of the window; three-session quota.
            registry.add("account.session.sweep-initial-delay-ms") { "3600000" }
            registry.add("account.session.quota") { "3" }
        }
    }

    @Autowired
    private lateinit var users: UserRepository

    @Autowired
    private lateinit var repo: SessionRepository

    @Autowired
    private lateinit var sessions: SessionService

    @Autowired
    private lateinit var edge: EdgeSessionStore

    @Autowired
    @Qualifier("sessionRedisTemplate")
    private lateinit var edgeRedis: StringRedisTemplate

    private fun seedUser(): String {
        val id = Tokens.newId("usr")
        RlsContext.asService { users.save(UserEntity(id = id)) }
        return id
    }

    @Test
    fun `issue writes the edge key and introspect replays registry truth`() {
        val uid = seedUser()
        val authTime = Instant.now().minusSeconds(30)
        val session = RlsContext.asService { sessions.issue(uid, "IT agent", "203.0.113.0/24", authTime) }

        val key = "edge:session:${session.sid}"
        val fields = edgeRedis.opsForHash<String, String>().entries(key)
        assertEquals(uid, fields["userId"])
        assertEquals(authTime.epochSecond.toString(), fields["authTime"])
        assertEquals(session.absoluteExpiresAt.epochSecond.toString(), fields["absExp"])
        assertTrue((edgeRedis.getExpire(key) ?: -1) > 0, "edge key carries a TTL")

        val introspected = RlsContext.asService { sessions.introspect(session.sid) }
        assertNotNull(introspected)
        assertEquals(uid, introspected!!.userId)
        assertEquals(authTime.epochSecond, introspected.authTime.epochSecond)
    }

    @Test
    fun `revoke is owner-scoped and deletes the edge key`() {
        val uid = seedUser()
        val other = seedUser()
        val session = RlsContext.asService { sessions.issue(uid, null, null) }

        assertFalse(RlsContext.asService { sessions.revoke(session.sid, other) }, "foreign user cannot revoke")
        assertTrue(edge.exists(session.sid))

        assertTrue(RlsContext.asService { sessions.revoke(session.sid, uid) })
        assertFalse(edge.exists(session.sid), "edge key gone: the gateway stops validating")
        val row = RlsContext.asService { repo.findById(session.sid).orElse(null) }
        assertNotNull(row!!.revokedAt)
    }

    @Test
    fun `quota evicts the oldest session when the cap is reached`() {
        val uid = seedUser()
        val issued = (1..3).map { RlsContext.asService { sessions.issue(uid, "agent $it", null) } }
        val fourth = RlsContext.asService { sessions.issue(uid, "agent 4", null) }

        val active = RlsContext.asService { sessions.activeSessions(uid) }
        assertEquals(3, active.size)
        assertFalse(active.any { it.sid == issued.first().sid }, "oldest session evicted")
        assertTrue(active.any { it.sid == fourth.sid })
        assertFalse(edge.exists(issued.first().sid), "evicted session loses its edge key")
    }

    @Test
    fun `sweeper revokes expired sessions whose edge key is gone`() {
        val uid = seedUser()
        val sid = expiredRow(uid, edgeAlive = false)

        sessions.sweep()

        val row = RlsContext.asService { repo.findById(sid).orElse(null) }
        assertNotNull(row!!.revokedAt, "no edge proof -> revoked")
        assertFalse(edge.exists(sid))
    }

    @Test
    fun `sweeper extends when the edge key is alive`() {
        val uid = seedUser()
        val sid = expiredRow(uid, edgeAlive = true)

        sessions.sweep()

        val row = RlsContext.asService { repo.findById(sid).orElse(null) }
        assertNull(row!!.revokedAt)
        assertTrue(row.idleExpiresAt.isAfter(Instant.now()), "sliding window honors edge-validated activity")
        assertTrue(edge.exists(sid), "extended edge key kept")
    }

    private fun expiredRow(uid: String, edgeAlive: Boolean): String {
        val sid = Tokens.newSid()
        val now = Instant.now()
        val row = SessionEntity(
            sid = sid,
            userId = uid,
            createdAt = now.minus(Duration.ofDays(9)),
            lastActiveAt = now.minus(Duration.ofDays(9)),
            authTime = now.minus(Duration.ofDays(9)),
            idleExpiresAt = now.minus(Duration.ofHours(1)),
            absoluteExpiresAt = now.plus(Duration.ofDays(60)),
        )
        RlsContext.asService { repo.save(row) }
        if (edgeAlive) {
            edge.write(sid, uid, row.authTime, row.absoluteExpiresAt, Duration.ofDays(7))
        }
        return sid
    }
}
