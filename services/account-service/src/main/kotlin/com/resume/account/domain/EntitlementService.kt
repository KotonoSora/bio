package com.resume.account.domain

import com.resume.account.config.AccountProperties
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class EntitlementSnapshot(
    val userId: String,
    val plan: String,
    val capabilities: Map<String, String>,
    val resolvedAt: Instant,
)

/**
 * Entitlement snapshot (BR-09, S-7): `free`|`pro` + registered capability map.
 * Consumers cache <= 30 s (NFR-07); any miss/error/timeout clamps to free and
 * increments `entitlement_snapshot_miss` - never a silent widen. Plan changes
 * self-heal via cache TTL; no event is emitted.
 */
@Service
class EntitlementService(
    private val ledger: PlanLedgerClient,
    props: AccountProperties,
    meters: MeterRegistry,
) : EntitlementSnapshotSource {
    private data class Cached(val snapshot: EntitlementSnapshot, val at: Instant)

    private val cache = ConcurrentHashMap<String, Cached>()
    private val cacheSeconds = props.services.entitlementCacheSeconds.coerceAtMost(30).coerceAtLeast(1)
    private val miss = meters.counter("entitlement_snapshot_miss")

    override fun snapshot(userId: String): EntitlementSnapshot {
        val now = Instant.now()
        cache[userId]?.let { if (it.at.plusSeconds(cacheSeconds).isAfter(now)) return it.snapshot }
        val ledgerPlan = ledger.fetch(userId)
        val snapshot = if (ledgerPlan == null) {
            miss.increment()
            EntitlementSnapshot(userId, plan = "free", capabilities = emptyMap(), resolvedAt = now)
        } else {
            EntitlementSnapshot(userId, plan = ledgerPlan.plan, capabilities = ledgerPlan.capabilities, resolvedAt = now)
        }
        cache[userId] = Cached(snapshot, now)
        return snapshot
    }
}
