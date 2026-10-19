package com.resume.profile

import com.resume.profile.data.CustomDomainRepository
import com.resume.profile.data.ProfileRepository
import com.resume.profile.domain.AccountPurgeService
import com.resume.profile.domain.Profile
import com.resume.profile.domain.domains.CustomDomain
import com.resume.profile.domain.domains.DomainDeniedException
import com.resume.profile.domain.domains.DomainForbiddenException
import com.resume.profile.domain.domains.DomainNotFoundException
import com.resume.profile.domain.domains.DomainQuotaReachedException
import com.resume.profile.domain.domains.DomainTakenException
import com.resume.profile.domain.domains.DomainsService
import com.resume.profile.domain.domains.ForeignDomainException
import com.resume.profile.domain.domains.Hostname
import com.resume.profile.domain.domains.HostnameInvalidException
import com.resume.profile.domain.domains.ResolveOutcome
import com.resume.profile.domain.domains.TxtLookupPort
import com.resume.profile.security.RlsUserHolder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Feature 04 pro-flow drills on real PG 18.6 + Redis (AC-01/02/03/04/06/08/11/12
 * BE halves): add gates and their order, verification-to-serving, the recheck
 * truth table (denylist drift, lapse/restore, lost control), hold expiry and
 * auto-release, reclaim transfer atomicity, purge participation, and the
 * BR-11 403/404 shape. The entitlement snapshot is stubbed at the real cache
 * key (`rb:plan:{userId}`); account-service is absent, so any HTTP path here
 * exercises fail-closed behavior only.
 */
@SpringBootTest
@Testcontainers
@Import(StubTxtConfig::class)
class DomainLifecycleIT {

    companion object {
        @Container
        @JvmStatic
        val pg: PostgreSQLContainer<Nothing> =
            PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:18.6@sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336").asCompatibleSubstituteFor("postgres"))
                .withInitScript("profile-it-init.sql")

        @Container
        @JvmStatic
        val redis: GenericContainer<Nothing> =
            GenericContainer<Nothing>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379)

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { pg.jdbcUrl }
            registry.add("spring.datasource.username") { "profile_app" }
            registry.add("spring.datasource.password") { "profile_app_it" }
            registry.add("spring.flyway.url") { pg.jdbcUrl }
            registry.add("spring.flyway.user") { "profile_migrator" }
            registry.add("spring.flyway.password") { "profile_migrator_it" }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
            // The worker sweeps are gated manually in these drills; the client
            // no-ops on a closed port (connect refused) - fail-closed paths only.
            registry.add("domains.worker.enabled") { "false" }
            registry.add("integration.account.base-url") { "http://127.0.0.1:9" }
        }
    }

    @Autowired private lateinit var service: DomainsService
    @Autowired private lateinit var purge: AccountPurgeService
    @Autowired private lateinit var domains: CustomDomainRepository
    @Autowired private lateinit var profiles: ProfileRepository
    @Autowired private lateinit var redis: StringRedisTemplate

    private fun token(): String = UUID.randomUUID().toString().replace("-", "").take(12)

    private fun uniqueHost(): String = "h${token()}.example.test"

    @BeforeEach
    fun resetStub() = STUB.clear()

    // ------------------------------------------------------------------ drills

    @Test
    fun `verify to serve drill - normalized add, one-time reveal, resolve flips`() {
        val user = seedUser(plan = "pro")
        val host = uniqueHost()

        val added = RlsUserHolder.runAs(user) { service.add(user, "  ${host.uppercase()}.  ", Instant.now()) }
        assertEquals(host, added.row.hostname, "normalization is the key the whole feature compares on")
        assertEquals(host, added.row.displayHost)
        assertEquals(CustomDomain.State.PENDING, added.row.status)
        assertNotNull(added.revealedToken, "the round's TXT value is revealed once at issue (S-2)")

        // No TXT published yet: the check stays honest and pending (no error inflation).
        val before = RlsUserHolder.runAs(user) { service.onDemandCheck(user, host, Instant.now().plusSeconds(60)) }
        assertEquals(CustomDomain.State.PENDING, before.status)
        assertEquals(ResolveOutcome.BLOCKED, resolve(host).result, "pending never serves")

        // Owner publishes the record; the on-demand check flips serving on.
        STUB.answer(Hostname.CHALLENGE_PREFIX + host, "rb-verify=${added.revealedToken}")
        val after = RlsUserHolder.runAs(user) { service.onDemandCheck(user, host, Instant.now().plusSeconds(120)) }
        assertEquals(CustomDomain.State.VERIFIED, after.status)
        assertNotNull(after.verifiedAt)

        val outcome = resolve(host)
        assertEquals(ResolveOutcome.PROFILE, outcome.result)
        assertEquals(usernameOf(user), outcome.username)
        assertEquals(host, outcome.canonicalHost)
        assertEquals("verified", latestState(host), "state changes publish domain.state_changed.v1")
    }

    @Test
    fun `a binding whose profile has no claimed username never serves`() {
        // DNS control is proven and the plan is pro, but the profile was never
        // claimed (blank username): the serving path addresses by username
        // (BR-04), so this binding is blocked - never served under a guess.
        val user = seedUser(plan = "pro", username = "")
        val host = uniqueHost()
        addAndVerify(user, host)

        val outcome = resolve(host)
        assertEquals(ResolveOutcome.BLOCKED, outcome.result)
        assertNull(outcome.username)
    }

    @Test
    fun `add gates run in order - normalize, denylist, plan, quota, uniqueness`() {
        // Normalization runs before every gate: even a free account gets the shape code.
        val free = seedUser(plan = "free")
        assertThrows(HostnameInvalidException::class.java) {
            RlsUserHolder.runAs(free) { service.add(free, "*.example.com", Instant.now()) }
        }

        // Denylist before plan: the deny answer carries no plan facts.
        val pro = seedUser(plan = "pro")
        assertThrows(DomainDeniedException::class.java) {
            RlsUserHolder.runAs(pro) { service.add(pro, "accounts.google.com", Instant.now()) }
        }
        assertThrows(DomainDeniedException::class.java) {
            RlsUserHolder.runAs(pro) { service.add(pro, "evil.g00gle.com", Instant.now()) }
        }

        // Plan gate: free denies, and a snapshot miss (account-service absent) is fail-closed.
        assertThrows(DomainForbiddenException::class.java) {
            RlsUserHolder.runAs(free) { service.add(free, uniqueHost(), Instant.now()) }
        }
        val missing = seedUser(plan = null)
        assertThrows(DomainForbiddenException::class.java) {
            RlsUserHolder.runAs(missing) { service.add(missing, uniqueHost(), Instant.now()) }
        }

        // Uniqueness across accounts: a foreign-held hostname answers 409, not a
        // raw unique violation.
        val holder = seedUser(plan = "pro")
        val host = uniqueHost()
        RlsUserHolder.runAs(holder) { service.add(holder, host, Instant.now()) }
        assertThrows(DomainTakenException::class.java) {
            RlsUserHolder.runAs(pro) { service.add(pro, host, Instant.now()) }
        }
    }

    @Test
    fun `quota caps an account at ten hostnames`() {
        val user = seedUser(plan = "pro")
        repeat(DomainsService.MAX_HOSTNAMES_PER_ACCOUNT) {
            RlsUserHolder.runAs(user) { service.add(user, "h$it.${token()}.example.test", Instant.now()) }
        }
        assertThrows(DomainQuotaReachedException::class.java) {
            RlsUserHolder.runAs(user) { service.add(user, uniqueHost(), Instant.now()) }
        }
    }

    @Test
    fun `foreign CRUD is 403 and unknown hosts are 404`() {
        val owner = seedUser(plan = "pro")
        val other = seedUser(plan = "pro")
        val host = uniqueHost()
        RlsUserHolder.runAs(owner) { service.add(owner, host, Instant.now()) }

        assertThrows(ForeignDomainException::class.java) {
            RlsUserHolder.runAs(other) { service.recheck(other, host, Instant.now()) }
        }
        assertThrows(ForeignDomainException::class.java) {
            RlsUserHolder.runAs(other) { service.onDemandCheck(other, host, Instant.now()) }
        }
        assertThrows(ForeignDomainException::class.java) {
            RlsUserHolder.runAs(other) { service.remove(other, host) }
        }
        assertThrows(DomainNotFoundException::class.java) {
            RlsUserHolder.runAs(other) { service.recheck(other, uniqueHost(), Instant.now()) }
        }
        // The owner's row is untouched by the refused foreign attempt.
        assertEquals(owner, svcRow(host)!!.userId)
    }

    @Test
    fun `lapse suspends and renewal restores while DNS control still holds`() {
        val user = seedUser(plan = "pro")
        val host = uniqueHost()
        addAndVerify(user, host)

        setPlan(user, "free")
        val suspended = RlsUserHolder.runAs(user) { service.recheck(user, host, Instant.now()) }
        assertEquals(CustomDomain.State.SUSPENDED, suspended.status)
        assertEquals(CustomDomain.Suspension.LAPSE, suspended.suspensionReason)
        assertEquals(ResolveOutcome.BLOCKED, resolve(host).result, "a lapsed plan halts serving at the gate")

        setPlan(user, "pro")
        val restored = RlsUserHolder.runAs(user) { service.recheck(user, host, Instant.now()) }
        assertEquals(CustomDomain.State.VERIFIED, restored.status)
        assertNull(restored.suspensionReason)
        assertEquals(ResolveOutcome.PROFILE, resolve(host).result, "DNS was never touched - restore is immediate")
    }

    @Test
    fun `hold expiry issues a fresh round after thirty days`() {
        val user = seedUser(plan = "pro")
        val host = uniqueHost()
        addAndVerify(user, host)
        setPlan(user, "free")
        RlsUserHolder.runAs(user) { service.recheck(user, host, Instant.now()) }

        val suspended = svcRow(host)!!
        val beforeHash = suspended.challengeHash
        ageRow(host) { it.suspendedAt = Instant.now().minusSeconds(31L * 24 * 60 * 60) }

        val released = RlsUserHolder.asService {
            service.releaseHoldExpired(domains.findByHostname(host)!!, Instant.now())
        }
        assertNotNull(released)
        assertEquals(CustomDomain.State.UNVERIFIED, released!!.status)
        assertEquals(2, released.attemptRound, "the hold release opens a fresh round")
        assertNotEquals(beforeHash, released.challengeHash, "a new token - the old record can never re-verify")
        assertNull(released.suspendedAt)
        assertEquals("unverified", latestState(host))
    }

    @Test
    fun `denylist drift suspends a live binding pending review`() {
        val user = seedUser(plan = "pro")
        val host = uniqueHost()
        addAndVerify(user, host)

        RlsUserHolder.asService { service.denylistAdd(host, "abuse", "ops:test") }
        val suspended = RlsUserHolder.asService { service.workerRecheck(domains.findByHostname(host)!!, Instant.now()) }
        assertEquals(CustomDomain.State.SUSPENDED, suspended.status)
        assertEquals(CustomDomain.Suspension.DENYLIST, suspended.suspensionReason)
        assertEquals(ResolveOutcome.BLOCKED, resolve(host).result)
        assertEquals("suspended", latestState(host))
    }

    @Test
    fun `lost control un-verifies, and fourteen failing days auto-release the host`() {
        val user = seedUser(plan = "pro")
        val host = uniqueHost()
        addAndVerify(user, host)

        STUB.clear()
        val unverified = RlsUserHolder.asService { service.workerRecheck(domains.findByHostname(host)!!, Instant.now()) }
        assertEquals(CustomDomain.State.UNVERIFIED, unverified.status)
        assertEquals(ResolveOutcome.BLOCKED, resolve(host).result)

        ageRow(host) { it.lastRecheckAt = Instant.now().minusSeconds(15L * 24 * 60 * 60) }
        val released = RlsUserHolder.asService {
            service.autoRelease(domains.findByHostname(host)!!, Instant.now())
        }
        assertTrue(released)
        assertNull(svcRow(host), "released means gone - the hostname is addable again")
        assertEquals("removed", latestState(host))

        val newcomer = seedUser(plan = "pro")
        val readded = RlsUserHolder.runAs(newcomer) { service.add(newcomer, host, Instant.now()) }
        assertEquals(CustomDomain.State.PENDING, readded.row.status)
    }

    @Test
    fun `reclaim transfer requires the fresh round TXT and is all-or-nothing`() {
        val owner = seedUser(plan = "pro")
        val claimant = seedUser(plan = "pro")
        val host = uniqueHost()
        addAndVerify(owner, host)
        val claimantProfileId = profileIdOf(claimant)

        // Round one: the forced round mints a fresh candidate value once.
        val forced = RlsUserHolder.asService {
            service.reclaimForce(host, "EV-1", "ops:test", Instant.now())
        }
        assertNotNull(forced.revealedToken)

        // Failure changes nothing for either party.
        assertNull(
            RlsUserHolder.asService {
                service.reclaimAttempt(host, claimantProfileId, "EV-1", "ops:test", Instant.now())
            },
        )
        assertEquals(owner, svcRow(host)!!.userId)

        // The claimant publishes the fresh value: one transaction moves ownership.
        STUB.answer(Hostname.CHALLENGE_PREFIX + host, "rb-verify=${forced.revealedToken}")
        val transferred = RlsUserHolder.asService {
            service.reclaimAttempt(host, claimantProfileId, "EV-1", "ops:test", Instant.now())
        }
        assertNotNull(transferred)
        assertEquals(claimant, transferred!!.userId)
        assertEquals(CustomDomain.State.VERIFIED, transferred.status)
        assertEquals(ResolveOutcome.PROFILE, resolve(host).result)
        assertEquals(usernameOf(claimant), resolve(host).username)
    }

    @Test
    fun `abuse suspension clears only with the plan and control proven`() {
        val user = seedUser(plan = "pro")
        val host = uniqueHost()
        val roundValue = addAndVerify(user, host)

        // Simulate the ops abuse hold (S-7).
        ageRow(host) {
            it.status = CustomDomain.State.SUSPENDED
            it.suspensionReason = CustomDomain.Suspension.ABUSE
            it.suspendedAt = Instant.now()
        }
        STUB.clear()
        val stillHeld = RlsUserHolder.asService { service.unsuspend(host, "ops:test", Instant.now()) }
        assertEquals(CustomDomain.State.SUSPENDED, stillHeld.status)

        STUB.answer(Hostname.CHALLENGE_PREFIX + host, "rb-verify=$roundValue")
        val cleared = RlsUserHolder.asService { service.unsuspend(host, "ops:test", Instant.now()) }
        assertEquals(CustomDomain.State.VERIFIED, cleared.status)
        assertNull(cleared.suspensionReason)
    }

    @Test
    fun `private profiles and deactivated accounts resolve neutral on any host`() {
        val user = seedUser(plan = "pro")
        val host = uniqueHost()
        addAndVerify(user, host)

        RlsUserHolder.runAs(user) {
            val profile = profiles.findByUserIdOrderByCreatedAtAsc(user).first()
            profile.isPublic = false
            profiles.saveAndFlush(profile)
        }
        assertEquals(ResolveOutcome.BLOCKED, resolve(host).result, "private never surfaces on any host")

        RlsUserHolder.runAs(user) {
            val profile = profiles.findByUserIdOrderByCreatedAtAsc(user).first()
            profile.isPublic = true
            profiles.saveAndFlush(profile)
        }
        redis.opsForValue().set("profile.acctstat:$user", "deactivated")
        assertEquals(ResolveOutcome.BLOCKED, resolve(host).result, "a suspended account never surfaces either")
    }

    @Test
    fun `purge deletes bindings and counts them honestly`() {
        val user = seedUser(plan = "pro")
        val host = uniqueHost()
        addAndVerify(user, host)

        val counts = RlsUserHolder.asService { purge.purge(user, "run-${token()}") }
        assertEquals(1L, counts.tables["custom_domains"], "the certificate count is the row count")
        assertNull(svcRow(host))
        assertEquals(0L, RlsUserHolder.asService { purge.probe(user) }.rows["custom_domains"])
    }

    // ----------------------------------------------------------------- helpers

    private fun seedUser(plan: String?, username: String = "u${token()}"): String {
        val id = "usr_${token()}"
        RlsUserHolder.runAs(id) {
            profiles.saveAndFlush(
                Profile(id = "prf_${token()}", userId = id, username = username, isPublic = true),
            )
        }
        if (plan != null) setPlan(id, plan)
        redis.opsForValue().set("profile.acctstat:$id", "active")
        return id
    }

    private fun setPlan(userId: String, plan: String) {
        redis.opsForValue().set("rb:plan:$userId", plan)
    }

    private fun usernameOf(userId: String): String =
        RlsUserHolder.runAs(userId) { profiles.findByUserIdOrderByCreatedAtAsc(userId).first().username }

    private fun profileIdOf(userId: String): String =
        RlsUserHolder.runAs(userId) { profiles.findByUserIdOrderByCreatedAtAsc(userId).first().id }

    /** Adds and verifies in one step; returns the round token (already spent). */
    private fun addAndVerify(userId: String, host: String): String {
        val added = RlsUserHolder.runAs(userId) { service.add(userId, host, Instant.now()) }
        val value = requireNotNull(added.revealedToken)
        STUB.answer(Hostname.CHALLENGE_PREFIX + host, "rb-verify=$value")
        RlsUserHolder.runAs(userId) { service.onDemandCheck(userId, host, Instant.now().plusSeconds(30)) }
        return value
    }

    private fun resolve(host: String): ResolveOutcome = RlsUserHolder.asService { service.resolveByHost(host) }

    private fun svcRow(host: String): CustomDomain? = RlsUserHolder.asService { domains.findByHostname(host) }

    /** Direct row mutation under svc scope (simulates elapsed worker windows). */
    private fun ageRow(host: String, mutate: (CustomDomain) -> Unit) {
        RlsUserHolder.asService {
            val row = domains.findByHostname(host)!!
            mutate(row)
            domains.saveAndFlush(row)
        }
    }

    private fun latestState(host: String): String? = stateEvents(host).lastOrNull()?.value?.get("attr.state")

    private fun stateEvents(host: String): List<MapRecord<String, String, String>> =
        redis.opsForStream<String, String>()
            .range("events:profile", Range.unbounded())
            .orEmpty()
            .filter { it.value["attr.hostname"] == host }
}

/** Mutable TXT stub: tests publish DNS answers through [answer]. */
class StubTxtLookupPort : TxtLookupPort {
    private val answers = ConcurrentHashMap<String, List<String>>()

    fun answer(name: String, vararg records: String) {
        answers[name] = records.toList()
    }

    fun clear() = answers.clear()

    override fun txt(name: String): List<String> = answers[name] ?: emptyList()
}

/** Shared stub instance: the bean and every drill reference the same mutable map. */
val STUB = StubTxtLookupPort()

/** Replaces the DoH adapter with the stub for every drill (never a real DNS call). */
@TestConfiguration
class StubTxtConfig {
    @Bean
    @Primary
    fun stubTxtLookupPort(): StubTxtLookupPort = STUB
}
