package com.resume.account.api

import com.resume.account.domain.ApiException
import com.resume.account.domain.ContactEmailService
import com.resume.account.domain.IdentityService
import com.resume.account.domain.RequestFacts
import com.resume.account.domain.SessionService
import com.resume.account.domain.UserView
import com.resume.account.security.AccountRateLimiter
import com.resume.account.security.AuthClaims
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Owner API (TL 03). Identity and scope come from the session claims only
 * (BR-08): `user_id` is never accepted from the client, foreign rows resolve to
 * zero rows via RLS plus explicit predicates. Masks are storage-level - raw IPs
 * and user-agents do not exist here (NFR-12).
 */
@RestController
@RequestMapping("/api/v1/me")
class MeController(
    private val users: IdentityService,
    private val sessions: SessionService,
    private val contactEmail: ContactEmailService,
    private val limiter: AccountRateLimiter,
) {

    @GetMapping
    fun me(): MeResponse {
        val (userId, _) = AuthClaims.requireSession()
        limiter.ownerRead(userId)
        return view(users.view(userId) ?: throw ApiException.unauthenticated())
    }

    @GetMapping("/sessions")
    fun sessions(): SessionListResponse {
        val (userId, sid) = AuthClaims.requireSession()
        limiter.ownerRead(userId)
        val list = sessions.activeSessions(userId).map {
            SessionSummary(
                sid = it.sid,
                current = it.sid == sid,
                deviceLabel = it.deviceLabel,
                ipClass = it.ipClass,
                createdAt = it.createdAt,
                lastActiveAt = it.lastActiveAt,
            )
        }
        return SessionListResponse(list)
    }

    @DeleteMapping("/sessions/{sid}")
    fun revoke(@PathVariable sid: String): ResponseEntity<Void> {
        val (userId, _) = AuthClaims.requireSession()
        limiter.ownerRead(userId)
        val revoked = sessions.revoke(sid, userId)
        if (!revoked) throw ApiException.notFound("session_not_found", "That session no longer exists.")
        return ResponseEntity.noContent().build()
    }

    /** Revoke-all-others: the current session always survives (BR-06). */
    @DeleteMapping("/sessions")
    fun revokeOthers(request: HttpServletRequest): ResponseEntity<Void> {
        val (userId, sid) = AuthClaims.requireSession()
        limiter.ownerRead(userId)
        sessions.revokeOthers(userId, sid, RequestFacts.ipClass(request))
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/contact-email")
    fun setContactEmail(
        @Valid @RequestBody body: ContactEmailRequest,
        request: HttpServletRequest,
    ): ResponseEntity<Void> {
        val (userId, _) = AuthClaims.requireSession()
        limiter.ownerRead(userId)
        contactEmail.send(userId, body.email, RequestFacts.ipClass(request))
        return ResponseEntity.status(HttpStatus.ACCEPTED).build()
    }

    @PostMapping("/contact-email/verify")
    fun verifyContactEmail(
        @Valid @RequestBody body: ContactEmailVerifyRequest,
        request: HttpServletRequest,
    ): MeResponse {
        val (userId, _) = AuthClaims.requireSession()
        limiter.ownerRead(userId)
        contactEmail.verify(userId, body.code, RequestFacts.ipClass(request))
        return view(users.view(userId) ?: throw ApiException.unauthenticated())
    }

    private fun view(user: UserView): MeResponse = MeResponse(
        id = user.id,
        name = user.name,
        email = user.email,
        emailVerified = user.emailVerified,
        emailConflict = user.emailConflict,
        contactEmail = user.contactEmail,
        contactVerified = user.contactVerified,
        imageKey = user.imageKey,
        createdAt = user.createdAt,
    )
}
