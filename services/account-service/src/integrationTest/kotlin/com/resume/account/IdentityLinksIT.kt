package com.resume.account

import com.resume.account.data.UserEntity
import com.resume.account.data.UserRepository
import com.resume.account.domain.IdpProfile
import com.resume.account.domain.IdentityService
import com.resume.account.domain.LinkOutcome
import com.resume.account.domain.Tokens
import com.resume.account.domain.UnlinkOutcome
import com.resume.account.security.RlsContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

/**
 * AC-05 BE half on real PG: find-or-create resolves by `(provider_id,
 * provider_sub)` only (BR-02), a foreign subject never attaches (no merge), and
 * the last linked provider can never be unlinked (BR-05). The settings read
 * (`linkedProviders`) stays owner-scoped under RLS.
 */
@SpringBootTest
@Testcontainers
class IdentityLinksIT {

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
            // Deterministic tests: keep the background sweeper out of the window.
            registry.add("account.session.sweep-initial-delay-ms") { "3600000" }
        }
    }

    @Autowired
    private lateinit var users: UserRepository

    @Autowired
    private lateinit var identities: IdentityService

    private fun seedUser(): String {
        val id = Tokens.newId("usr")
        RlsContext.asService { users.save(UserEntity(id = id)) }
        return id
    }

    private fun profile(provider: String, sub: String, email: String? = null) =
        IdpProfile(providerId = provider, subject = sub, email = email, emailVerified = email != null, name = null)

    @Test
    fun `find-or-create resolves by provider subject only`() {
        val (user, created) = RlsContext.asService {
            identities.resolve(profile("google", "sub-stable", "first@example.test"), null)
        }
        assertTrue(created)

        // The IdP email changed: the same subject must resolve to the same account.
        val (again, createdAgain) = RlsContext.asService {
            identities.resolve(profile("google", "sub-stable", "changed@example.test"), null)
        }
        assertEquals(user.id, again.id)
        assertEquals("changed@example.test", again.email)
        assertFalse(createdAgain)
    }

    @Test
    fun `link refuses a subject owned by another account and unlink guards the last link`() {
        val owner = seedUser()
        val other = seedUser()
        RlsContext.asService { identities.link(owner, profile("google", "sub-owner"), null) }

        val foreign = RlsContext.asService { identities.link(other, profile("google", "sub-owner"), null) }
        assertEquals(LinkOutcome.ForeignSubject, foreign, "a foreign subject never merges")

        val lastLink = RlsContext.asService { identities.unlink(owner, "google", null) }
        assertEquals(UnlinkOutcome.LastLink, lastLink)

        val second = RlsContext.asService { identities.link(owner, profile("linkedin", "sub-owner-li"), null) }
        assertInstanceOf(LinkOutcome.Linked::class.java, second)
        val sameAgain = RlsContext.asService { identities.link(owner, profile("linkedin", "sub-owner-li"), null) }
        assertEquals(LinkOutcome.AlreadyLinked, sameAgain)

        val unlinked = RlsContext.asService { identities.unlink(owner, "linkedin", null) }
        assertEquals(UnlinkOutcome.Unlinked, unlinked)
        val remaining = RlsContext.asService { identities.linkedProviders(owner) }
        assertEquals(listOf("google"), remaining.map { it.providerId })

        // Owner scope cannot read foreign link rows even when handed a foreign id.
        RlsContext.setUser(other)
        val sneak = try {
            identities.linkedProviders(owner)
        } finally {
            RlsContext.clear()
        }
        assertTrue(sneak.isEmpty(), "owner scope never leaks foreign links")
    }
}
