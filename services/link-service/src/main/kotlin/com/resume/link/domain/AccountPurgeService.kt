package com.resume.link.domain

import com.fasterxml.jackson.databind.ObjectMapper
import com.resume.link.data.ShortLinkRepository
import com.resume.link.security.RlsUserHolder
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

/** Purge counts returned to the runner (embedded verbatim in the certificate). */
data class LinkPurgeCounts(val tables: Map<String, Long>, val files: Long)

/** Residual-probe counts (BR-10): honest reads, never deletes. */
data class LinkResidual(val rows: Map<String, Long>, val files: Long)

/**
 * link-service purge participant (feature 02 D-1): `short_links` rows by
 * `user_id` under the server-set svc scope - zero cross-user deletes (NFR-09).
 * Idempotent per `runId` (counts cached after full success); a different active
 * run answers `409 purge_in_progress` (S-3). Resolve-cache keys (`links:code:*`)
 * and QR render files (`links/qr/`) belong to features 29/30 and are not
 * migrated yet: they contribute 0 honestly until those namespaces exist.
 */
@Service
class AccountPurgeService(
    private val links: ShortLinkRepository,
    private val audit: AuditService,
    private val redis: StringRedisTemplate,
    private val mapper: ObjectMapper,
    meters: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val conflicts = meters.counter("deletion.purge.conflict")

    @Transactional
    fun purge(userId: String, runId: String): LinkPurgeCounts = RlsUserHolder.asService {
        val cacheKey = cacheKey(userId, runId)
        redisGet(cacheKey)?.let { return@asService mapper.readValue(it, LinkPurgeCounts::class.java) }

        val activeKey = activeKey(userId)
        val active = redisGet(activeKey)
        if (active != null && active != runId) {
            conflicts.increment()
            throw PurgeInProgressException()
        }
        redisSet(activeKey, runId, Duration.ofMinutes(30))
        try {
            val deleted = links.deleteByUserId(userId).toLong()
            val counts = LinkPurgeCounts(tables = linkedMapOf("short_links" to deleted), files = 0)
            redisSet(cacheKey, mapper.writeValueAsString(counts), Duration.ofHours(24))
            audit.append(userId, "svc:account-purge", "link.purge", runId, "ok")
            log.info("link purge user={} run={} links={}", userId, runId, deleted)
            counts
        } finally {
            redisDelete(activeKey)
        }
    }

    /** Weekly residual probe (BR-10): link rows for the account, nothing else. */
    @Transactional(readOnly = true)
    fun probe(userId: String): LinkResidual = RlsUserHolder.asService {
        LinkResidual(rows = linkedMapOf("short_links" to links.countByUserId(userId)), files = 0)
    }

    private fun cacheKey(userId: String, runId: String) = "link:purge:$userId:$runId"

    private fun activeKey(userId: String) = "link:purge:active:$userId"

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
