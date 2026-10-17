package com.resume.analytics.domain

import com.fasterxml.jackson.databind.ObjectMapper
import com.resume.analytics.security.RlsUserHolder
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

/** Purge counts returned to the runner (embedded verbatim in the certificate). */
data class AnalyticsPurgeCounts(val tables: Map<String, Long>, val files: Long)

/** Residual-probe counts (BR-10): honest reads, never deletes. */
data class AnalyticsResidual(val rows: Map<String, Long>, val files: Long)

/**
 * analytics-service purge participant (feature 02 D-1): owner-scoped rows by
 * `user_id` under the server-set svc scope - zero cross-user deletes (NFR-09).
 * Idempotent per `runId` (counts cached after full success); a different active
 * run answers `409 purge_in_progress` (S-3). Day buckets, click rows, owner
 * rollups, and link-hit aggregates arrive with features 12/31 and register here;
 * `hourly_rollup` is the migrated table today. Realtime marker keys (`rt:*`)
 * are minute-keyed with a 65-min TTL - eviction is a correct zero (never a
 * delete path); username-keyed marker cleanup extends with feature 12.
 */
@Service
class AccountPurgeService(
    private val db: JdbcTemplate,
    private val audit: AuditService,
    private val redis: StringRedisTemplate,
    private val mapper: ObjectMapper,
    meters: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val conflicts = meters.counter("deletion.purge.conflict")

    @Transactional
    fun purge(userId: String, runId: String): AnalyticsPurgeCounts = RlsUserHolder.asService {
        val cacheKey = cacheKey(userId, runId)
        redisGet(cacheKey)?.let { return@asService mapper.readValue(it, AnalyticsPurgeCounts::class.java) }

        val activeKey = activeKey(userId)
        val active = redisGet(activeKey)
        if (active != null && active != runId) {
            conflicts.increment()
            throw PurgeInProgressException()
        }
        redisSet(activeKey, runId, Duration.ofMinutes(30))
        try {
            val deleted = db.update("DELETE FROM hourly_rollup WHERE user_id = ?", userId).toLong()
            val counts = AnalyticsPurgeCounts(tables = linkedMapOf("hourly_rollup" to deleted), files = 0)
            redisSet(cacheKey, mapper.writeValueAsString(counts), Duration.ofHours(24))
            audit.append(userId, "svc:account-purge", "analytics.purge", runId, "ok")
            log.info("analytics purge user={} run={} rollups={}", userId, runId, deleted)
            counts
        } finally {
            redisDelete(activeKey)
        }
    }

    /** Weekly residual probe (BR-10): rollup rows for the account, nothing else. */
    @Transactional(readOnly = true)
    fun probe(userId: String): AnalyticsResidual = RlsUserHolder.asService {
        val count = db.queryForObject("SELECT count(*) FROM hourly_rollup WHERE user_id = ?", Long::class.java, userId) ?: 0L
        AnalyticsResidual(rows = linkedMapOf("hourly_rollup" to count), files = 0)
    }

    private fun cacheKey(userId: String, runId: String) = "ana:purge:$userId:$runId"

    private fun activeKey(userId: String) = "ana:purge:active:$userId"

    private fun redisGet(key: String): String? = try {
        redis.opsForValue().get(key)
    } catch (_: Exception) {
        null
    }

    private fun redisSet(key: String, value: String, ttl: Duration) {
        try {
            redis.opsForValue().set(key, value, ttl)
        } catch (_: Exception) {
            // cache-only; rows are already gone and retries re-run honestly
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
class PurgeInProgressException : RuntimeException("A purge run is already in progress.")
