package com.resume.profile.domain.domains

import com.resume.profile.data.CustomDomainRepository
import com.resume.profile.data.DomainDenylistRepository
import com.resume.profile.data.ProfileRepository
import com.resume.profile.domain.AuditService
import com.resume.profile.events.DomainEvent
import com.resume.profile.events.EventPublisher
import com.resume.profile.integration.AccountStatusClient
import com.resume.profile.integration.EntitlementSnapshotClient
import com.resume.profile.security.RlsUserHolder
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Feature 04 application service (DDD slice): the one place binding state
 * transitions happen (TL 02 transitions table). Serving reads exactly one
 * gate - `verified` + plan pro (S-1) - and the plan snapshot is fail-closed
 * (snapshot miss denies, D-3/BR-06). Denylist and control checks order
 * fail-safety first: a denylisted host never serves regardless of plan (S-4).
 */
@Service
class DomainsService(
    private val domains: CustomDomainRepository,
    private val denylistRepo: DomainDenylistRepository,
    private val profiles: ProfileRepository,
    private val entitlements: EntitlementSnapshotClient,
    private val accountStatus: AccountStatusClient,
    private val challenges: ChallengeService,
    private val verifier: TxtLookupPort,
    private val existenceProbe: DomainExistenceProbe,
    private val audit: AuditService,
    private val events: EventPublisher,
    private val redis: StringRedisTemplate,
    meters: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val resolveBlocked = meters.counter("domain.resolve.blocked")
    private val resolveUnknown = meters.counter("domain.resolve.unknown")

    // ---------------------------------------------------------------- owner

    /**
     * BR-02 add: normalize -> denylist -> plan gate -> quota -> uniqueness;
     * the row starts `pending` with a fresh challenge round (BR-01) whose
     * one-time token is revealed exactly once through the response mapping.
     */
    @Transactional
    fun add(userId: String, rawHostname: String, now: Instant): RoundResult {
        val host = Hostname.of(rawHostname)
        if (isDenylisted(host.ascii)) throw DomainDeniedException()
        if (!entitlements.isPro(userId)) throw DomainForbiddenException()
        if (domains.countByUserId(userId) >= MAX_HOSTNAMES_PER_ACCOUNT) throw DomainQuotaReachedException()
        // BR-03 takeover guard: owner scope sees own rows; the svc-scope probe sees
        // the rest (a foreign-held hostname answers 409, never a raw unique violation).
        if (domains.existsByHostname(host.ascii) || takenByAnyOwner(host.ascii)) throw DomainTakenException()

        val round = challenges.issue(now)
        val profile = profiles.findByUserIdOrderByCreatedAtAsc(userId).firstOrNull()
            ?: throw HostnameInvalidException("You need a profile before you can add a domain.")
        val row = domains.saveAndFlush(
            CustomDomain(
                id = "dmn_" + UUID.randomUUID().toString().take(12),
                hostname = host.ascii,
                displayHost = host.display,
                profileId = profile.id,
                userId = userId,
                status = CustomDomain.State.PENDING,
                challengeHash = round.tokenHash,
                challengeIssuedAt = round.issuedAt,
            ),
        )
        audit.append(userId, "domain-owner", "domain.add", host.ascii, "pending")
        return RoundResult(row, round.token)
    }

    @Transactional(readOnly = true)
    fun list(userId: String): List<CustomDomain> = domains.findByUserIdOrderByCreatedAtAsc(userId)

    fun isPro(userId: String): Boolean = entitlements.isPro(userId)

    /**
     * On-demand TXT check (BR-01 step 3; NFR-02 <= 60 s): success flips
     * `verified`, serving starts after the platform TLS job; failures stay
     * pending - "no error inflation" (BA 05).
     */
    @Transactional
    fun onDemandCheck(userId: String, hostname: String, now: Instant): CustomDomain {
        val row = ownedOrForeignOrUnknown(userId, hostname)
        return roundCheck(row, now)
    }

    /**
     * Owner recheck (BR-08): re-evaluates denylist, plan, and DNS control -
     * the denylist drift path suspends a live binding pending review, a plan
     * lapse suspends (DNS untouched, BR-07), lost control un-verifies.
     */
    @Transactional
    fun recheck(userId: String, hostname: String, now: Instant): CustomDomain {
        val row = ownedOrForeignOrUnknown(userId, hostname)
        return applyRecheck(row, now)
    }

    /**
     * Fresh round, same row (BA 04 "Regenerate challenge"): old token dies
     * (S-2); the value is shown once in this call's response only.
     */
    @Transactional
    fun reissueChallenge(userId: String, hostname: String, now: Instant): RoundResult {
        val row = ownedOrForeignOrUnknown(userId, hostname)
        if (row.isLive()) throw IllegalStateException("A verified host has no active round to regenerate.")
        val round = challenges.issue(now)
        row.challengeHash = round.tokenHash
        row.challengeIssuedAt = round.issuedAt
        row.attemptRound += 1
        row.updatedAt = now
        resetAttempts(row.hostname)
        val saved = domains.saveAndFlush(row)
        audit.append(row.userId, "domain-owner", "domain.reissue", row.hostname, "round${row.attemptRound}")
        return RoundResult(saved, round.token)
    }

    /** Explicit removal (BR-07): deleted immediately; serving halts at the commit (NFR-04). */
    @Transactional
    fun remove(userId: String, hostname: String) {
        val row = ownedOrForeignOrUnknown(userId, hostname)
        domains.delete(row)
        audit.append(row.userId, "domain-owner", "domain.remove", row.hostname, "ok")
        publishState(row.userId, row.hostname, Event.STATE_REMOVED)
    }

    /** IDOR shape (BR-11): `403` for foreign targets, `404` for unknown hostnames. */
    private fun ownedOrForeignOrUnknown(userId: String, hostname: String): CustomDomain {
        val normalized = Hostname.of(hostname)
        val own = domains.findByHostname(normalized.ascii)
        if (own != null) return own // owner RLS guarantees this row belongs to `userId`
        // Foreign rows are invisible under owner RLS; one svc-scope existence read
        // restores the 403/404 distinction without widening the owner path.
        val foreign = RlsUserHolder.asService { existenceProbe.lookup(normalized.ascii) }
        if (foreign != null && foreign.userId != userId) throw ForeignDomainException()
        throw DomainNotFoundException()
    }

    /** Add-time takeover guard: true when any owner (own or foreign) already holds the host. */
    private fun takenByAnyOwner(hostAscii: String): Boolean =
        RlsUserHolder.asService { existenceProbe.lookup(hostAscii) } != null

    // --------------------------------------------------------------- worker

    /** Pending poll cadence (NFR-02): 30 s, 2 m, 5 m, 15 m, 1 h, then every 6 h in the 7-day window. */
    fun nextPollDue(row: CustomDomain, now: Instant): Boolean {
        val anchor = row.lastRecheckAt ?: row.challengeIssuedAt
        val attempts = attemptsFor(row.hostname).coerceIn(0, POLL_BACKOFF_SECONDS.lastIndex)
        return !anchor.plusSeconds(POLL_BACKOFF_SECONDS[attempts]).isAfter(now)
    }

    @Transactional
    fun pollPending(row: CustomDomain, now: Instant): CustomDomain = roundCheck(row, now)

    /** Live binding rechecks (BR-08): every 24 h staggered by hostname hash (NFR-03). */
    fun isRecheckDue(row: CustomDomain, now: Instant): Boolean {
        val anchor = row.lastRecheckAt ?: row.verifiedAt ?: row.updatedAt
        return !anchor.plus(staggerSeconds(row.hostname)).isAfter(now)
    }

    @Transactional
    fun workerRecheck(row: CustomDomain, now: Instant): CustomDomain = applyRecheck(row, now)

    /** NFR-10: `suspended` beyond the 30-day hold -> `unverified` + a fresh round. */
    @Transactional
    fun releaseHoldExpired(row: CustomDomain, now: Instant): CustomDomain? {
        val heldAt = row.suspendedAt ?: return null
        if (heldAt.plusSeconds(DAYS_30).isAfter(now)) return null
        val round = challenges.issue(now)
        row.status = CustomDomain.State.UNVERIFIED
        row.suspendedAt = null
        row.suspensionReason = null
        row.challengeHash = round.tokenHash
        row.challengeIssuedAt = round.issuedAt
        row.attemptRound += 1
        row.updatedAt = now
        resetAttempts(row.hostname)
        val saved = domains.saveAndFlush(row)
        audit.append(row.userId, "domain-worker", "domain.hold_expired", row.hostname, "unverified")
        publishState(row.userId, row.hostname, Event.STATE_UNVERIFIED)
        return saved
    }

    /** NFR-10: `unverified` with failing control for 14 days -> released (addable again). */
    @Transactional
    fun autoRelease(row: CustomDomain, now: Instant): Boolean {
        if (row.status != CustomDomain.State.UNVERIFIED || row.lastRecheckOk != false) return false
        val failedSince = row.lastRecheckAt ?: return false
        if (failedSince.plusSeconds(DAYS_14).isAfter(now)) return false
        domains.delete(row)
        audit.append(row.userId, "domain-worker", "domain.auto_release", row.hostname, "released")
        publishState(row.userId, row.hostname, Event.STATE_REMOVED)
        return true
    }

    // ----------------------------------------------------------- resolution

    /**
     * Host resolve (TL 03 serving contract, D-3): verified + public profile +
     * active account + plan pro -> `profile`; anything else `blocked`
     * (identical shape/timing class to `unknown`, NFR-09/BR-11); no binding
     * is `unknown`. Unknown/unverified hosts never touch the profile query
     * path (TL serving contract) - the verified check runs first.
     */
    @Transactional(readOnly = true)
    fun resolveByHost(hostname: String): ResolveOutcome {
        val host = Hostname.of(hostname)
        val row = domains.findByHostname(host.ascii) ?: run {
            resolveUnknown.increment()
            return ResolveOutcome(ResolveOutcome.UNKNOWN, null)
        }
        if (!row.isLive()) {
            resolveBlocked.increment()
            return ResolveOutcome(ResolveOutcome.BLOCKED, null)
        }
        val profile = profiles.findById(row.profileId).orElse(null) ?: run {
            resolveBlocked.increment()
            return ResolveOutcome(ResolveOutcome.BLOCKED, null)
        }
        if (!profile.isPublic || !accountStatus.isActive(profile.userId)) {
            resolveBlocked.increment()
            return ResolveOutcome(ResolveOutcome.BLOCKED, null)
        }
        val username = profile.username.takeIf { it.isNotBlank() }
        if (username == null) {
            // An unclaimed profile (blank username) is unaddressable: the
            // serving path renders the resolved username (BR-04), so a binding
            // without one is blocked, never served under a guessed address.
            resolveBlocked.increment()
            return ResolveOutcome(ResolveOutcome.BLOCKED, null)
        }
        if (!entitlements.isPro(profile.userId)) {
            resolveBlocked.increment()
            return ResolveOutcome(ResolveOutcome.BLOCKED, null)
        }
        return ResolveOutcome(ResolveOutcome.PROFILE, username, row.hostname)
    }

    // ------------------------------------------------------------- reclaim

    /**
     * BR-12 round one: force a fresh challenge round on a (possibly disputed)
     * binding. Support runs with documented evidence; the route is audited and
     * no user identity is minted here - ops never binds or serves as a user.
     */
    @Transactional
    fun reclaimForce(hostname: String, evidenceRef: String, actor: String, now: Instant): RoundResult {
        val host = Hostname.of(hostname)
        val row = domains.findByHostname(host.ascii) ?: throw DomainNotFoundException()
        val round = challenges.issue(now)
        row.challengeHash = round.tokenHash
        row.challengeIssuedAt = round.issuedAt
        row.attemptRound += 1
        row.updatedAt = now
        resetAttempts(row.hostname)
        val saved = domains.saveAndFlush(row)
        audit.append(row.userId, "domain-ops", "domain.reclaim_force", row.hostname, evidenceRef)
        return RoundResult(saved, round.token)
    }

    /** BR-12 round two: claimant proves DNS control; success transfers in one transaction. */
    @Transactional
    fun reclaimAttempt(hostname: String, claimantProfileId: String, evidenceRef: String, actor: String, now: Instant): CustomDomain? {
        val host = Hostname.of(hostname)
        val row = domains.findByHostname(host.ascii) ?: throw DomainNotFoundException()
        val claimant = profiles.findById(claimantProfileId).orElse(null)
            ?: throw DomainNotFoundException()
        val records = verifier.txt(row.txtName())
        if (!challenges.matches(records, row.challengeHash)) {
            audit.append(row.userId, "domain-ops", "domain.reclaim_attempt", row.hostname, "failed")
            return null // BR-12: failure changes nothing for either party
        }
        row.userId = claimant.userId
        row.profileId = claimant.id
        row.status = CustomDomain.State.VERIFIED
        row.verifiedAt = now
        row.suspendedAt = null
        row.suspensionReason = null
        row.lastRecheckAt = now
        row.lastRecheckOk = true
        row.updatedAt = now
        val saved = domains.saveAndFlush(row)
        audit.append(row.userId, "domain-ops", "domain.reclaim_transfer", row.hostname, evidenceRef)
        publishState(row.userId, row.hostname, Event.STATE_VERIFIED)
        return saved
    }

    /** Abuse-review resolution (S-7): clears a phishing suspension; control must re-pass. */
    @Transactional
    fun unsuspend(hostname: String, actor: String, now: Instant): CustomDomain {
        val host = Hostname.of(hostname)
        val row = domains.findByHostname(host.ascii) ?: throw DomainNotFoundException()
        if (row.status != CustomDomain.State.SUSPENDED) return row
        val served = entitlements.planOf(row.userId)
        val txtOk = challenges.matches(verifier.txt(row.txtName()), row.challengeHash)
        if (served == PLAN_PRO && txtOk) {
            row.status = CustomDomain.State.VERIFIED
            row.suspendedAt = null
            row.suspensionReason = null
            row.lastRecheckAt = now
            row.lastRecheckOk = true
            row.updatedAt = now
            val saved = domains.saveAndFlush(row)
            audit.append(row.userId, "domain-ops", "domain.unsuspend", row.hostname, "restored")
            publishState(row.userId, row.hostname, Event.STATE_VERIFIED)
            return saved
        }
        return suspendRow(row, CustomDomain.Suspension.ABUSE, now)
    }

    // ------------------------------------------------------------ denylist

    /** Denylist write (S-4): ops-only, audited; resolve/recheck read it in any app context. */
    @Transactional
    fun denylistAdd(hostnameRaw: String, reason: String, actor: String): DomainDenylist {
        val normalized = Hostname.of(hostnameRaw)
        denylistRepo.findByHostname(normalized.ascii)?.let { return it } // idempotent re-add
        val row = DomainDenylist(
            id = "dnl_" + UUID.randomUUID().toString().take(12),
            hostname = normalized.ascii,
            reason = reason.takeUnless { it.isBlank() } ?: "abuse",
        )
        val saved = denylistRepo.saveAndFlush(row)
        audit.append("domain-ops", actor, "domain.denylist_add", normalized.ascii, saved.reason)
        return saved
    }

    @Transactional
    fun denylistRemove(hostnameRaw: String, actor: String) {
        val normalized = Hostname.of(hostnameRaw)
        val row = denylistRepo.findByHostname(normalized.ascii) ?: throw DomainNotFoundException()
        denylistRepo.delete(row)
        audit.append("domain-ops", actor, "domain.denylist_remove", normalized.ascii, row.reason)
    }

    /** Suffix-pattern denylist matching (Q-01): exact host or any suffix label boundary. */
    fun isDenylisted(normalizedHost: String): Boolean {
        val labels = normalizedHost.split('.')
        for (i in labels.indices) {
            val pattern = if (i == 0) normalizedHost else "*." + labels.subList(i, labels.size).joinToString(".")
            if (denylistRepo.existsByHostname(pattern)) return true
        }
        return false
    }

    // -------------------------------------------------------------- events

    private fun publishState(userId: String, hostname: String, state: String) {
        events.publishAfterCommit(
            DomainEvent(
                type = EVENT_TYPE,
                aggregateId = hostname,
                userId = userId,
                payload = mapOf(Event.HOSTNAME to hostname, Event.STATE to state),
            ),
        )
    }

    // -------------------------------------------------------------- private

    /** Pending/unverified check (round-gated): success verifies, failure records attempts honestly. */
    private fun roundCheck(row: CustomDomain, now: Instant): CustomDomain {
        if (!row.challengeRoundActive(now)) {
            row.updatedAt = now
            return domains.saveAndFlush(row) // expired round: no DNS call; status unchanged (BR-01)
        }
        bumpAttempts(row.hostname)
        val matched = challenges.matches(verifier.txt(row.txtName()), row.challengeHash)
        row.lastRecheckAt = now
        row.lastRecheckOk = matched
        if (matched) {
            row.status = CustomDomain.State.VERIFIED
            row.verifiedAt = row.verifiedAt ?: now
            row.suspendedAt = null
            row.suspensionReason = null
        }
        row.updatedAt = now
        val saved = domains.saveAndFlush(row)
        if (matched) {
            audit.append(row.userId, "domain-owner", "domain.verify", row.hostname, "verified")
            publishState(row.userId, row.hostname, Event.STATE_VERIFIED)
        }
        return saved
    }

    /** The recheck truth table (TL 02); denylist > plan > DNS control (S-4, BR-06, BR-08). */
    private fun applyRecheck(row: CustomDomain, now: Instant): CustomDomain {
        if (isDenylisted(row.hostname)) return suspendRow(row, CustomDomain.Suspension.DENYLIST, now)
        val plan = entitlements.planOf(row.userId)

        if (row.isLive()) {
            if (plan != PLAN_PRO) return suspendRow(row, CustomDomain.Suspension.LAPSE, now)
            val txtOk = challenges.matches(verifier.txt(row.txtName()), row.challengeHash)
            row.lastRecheckAt = now
            row.lastRecheckOk = txtOk
            if (txtOk) {
                row.updatedAt = now
                return domains.saveAndFlush(row)
            }
            row.status = CustomDomain.State.UNVERIFIED
            row.updatedAt = now
            val saved = domains.saveAndFlush(row)
            audit.append(row.userId, "domain-worker", "domain.recheck", row.hostname, "control_lost")
            publishState(row.userId, row.hostname, Event.STATE_UNVERIFIED)
            return saved
        }

        if (row.status == CustomDomain.State.SUSPENDED) {
            if (row.suspensionReason != CustomDomain.Suspension.LAPSE || plan != PLAN_PRO) return row
            val restored = challenges.matches(verifier.txt(row.txtName()), row.challengeHash)
            row.lastRecheckAt = now
            row.lastRecheckOk = restored
            if (restored) {
                row.status = CustomDomain.State.VERIFIED
                row.suspendedAt = null
                row.suspensionReason = null
                row.updatedAt = now
                val saved = domains.saveAndFlush(row)
                audit.append(row.userId, "domain-owner", "domain.restore", row.hostname, "verified")
                publishState(row.userId, row.hostname, Event.STATE_VERIFIED)
                return saved
            }
            row.updatedAt = now
            return domains.saveAndFlush(row)
        }

        // pending/unverified: the round gate applies (full re-verify once stale).
        return roundCheck(row, now)
    }

    private fun suspendRow(row: CustomDomain, reason: String, now: Instant): CustomDomain {
        if (row.status != CustomDomain.State.SUSPENDED || row.suspensionReason != reason) {
            row.suspendedAt = now
            row.suspensionReason = reason
        }
        row.status = CustomDomain.State.SUSPENDED
        row.lastRecheckOk = false
        row.lastRecheckAt = now
        row.updatedAt = now
        val saved = domains.saveAndFlush(row)
        audit.append(row.userId, "domain-tooling", "domain.suspend", row.hostname, reason)
        publishState(row.userId, row.hostname, Event.STATE_SUSPENDED)
        return saved
    }

    private fun bumpAttempts(host: String) {
        try {
            redis.opsForValue().increment(attemptsKey(host))
            redis.expire(attemptsKey(host), Duration.ofSeconds(ROUND_TTL_SECONDS))
        } catch (_: Exception) {
            // cache-only attempt counter
        }
    }

    private fun resetAttempts(host: String) {
        try {
            redis.delete(attemptsKey(host))
        } catch (_: Exception) {
            // cache-only
        }
    }

    private fun attemptsKey(host: String) = "rb:domain:attempts:$host"

    fun attemptsFor(host: String): Int = try {
        redis.opsForValue().get(attemptsKey(host))?.toIntOrNull() ?: 0
    } catch (_: Exception) {
        0
    }

    private fun CustomDomain.txtName(): String = Hostname.CHALLENGE_PREFIX + hostname

    companion object {
        const val EVENT_TYPE = "domain.state_changed.v1"
        const val MAX_HOSTNAMES_PER_ACCOUNT = 10
        const val PLAN_PRO = "pro"
        const val ROUND_TTL_SECONDS = 7L * 24 * 60 * 60
        private const val DAYS_14 = 14L * 24 * 60 * 60
        private const val DAYS_30 = 30L * 24 * 60 * 60

        /** Backoff steps from the round issue (NFR-02: 30 s, 2 m, 5 m, 15 m, 1 h, 6 h). */
        val POLL_BACKOFF_SECONDS = longArrayOf(30L, 120L, 300L, 900L, 3600L, 6 * 3600L)

        /** 24 h +-10% jitter via hash spread; deterministic across worker steps (NFR-03). */
        fun staggerSeconds(hostname: String): Duration {
            val digest = MessageDigest.getInstance("SHA-256").digest(hostname.toByteArray())
            val spread = (digest[0].toInt() and 0xFF) % 48 // 0..47 s (jitter only) - real stagger comes from recheck sweeps
            return Duration.ofSeconds(24L * 60 * 60 - spread)
        }
    }
}

object Event {
    const val HOSTNAME = "hostname"
    const val STATE = "state"
    const val STATE_VERIFIED = "verified"
    const val STATE_SUSPENDED = "suspended"
    const val STATE_UNVERIFIED = "unverified"
    const val STATE_REMOVED = "removed"
}

/** A round issuance: `revealedToken` is the one-time TXT value (never stored, never logged). */
data class RoundResult(val row: CustomDomain, val revealedToken: String?)

/** The internal resolve outcome (TL 03): `profile | blocked | unknown`. */
data class ResolveOutcome(
    val result: String,
    val username: String?,
    val canonicalHost: String? = null,
) {
    companion object {
        const val PROFILE = "profile"
        const val BLOCKED = "blocked"
        const val UNKNOWN = "unknown"
    }
}
