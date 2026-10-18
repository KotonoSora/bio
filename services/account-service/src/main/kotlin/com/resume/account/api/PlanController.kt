package com.resume.account.api

import com.resume.account.domain.PlanService
import com.resume.account.security.AccountRateLimiter
import com.resume.account.security.AuthClaims
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Feature 03 owner read (TL 03): `GET /api/v1/plan` - one read for the account
 * plan panel. Identity comes from the session claims only (BR-08); client plan
 * flags are never trusted (BR-09, S-1). Per-user and never shared: the response
 * is `private, max-age=0` (TL 01 cache posture), with the 30 s snapshot bound
 * honored inside the service.
 */
@RestController
class PlanController(
    private val plans: PlanService,
    private val limiter: AccountRateLimiter,
) {

    @GetMapping("/api/v1/plan")
    fun plan(): ResponseEntity<PlanResponse> {
        val (userId, _) = AuthClaims.requireSession()
        // 60 req/min/user (NFR-07; same owner-read class as /api/v1/me).
        limiter.ownerRead(userId)
        return ResponseEntity.ok()
            .header("Cache-Control", "private, max-age=0")
            .body(plans.plan(userId))
    }
}
