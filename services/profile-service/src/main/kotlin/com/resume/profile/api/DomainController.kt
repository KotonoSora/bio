package com.resume.profile.api

import com.resume.profile.domain.domains.CustomDomain
import com.resume.profile.domain.domains.DomainsService
import com.resume.profile.domain.domains.Hostname
import com.resume.profile.domain.domains.RoundResult
import com.resume.profile.security.DomainRateLimiter
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class AddDomainRequest(val hostname: String = "")

/**
 * TL 03 owner DTO mirror (names frozen, additive-only). `txtValue` reveals a
 * round's token exactly once (S-2); `challengeExpired` mirrors BA 05's
 * pending-expiry fact; `attempts` is the honest attempt counter.
 */
data class DomainResponse(
    val hostname: String,
    val displayHost: String,
    val status: String,
    val txtName: String? = null,
    val txtValue: String? = null,
    val challengeExpiresAt: Instant? = null,
    val verifiedAt: Instant? = null,
    val lastRecheckedAt: Instant? = null,
    val serving: Boolean,
    val challengeExpired: Boolean = false,
    val attempts: Int = 0,
)

/**
 * Owner domain CRUD (TL 03 endpoint map; session-scoped by the gateway JWT):
 * `user_id` is server-set from the identity claim and re-scoped by RLS plus
 * explicit predicates - request bodies never carry it (BR-11). Rate
 * envelopes ride the feature limiter (NFR-08); codes are the TL 03 catalog.
 */
@RestController
@RequestMapping("/api/v1/domains")
class DomainController(
    private val domains: DomainsService,
    private val limiter: DomainRateLimiter,
) {
    @GetMapping
    fun list(auth: Authentication?): List<DomainResponse> {
        val userId = require(auth)
        requireRate(limiter.consumeListPerMinute(userId))
        val pro = domains.isPro(userId)
        return domains.list(userId).map { toResponse(it, pro, revealedToken = null) }
    }

    @PostMapping
    fun add(auth: Authentication?, @RequestBody body: AddDomainRequest): ResponseEntity<DomainResponse> {
        val userId = require(auth)
        requireRate(limiter.consumeAddDaily(userId))
        val result: RoundResult = domains.add(userId, body.hostname, Instant.now())
        val body0 = toResponse(result.row, pro = domains.isPro(userId), revealedToken = result.revealedToken)
        return ResponseEntity.status(HttpStatus.CREATED).body(body0)
    }

    @PostMapping("/{host}/verify")
    fun verify(auth: Authentication?, @PathVariable host: String): DomainResponse {
        val userId = require(auth)
        requireRate(limiter.consumeVerifyPerMinute(userId))
        return toResponse(
            domains.onDemandCheck(userId, host, Instant.now()),
            pro = domains.isPro(userId),
            revealedToken = null,
        )
    }

    @PostMapping("/{host}/recheck")
    fun recheck(auth: Authentication?, @PathVariable host: String): DomainResponse {
        val userId = require(auth)
        requireRate(limiter.consumeVerifyPerMinute(userId))
        return toResponse(
            domains.recheck(userId, host, Instant.now()),
            pro = domains.isPro(userId),
            revealedToken = null,
        )
    }

    @PostMapping("/{host}/reissue")
    fun reissue(auth: Authentication?, @PathVariable host: String): DomainResponse {
        val userId = require(auth)
        requireRate(limiter.consumeVerifyPerMinute(userId))
        val result = domains.reissueChallenge(userId, host, Instant.now())
        return toResponse(result.row, pro = domains.isPro(userId), revealedToken = result.revealedToken)
    }

    @DeleteMapping("/{host}")
    fun remove(auth: Authentication?, @PathVariable host: String): ResponseEntity<Void> {
        val userId = require(auth)
        requireRate(limiter.consumeVerifyPerMinute(userId))
        domains.remove(userId, host)
        return ResponseEntity.noContent().build()
    }

    private fun require(auth: Authentication?): String =
        auth?.name ?: throw IllegalStateException("Authentication required.")

    private fun requireRate(state: DomainRateLimiter.RetryState) {
        if (!state.allowed) throw DomainRateLimiter.ThrottleException(state.seconds ?: 60)
    }

    private fun toResponse(row: CustomDomain, pro: Boolean, revealedToken: String?): DomainResponse {
        val now = Instant.now()
        val live = row.status == CustomDomain.State.VERIFIED
        val roundActive = row.challengeRoundActive(now)
        return DomainResponse(
            hostname = row.hostname,
            displayHost = row.displayHost,
            status = row.status,
            txtName = if (!live) Hostname.CHALLENGE_PREFIX + row.hostname else null,
            txtValue = if (!live && roundActive) revealedToken else null,
            challengeExpiresAt = if (live) null else row.challengeIssuedAt.plusSeconds(CustomDomain.ROUND_TTL_SECONDS),
            verifiedAt = row.verifiedAt,
            lastRecheckedAt = row.lastRecheckAt,
            serving = live && pro,
            challengeExpired = if (live) false else !roundActive,
            attempts = if (live) 0 else domains.attemptsFor(row.hostname),
        )
    }
}
