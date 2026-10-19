package com.resume.profile.domain.domains

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Binding aggregate root (feature 04 D-2): one hostname -> one profile.
 * The state machine is enforced by the application service (TL 02
 * transitions table); serving reads one state: [isServeReady] - a binding
 * serves ONLY in `verified`, and only when the plan gate passes (BR-06) -
 * never under any other state, ever (S-1).
 */
@Entity
@Table(name = "custom_domains")
class CustomDomain(
    @Id var id: String = "",
    @Column(nullable = false, unique = true) var hostname: String = "",
    @Column(name = "display_host", nullable = false) var displayHost: String = "",
    @Column(name = "profile_id", nullable = false) var profileId: String = "",
    @Column(name = "user_id", nullable = false) var userId: String = "",
    @Column(nullable = false) var status: String = State.PENDING,
    @Column(name = "challenge_hash", nullable = false) var challengeHash: String = "",
    @Column(name = "challenge_issued_at", nullable = false) var challengeIssuedAt: Instant = Instant.now(),
    @Column(name = "attempt_round", nullable = false) var attemptRound: Int = 1,
    @Column(name = "verified_at") var verifiedAt: Instant? = null,
    @Column(name = "last_recheck_at") var lastRecheckAt: Instant? = null,
    @Column(name = "last_recheck_ok") var lastRecheckOk: Boolean? = null,
    @Column(name = "suspended_at") var suspendedAt: Instant? = null,
    @Column(name = "suspension_reason") var suspensionReason: String? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
) {
    object State {
        const val PENDING = "pending"
        const val VERIFIED = "verified"
        const val UNVERIFIED = "unverified"
        const val SUSPENDED = "suspended"
    }

    object Suspension {
        const val LAPSE = "entitlement_lapsed"
        const val ABUSE = "abuse_phishing_review"
        const val DENYLIST = "denylist_hit"
    }

    /** S-1: one serving gate over the whole feature. */
    fun isServeReady(planetOk: Boolean): Boolean =
        status == State.VERIFIED && planetOk

    fun isLive(): Boolean = status == State.VERIFIED

    /** The record the owner sets: `_rb-challenge.<host>` -> `rb-verify=<token>`. */
    fun challengeRoundActive(now: Instant): Boolean = challengeIssuedAt.plusSeconds(ROUND_TTL_SECONDS).isAfter(now)

    override fun toString(): String = "CustomDomain($hostname, status=$status, round=$attemptRound)"

    companion object {
        const val ROUND_TTL_SECONDS = 7L * 24 * 60 * 60
    }
}
