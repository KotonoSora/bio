package com.resume.billing.domain

import com.resume.billing.config.CatalogProperties
import org.springframework.stereotype.Service

/**
 * Two tiers, plainly named (BR-01); the parity third tier is intentionally not
 * copied. Claim ids are registry-backed (`plans-claims.json`): the SPA renders
 * each sentence through the registry, and drift fails CI (BR-11).
 */
data class Tier(val id: String, val name: String, val claims: List<String>)

data class Prices(val monthlyVnd: Long, val yearlyVnd: Long, val yearlyDiscountPct: Int? = null)

data class PlanCatalogView(val version: String, val tiers: List<Tier>, val prices: Prices?, val trialDays: Int?)

/**
 * The public pricing data source (feature 03 D-2): amounts, discounts, and
 * day-counts render from config at request time - nothing is hand-typed
 * (BR-03). Yearly math is server-computed to the exact VND (BR-04, NFR-04):
 * 12x monthly minus the configured percent, rounded half-up to the dong.
 *
 * Until feature 35's ledger rows exist, every account reads the structural
 * free floor; pro capabilities are defined here so the entitlement contract
 * with feature 06 is already frozen (D-3, BR-10).
 */
@Service
class PlanCatalog(private val props: CatalogProperties) {

    val tiers: List<Tier> = listOf(
        Tier(id = "free", name = "Free", claims = listOf("one_profile", "unlimited_links", "basic_resume")),
        Tier(id = "pro", name = "Pro", claims = listOf("unlimited_profiles", "custom_domain", "realtime_analytics", "premium_templates", "ai_jd_tailor", "tips_enabled")),
    )

    fun version(): String = props.version

    /**
     * The registered entitlement values (BR-10, D-3): feature 06's create guard
     * consumes `profiles.limit` from the feature 01 snapshot; the value is never
     * hardcoded in the consumer.
     */
    fun capabilities(plan: String): Map<String, String> = when (plan) {
        "pro" -> mapOf("profiles.limit" to "unlimited")
        "free" -> mapOf("profiles.limit" to "1")
        else -> emptyMap()
    }

    /** Configured discount percent, trusted only inside the validated range (NFR-05). */
    private val discountPct: Int? = props.yearlyDiscountPct?.takeIf { it in 0..44 }

    /** Yearly = 12x monthly minus the configured discount, half-up at the dong (AC-03). */
    fun yearlyVnd(monthlyVnd: Long, discountPct: Int?): Long =
        12 * monthlyVnd - (12 * monthlyVnd * (discountPct ?: 0) + 50) / 100

    fun view(): PlanCatalogView = PlanCatalogView(
        version = props.version,
        tiers = tiers,
        prices = props.monthlyVnd?.let { monthly -> Prices(monthly, yearlyVnd(monthly, discountPct), discountPct) },
        trialDays = props.trialDays,
    )

    /**
     * The structural free floor answered on the internal plan read
     * (`GET /internal/v1/billing/plan/{userId}`): no ledger rows exist yet, so
     * the floor is materialized structurally (feature 01 PlanLedgerClient).
     */
    fun planFor(userId: String): String = "free"
}
