package com.resume.account.domain

import com.fasterxml.jackson.databind.ObjectMapper
import com.resume.account.api.PurgeProbeResponse
import com.resume.account.api.PurgeResponse
import com.resume.account.data.LinkedAccountRepository
import com.resume.account.data.SessionRepository
import com.resume.account.data.UserRepository
import com.resume.account.security.RlsContext
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

/**
 * account-service's own purge participant (TL 03 roster): hard-deletes
 * `users`, `linked_accounts`, `sessions` (feature 01) under the server-set
 * `svc` RLS scope with an explicit user predicate - zero cross-user deletes
 * (NFR-09). Idempotent per `runId`: counts are cached after full success, so a
 * retry returns identical numbers (NFR-06). A different active run for the
 * same account answers `409 purge_in_progress` (S-3).
 */
@Service
class AccountPurgeHandler(
    private val users: UserRepository,
    private val links: LinkedAccountRepository,
    private val sessions: SessionRepository,
    private val redis: StringRedisTemplate,
    private val mapper: ObjectMapper,
    meters: MeterRegistry,
) {
    private val conflicts = meters.counter("deletion.purge.conflict")

    @Transactional
    fun purge(userId: String, runId: String): PurgeResponse = RlsContext.asService {
        val cacheKey = cacheKey(userId, runId)
        redis.opsForValue().get(cacheKey)?.let {
            return@asService mapper.readValue(it, PurgeResponse::class.java)
        }
        val activeKey = "acct:purge:active:$userId"
        val active = redis.opsForValue().get(activeKey)
        if (active != null && active != runId) {
            conflicts.increment()
            throw ApiException.purgeInProgress()
        }
        redis.opsForValue().setIfAbsent(activeKey, runId, Duration.ofMinutes(30))
        try {
            val tables = linkedMapOf<String, Long>()
            tables["sessions"] = sessions.deleteByUserId(userId).toLong()
            tables["linked_accounts"] = links.deleteByUserId(userId).toLong()
            tables["users"] = users.deleteByUserId(userId).toLong()
            val response = PurgeResponse(runId = runId, tables = tables, files = 0)
            redis.opsForValue().set(cacheKey, mapper.writeValueAsString(response), Duration.ofHours(24))
            return@asService response
        } finally {
            redis.delete(activeKey)
        }
    }

    /** Weekly residual probe (BR-10): honest counts, never a delete. */
    @Transactional(readOnly = true)
    fun probe(userId: String): PurgeProbeResponse = RlsContext.asService {
        val residual = linkedMapOf<String, Long>()
        residual["users"] = if (users.existsById(userId)) 1 else 0
        residual["linked_accounts"] = links.countByUserId(userId)
        residual["sessions"] = sessions.countByUserId(userId)
        PurgeProbeResponse(userId = userId, residualRows = residual, residualFiles = 0)
    }

    private fun cacheKey(userId: String, runId: String) = "acct:purge:$userId:$runId"
}
