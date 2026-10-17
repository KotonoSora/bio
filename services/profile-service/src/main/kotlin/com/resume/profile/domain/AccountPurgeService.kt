package com.resume.profile.domain

import com.fasterxml.jackson.databind.ObjectMapper
import com.resume.profile.data.ProfileRepository
import com.resume.profile.data.RetiredUsernameRepository
import com.resume.profile.security.RlsUserHolder
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

/** Purge counts returned to the runner (embedded verbatim in the certificate). */
data class ProfilePurgeCounts(val tables: Map<String, Long>, val files: Long)

/** Residual-probe counts (BR-10): honest reads, never deletes. */
data class ProfileResidual(val rows: Map<String, Long>, val files: Long)

/**
 * profile-service purge participant (feature 02 D-3, S-6): tombstone insert and
 * profile delete in one transaction under the server-set `svc` scope with an
 * explicit user predicate - zero cross-user deletes (NFR-09). Idempotent per
 * `runId` (counts cached; the tombstone insert is ON CONFLICT DO NOTHING), and a
 * different active run answers `409 purge_in_progress` (S-3).
 *
 * Avatar files belong to feature 09's pipeline and are not migrated yet: `files`
 * is honestly 0 until that namespace exists (TL 03 counts table).
 */
@Service
class AccountPurgeService(
    private val profiles: ProfileRepository,
    private val retired: RetiredUsernameRepository,
    private val audit: AuditService,
    private val redis: StringRedisTemplate,
    private val mapper: ObjectMapper,
    meters: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val conflicts = meters.counter("deletion.purge.conflict")

    @Transactional
    fun purge(userId: String, runId: String): ProfilePurgeCounts = RlsUserHolder.asService {
        val cacheKey = cacheKey(userId, runId)
        redisGet(cacheKey)?.let { return@asService mapper.readValue(it, ProfilePurgeCounts::class.java) }

        val activeKey = activeKey(userId)
        val active = redisGet(activeKey)
        if (active != null && active != runId) {
            conflicts.increment()
            throw AccountPurgeInProgressException()
        }
        redisSet(activeKey, runId, Duration.ofMinutes(30))
        try {
            val rows = profiles.findByUserId(userId)
            var retiredInserted = 0L
            for (profile in rows) {
                retiredInserted += retired.retireIfAbsent(profile.username).toLong()
            }
            val deleted = profiles.deleteByUserId(userId).toLong()
            val tables = linkedMapOf("profiles" to deleted, "retired_usernames" to retiredInserted)
            val counts = ProfilePurgeCounts(tables = tables, files = 0)
            redisSet(cacheKey, mapper.writeValueAsString(counts), Duration.ofHours(24))
            audit.append(userId, "svc:account-purge", "profile.purge", runId, "ok")
            log.info("profile purge user={} run={} profiles={} retired={}", userId, runId, deleted, retiredInserted)
            counts
        } finally {
            redisDelete(activeKey)
        }
    }

    /** Weekly residual probe (BR-10): profile rows for the account, nothing else. */
    @Transactional(readOnly = true)
    fun probe(userId: String): ProfileResidual = RlsUserHolder.asService {
        val residual = linkedMapOf<String, Long>()
        residual["profiles"] = profiles.countByUserId(userId)
        ProfileResidual(rows = residual, files = 0)
    }

    private fun cacheKey(userId: String, runId: String) = "profile:purge:$userId:$runId"

    private fun activeKey(userId: String) = "profile:purge:active:$userId"

    private fun redisGet(key: String): String? = try {
        redis.opsForValue().get(key)
    } catch (_: Exception) {
        null
    }

    private fun redisSet(key: String, value: String, ttl: Duration) {
        try {
            redis.opsForValue().set(key, value, ttl)
        } catch (_: Exception) {
            // cache-only; the rows are already gone and retries re-run honestly
        }
    }

    private fun redisDelete(key: String) {
        try {
            redis.delete(key)
        } catch (_: Exception) {
            // TTL covers
        }
    }
}

/** 409 `purge_in_progress`: a different run currently holds this account. */
class AccountPurgeInProgressException : RuntimeException("A purge run is already in progress.")
