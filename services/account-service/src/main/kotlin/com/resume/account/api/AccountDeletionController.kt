package com.resume.account.api

import com.resume.account.domain.DeletionService
import com.resume.account.domain.RequestFacts
import com.resume.account.security.AccountRateLimiter
import com.resume.account.security.AuthClaims
import com.resume.account.security.StepUpGuard
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Owner deletion surface (TL 03). Identity comes from session claims only
 * (BR-08); CSRF is enforced at the edge double-submit (S-6). `delete-request`
 * requires a fresh step-up (S-1) plus the exact typed confirmation (BR-01) and
 * revokes every session on commit, the caller's included (BR-02) - the client
 * treats that 200 as final and shows the signed-out state. `delete-cancel` is
 * the restore path: login + one controlled click, no step-up (BR-03).
 */
@RestController
@RequestMapping("/api/v1/account")
class AccountDeletionController(
    private val deletions: DeletionService,
    private val limiter: AccountRateLimiter,
    private val stepUp: StepUpGuard,
) {

    @PostMapping("/delete-request")
    fun request(@RequestBody body: DeleteRequest, request: HttpServletRequest): DeleteStatusResponse {
        val (userId, _) = AuthClaims.requireSession()
        limiter.deletionAction(userId)
        stepUp.requireFresh()
        return deletions.request(userId, body.confirmation, RequestFacts.ipClass(request))
    }

    @PostMapping("/delete-cancel")
    fun cancel(request: HttpServletRequest): DeleteStatusResponse {
        val (userId, _) = AuthClaims.requireSession()
        limiter.deletionAction(userId)
        return deletions.cancel(userId, RequestFacts.ipClass(request))
    }

    @GetMapping("/delete-status")
    fun status(): DeleteStatusResponse {
        val (userId, _) = AuthClaims.requireSession()
        limiter.deletionStatus(userId)
        return deletions.status(userId)
    }
}
