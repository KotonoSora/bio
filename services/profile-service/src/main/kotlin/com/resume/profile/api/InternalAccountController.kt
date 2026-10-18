package com.resume.profile.api

import com.resume.profile.domain.AccountPurgeService
import com.resume.profile.security.RlsUserHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class AccountPurgeRequest(val userId: String = "", val runId: String = "")

data class AccountPurgeResponse(val runId: String, val tables: Map<String, Long>, val files: Long)

data class AccountPurgeProbeResponse(val userId: String, val residualRows: Map<String, Long>, val residualFiles: Long)

/** Malformed purge bodies are 422 `validation_failed`, never a silent no-op. */
class PurgeRequestValidationException(message: String) : RuntimeException(message)

/**
 * Platform purge participant contract (feature 02 D-1; mesh-only, service JWT):
 * `POST /internal/v1/account/purge` + `GET /internal/v1/account/purge-probe/{userId}`.
 * The handler owns its SQL and RLS scope; the controller only shapes the DTOs.
 * The `svc` scope wraps the service call OUTSIDE the transaction boundary - the
 * transaction binds its connection at begin, so a scope set inside would arrive
 * too late (deny-by-default would silently filter every statement).
 */
@RestController
@RequestMapping("/internal/v1/account")
class InternalAccountController(private val purge: AccountPurgeService) {

    @PostMapping("/purge")
    fun purge(@RequestBody body: AccountPurgeRequest): AccountPurgeResponse {
        if (body.userId.isBlank() || body.runId.isBlank()) {
            throw PurgeRequestValidationException("userId and runId are required.")
        }
        val counts = RlsUserHolder.asService { purge.purge(body.userId, body.runId) }
        return AccountPurgeResponse(runId = body.runId, tables = counts.tables, files = counts.files)
    }

    @GetMapping("/purge-probe/{userId}")
    fun probe(@PathVariable userId: String): AccountPurgeProbeResponse {
        val residual = RlsUserHolder.asService { purge.probe(userId) }
        return AccountPurgeProbeResponse(userId = userId, residualRows = residual.rows, residualFiles = residual.files)
    }
}
