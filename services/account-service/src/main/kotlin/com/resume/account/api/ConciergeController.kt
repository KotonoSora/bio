package com.resume.account.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.resume.account.domain.concierge.ConciergeApprovalService
import com.resume.account.domain.concierge.ConciergeContractService
import com.resume.account.domain.concierge.ConciergeRateLimiter
import com.resume.account.domain.concierge.ConciergeTaskBoard
import com.resume.account.domain.concierge.ConciergeViews
import com.resume.account.security.AuthClaims
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Concierge dashboard API (TL 03 "Client endpoints"). Identity and scope come
 * from the session claims only - `user_id` is never accepted from the client
 * (BR-08); rows resolve under the owner RLS predicate plus explicit predicates.
 * CSRF is the edge double-submit (S-6); envelopes live in ConciergeRateLimiter.
 */
@RestController
@RequestMapping("/api/v1/concierge")
class ConciergeController(
    private val contracts: ConciergeContractService,
    private val board: ConciergeTaskBoard,
    private val approvals: ConciergeApprovalService,
    private val views: ConciergeViews,
    private val limiter: ConciergeRateLimiter,
    private val mapper: ObjectMapper,
) {

    /**
     * TL 03: `200 ContractResponse` (`null` when no draft). The literal JSON
     * `null` body is the contract - an empty body would break every consumer.
     */
    @GetMapping
    fun contract(): ResponseEntity<String> {
        val (userId, _) = AuthClaims.requireSession()
        val view = views.clientContract(userId)
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapper.writeValueAsString(view))
    }

    @GetMapping("/tasks")
    fun tasks(): List<TaskResponse> {
        val (userId, _) = AuthClaims.requireSession()
        return views.clientTasks(userId)
    }

    @PostMapping("/tasks")
    fun requestTask(@RequestBody body: TaskRequest): ResponseEntity<TaskResponse> {
        val (userId, _) = AuthClaims.requireSession()
        val task = board.request(userId, body.kind.trim(), body.notes)
        return ResponseEntity.status(HttpStatus.CREATED).body(task)
    }

    @PostMapping("/tasks/{id}/approve")
    fun approveTask(@PathVariable id: String, @RequestBody body: ApproveTaskRequest): ApprovalResponse {
        val (userId, _) = AuthClaims.requireSession()
        limiter.clientAction(userId)
        return board.approve(userId, id, body.scope)
    }

    /**
     * ADDITIVE route (BA 03 step 2 / BA 04 "Setup-plan approval"): the TL client
     * list had no route for the contract-level approval that gates activation.
     * Draft-only; re-approve returns the recorded row (immutable, BA 05).
     */
    @PostMapping("/approve")
    fun approveSetupPlan(@RequestBody body: ApproveTaskRequest): ApprovalResponse {
        val (userId, _) = AuthClaims.requireSession()
        limiter.clientAction(userId)
        return approvals.approveSetupPlan(userId, body.scope)
    }

    /** 202 while the counterpart must confirm; 200 when this call *was* the confirmation. */
    @PostMapping("/pause")
    fun pause(@RequestBody(required = false) body: ExitDecisionRequest?): ResponseEntity<ContractResponse> {
        val (userId, _) = AuthClaims.requireSession()
        limiter.clientAction(userId)
        val outcome = contracts.clientTransition(userId, "pause", null, null)
        val response = outcome.contract
        return if (outcome.committed) ResponseEntity.ok(response) else ResponseEntity.accepted().body(response)
    }

    @PostMapping("/exit")
    fun exit(@RequestBody(required = false) body: ExitDecisionRequest?): ResponseEntity<ContractResponse> {
        val (userId, _) = AuthClaims.requireSession()
        limiter.clientAction(userId)
        val outcome = contracts.clientTransition(userId, "exit", body?.scheduledWork, body?.acknowledge)
        val response = outcome.contract
        return if (outcome.committed) ResponseEntity.ok(response) else ResponseEntity.accepted().body(response)
    }

    @DeleteMapping("/transition")
    fun cancelTransition(): ResponseEntity<Void> {
        val (userId, _) = AuthClaims.requireSession()
        contracts.clientCancel(userId)
        return ResponseEntity.noContent().build()
    }
}
