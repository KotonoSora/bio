package com.resume.profile.api

import com.resume.profile.domain.domains.CustomDomain
import com.resume.profile.domain.domains.DomainsService
import com.resume.profile.domain.domains.Hostname
import com.resume.profile.domain.domains.ResolveOutcome
import com.resume.profile.security.RlsUserHolder
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

data class ResolveResponse(
    val result: String,
    val username: String? = null,
    val canonicalHost: String? = null,
)

data class DomainStatusView(val hostname: String, val status: String)

data class ReclaimRequest(val evidenceRef: String = "", val claimantProfileId: String = "")

/** TL 03 frozen shape + additive claimant-reveal fields (BR-12 round one). */
data class ReclaimResponse(
    val hostname: String,
    val challengeIssuedAt: Instant,
    val expiresAt: Instant,
    val txtName: String? = null,
    val txtValue: String? = null,
)

data class ReclaimAttemptResponse(val hostname: String, val transferred: Boolean)

data class DenylistRequest(val hostname: String = "", val reason: String = "")

/**
 * Mesh-only domain API (TL 03 internal map; service JWT; the public edge
 * never routes here):
 *  - resolve (`GET /{host}`): the serving decision for the gateway (D-3);
 *  - `by-user`: feature 20 share derivation;
 *  - reclaim: the audited ops dispute round (BR-12);
 *  - denylist: the ops-maintained list (S-4).
 * Resolve/reclaim run under the server-set `svc` scope with explicit
 * predicates - the context alone is never an authorization (baseline 08).
 */
@RestController
@RequestMapping("/internal/v1/domains")
class InternalDomainController(
    private val domains: DomainsService,
) {
    @GetMapping("/{host}")
    fun resolve(@PathVariable host: String): ResolveResponse = RlsUserHolder.asService {
        val outcome = domains.resolveByHost(host)
        ResolveResponse(
            result = outcome.result,
            username = outcome.username?.takeIf { outcome.result == ResolveOutcome.PROFILE },
            canonicalHost = outcome.canonicalHost?.takeIf { outcome.result == ResolveOutcome.PROFILE },
        )
    }

    @GetMapping("/by-user/{userId}")
    fun byUser(@PathVariable userId: String): List<DomainStatusView> = RlsUserHolder.asService {
        domains.list(userId).map { DomainStatusView(it.hostname, it.status) }
    }

    /** BR-12: dispute round one (force fresh challenge, 202) or two (claimant verifies, 200). */
    @PostMapping("/{host}/reclaim")
    fun reclaim(
        @PathVariable host: String,
        @RequestBody body: ReclaimRequest,
        auth: Authentication?,
    ): Any {
        if (body.evidenceRef.isBlank()) throw PurgeRequestValidationException("evidenceRef is required.")
        val actor = auth?.name ?: "svc:unknown"
        val outcome: Any = RlsUserHolder.asService {
            if (body.claimantProfileId.isNotBlank()) {
                // Round two: an open round's TXT verification transfers ownership
                // to the claimant (AC-11); a failing round changes nothing (BR-12).
                val transferred: CustomDomain? =
                    domains.reclaimAttempt(host, body.claimantProfileId, body.evidenceRef, actor, Instant.now())
                ResponseEntity.ok(ReclaimAttemptResponse(transferred?.hostname ?: host, transferred != null))
            } else {
                val fresh = domains.reclaimForce(host, body.evidenceRef, actor, Instant.now())
                ResponseEntity.status(HttpStatus.ACCEPTED).body(
                    ReclaimResponse(
                        hostname = fresh.row.hostname,
                        challengeIssuedAt = fresh.row.challengeIssuedAt,
                        expiresAt = fresh.row.challengeIssuedAt.plusSeconds(CustomDomain.ROUND_TTL_SECONDS),
                        txtName = Hostname.CHALLENGE_PREFIX + fresh.row.hostname,
                        txtValue = fresh.revealedToken, // one-time reveal for the claimant's DNS edit (S-2)
                    ),
                )
            }
        }
        return outcome
    }

    /** S-7 resolution route: clear an abuse suspension after review. */
    @PostMapping("/{host}/unsuspend")
    fun unsuspend(@PathVariable host: String, auth: Authentication?): Map<String, Any> = RlsUserHolder.asService {
        val saved = domains.unsuspend(host, auth?.name ?: "svc:unknown", Instant.now())
        mapOf("hostname" to saved.hostname, "status" to saved.status)
    }

    @PostMapping("/denylist")
    fun denylistAdd(@RequestBody body: DenylistRequest, auth: Authentication?): Map<String, Any?> {
        if (body.hostname.isBlank()) throw PurgeRequestValidationException("hostname is required.")
        return RlsUserHolder.asService {
            val saved = domains.denylistAdd(body.hostname, body.reason, auth?.name ?: "svc:unknown")
            mapOf("hostname" to saved.hostname, "reason" to saved.reason)
        }
    }

    @DeleteMapping("/denylist/{host}")
    fun denylistRemove(@PathVariable host: String, auth: Authentication?): ResponseEntity<Void> {
        RlsUserHolder.asService { domains.denylistRemove(host, auth?.name ?: "svc:unknown") }
        return ResponseEntity.noContent().build()
    }
}
