package com.resume.account.api

import com.resume.account.domain.ApiException
import com.resume.account.domain.EntitlementService
import com.resume.account.domain.IdentityService
import com.resume.account.domain.RecoveryService
import com.resume.account.domain.RequestFacts
import com.resume.account.domain.SessionService
import com.resume.account.security.AuthClaims
import com.resume.account.security.RlsContext
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Mesh-only internal API (service JWT; the public edge never routes here).
 * Handlers run under an explicit server-set RLS context (`svc` for cross-user
 * reads, `ops` for the audited recovery route) and each applies its own
 * equality predicate - the context alone is never an authorization (BR-08).
 */
@RestController
@RequestMapping("/internal/v1")
class InternalAccountController(
    private val entitlements: EntitlementService,
    private val identities: IdentityService,
    private val sessions: SessionService,
    private val recovery: RecoveryService,
) {

    @GetMapping("/entitlements/{userId}")
    fun entitlementSnapshot(@PathVariable userId: String): EntitlementSnapshotResponse =
        RlsContext.asService {
            val snapshot = entitlements.snapshot(userId)
            EntitlementSnapshotResponse(snapshot.userId, snapshot.plan, snapshot.capabilities, snapshot.resolvedAt)
        }

    @GetMapping("/users/{userId}/email")
    fun userEmail(@PathVariable userId: String): EmailView = RlsContext.asService {
        val user = identities.emailView(userId) ?: throw ApiException.notFound("user_absent", "No such account.")
        EmailView(user.email, user.emailVerified)
    }

    /** Edge rebuild path (D-2): registry truth for a session id, never a grant on its own. */
    @GetMapping("/sessions/{sid}/introspect")
    fun introspect(@PathVariable sid: String): SessionIntrospectResponse {
        requireGatewayCaller()
        val session = RlsContext.asService { sessions.introspect(sid) }
            ?: throw ApiException.notFound("session_not_found", "That session no longer exists.")
        return SessionIntrospectResponse(session.userId, session.authTime, session.absoluteExpiresAt)
    }

    /** Ops-triggered recovery invitation (BR-12): token only, audited with the evidence ref. */
    @PostMapping("/account/recovery-link")
    fun recoveryLink(
        @RequestBody body: RecoveryLinkRequest,
        request: HttpServletRequest,
    ): ResponseEntity<RecoveryLinkResponse> {
        val (token, expiresAt) = RlsContext.asOps {
            recovery.create(body.userId, body.evidenceRef, RequestFacts.ipClass(request))
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(RecoveryLinkResponse(token, expiresAt))
    }

    private fun requireGatewayCaller() {
        val subject = AuthClaims.subject() ?: return // dev-mode empty-boot path
        if (subject != "edge:gateway") throw ApiException.unauthenticated()
    }
}
