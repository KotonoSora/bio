package com.resume.account.api

import com.resume.account.domain.ApiException
import com.resume.account.domain.IdentityService
import com.resume.account.domain.RequestFacts
import com.resume.account.domain.RecoveryService
import com.resume.account.domain.SessionService
import com.resume.account.domain.UnlinkOutcome
import com.resume.account.oauth.CallbackOutcome
import com.resume.account.oauth.LinkStart
import com.resume.account.oauth.OAuthFlowService
import com.resume.account.oauth.ProviderCatalog
import com.resume.account.security.AccountRateLimiter
import com.resume.account.security.AuthClaims
import com.resume.account.security.StepUpGuard
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import jakarta.servlet.http.HttpServletRequest

/**
 * Browser-facing auth surface (TL 03 endpoint map). SSO-only: no credential
 * route exists anywhere; replayed/expired state and provider errors collapse to
 * the generic `/auth/error` redirect (BR-04). Form navigation posts are used for
 * flows that hand off to the IdP; JSON ops (unlink/sign-out) are fetch+header.
 * `user_id` always comes from the session claims, never from a parameter (BR-08).
 */
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val oauth: OAuthFlowService,
    private val catalog: ProviderCatalog,
    private val sessions: SessionService,
    private val identities: IdentityService,
    private val recovery: RecoveryService,
    private val limiter: AccountRateLimiter,
    private val stepUp: StepUpGuard,
    private val cookie: SessionCookie,
) {

    @GetMapping("/providers")
    fun providers(): ProviderListResponse = ProviderListResponse(catalog.enabled().map { ProviderView(it.id, it.label) })

    @PostMapping("/sign-in/social")
    fun signIn(
        @RequestParam provider: String = "",
        @RequestParam callbackURL: String = "",
        request: HttpServletRequest,
    ): ResponseEntity<Void> {
        limiter.authIp(RequestFacts.ipClass(request))
        if (provider.isBlank()) throw ApiException.validation("Choose a sign-in method.")
        return Redirects.to(oauth.startLogin(provider.lowercase(), callbackURL))
    }

    @GetMapping("/callback/{provider}")
    fun callbackGet(
        @PathVariable provider: String,
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
        request: HttpServletRequest,
    ): ResponseEntity<Void> = complete(provider, code, state, request)

    /** Apple returns `form_post`; both methods land on the same validated completion. */
    @PostMapping("/callback/{provider}")
    fun callbackPost(
        @PathVariable provider: String,
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
        request: HttpServletRequest,
    ): ResponseEntity<Void> = complete(provider, code, state, request)

    private fun complete(provider: String, code: String?, state: String?, request: HttpServletRequest): ResponseEntity<Void> {
        limiter.authIp(RequestFacts.ipClass(request))
        val outcome = oauth.callback(
            provider = provider.lowercase(),
            code = code,
            state = state,
            deviceLabel = RequestFacts.deviceLabel(request),
            ipClass = RequestFacts.ipClass(request),
        )
        return when (outcome) {
            is CallbackOutcome.Success -> {
                val builder = ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, outcome.redirectTo)
                outcome.sid?.let { builder.header(HttpHeaders.SET_COOKIE, cookie.setHeader(it)) }
                builder.build()
            }
            CallbackOutcome.Failed -> Redirects.authError()
        }
    }

    /** Re-auth initiation for stale sensitive actions (BR-07); returns to the interrupted action. */
    @PostMapping("/step-up")
    fun stepUp(
        @RequestParam provider: String = "",
        @RequestParam returnTo: String = "/app",
        request: HttpServletRequest,
    ): ResponseEntity<Void> {
        val (userId, sid) = AuthClaims.requireSession()
        limiter.userAction(userId, "stepup")
        if (provider.isBlank()) throw ApiException.validation("Choose a sign-in method.")
        return Redirects.to(oauth.startStepUp(userId, sid, provider.lowercase(), returnTo))
    }

    @PostMapping("/link/{provider}")
    fun link(
        @PathVariable provider: String,
        @RequestParam returnTo: String = "/app/account/settings",
    ): ResponseEntity<Void> {
        val (userId, _) = AuthClaims.requireSession()
        limiter.userAction(userId, "stepup")
        stepUp.requireFresh()
        return when (val start = oauth.startLink(userId, provider.lowercase(), returnTo)) {
            is LinkStart.Redirect -> Redirects.to(start.url)
            LinkStart.AlreadyLinked -> throw ApiException.conflict("already_linked", "This sign-in method is already connected.")
        }
    }

    /** Fresh step-up required (BR-05); the last remaining provider can never be unlinked. */
    @PostMapping("/unlink/{provider}")
    fun unlink(@PathVariable provider: String, request: HttpServletRequest): ResponseEntity<Void> {
        val (userId, _) = AuthClaims.requireSession()
        limiter.userAction(userId, "stepup")
        stepUp.requireFresh()
        return when (identities.unlink(userId, provider.lowercase(), RequestFacts.ipClass(request))) {
            UnlinkOutcome.Unlinked -> ResponseEntity.noContent().build()
            UnlinkOutcome.NotLinked -> throw ApiException.notFound("not_linked", "That sign-in method isn't connected.")
            UnlinkOutcome.LastLink -> throw ApiException.conflict(
                "last_link_forbidden",
                "You need at least one sign-in method. Link another first.",
            )
        }
    }

    @PostMapping("/sign-out")
    fun signOut(request: HttpServletRequest): ResponseEntity<Void> {
        val (userId, sid) = AuthClaims.requireSession()
        sessions.logout(sid, userId, RequestFacts.ipClass(request))
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.clearHeader()).build()
    }

    /** Recovery landing: expired/reused/unknown tokens get the generic error page (BR-12). */
    @GetMapping("/recovery/{token}")
    fun recoveryLanding(@PathVariable token: String): ResponseEntity<Void> =
        if (recovery.inspect(token) != null) Redirects.to("/app/recovery/$token") else Redirects.authError()

    @PostMapping("/recovery/{token}/link/{provider}")
    fun recoveryLink(
        @PathVariable token: String,
        @PathVariable provider: String,
        request: HttpServletRequest,
    ): ResponseEntity<Void> {
        limiter.authIp(RequestFacts.ipClass(request))
        val url = oauth.startRecovery(token, provider.lowercase()) ?: return Redirects.authError()
        return Redirects.to(url)
    }
}
