package com.resume.billing.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Feature 03 owning config (TL 02): `billing.catalog` keys. Finance-owned
 * values stay config; specs never carry placeholders. Both numeric keys are
 * absence-optional - unset means the presented claim is omitted, never a
 * placeholder or a default (BR-03, Q-01/Q-02).
 */
@Component
@ConfigurationProperties("billing.catalog")
class CatalogProperties {
    /** Active catalog version id; carried in every payload for edge cache keys (G4). */
    var version: String = "rename-v0"
    /** Pro monthly price in VND; unset = no prices serve (names + claims only). */
    var monthlyVnd: Long? = null
    /** Yearly discount percent; validated 0..44 at load (NFR-05 ceiling). */
    var yearlyDiscountPct: Int? = null
    /** Trial length in days; unset = trial CTA omitted (BR-08). */
    var trialDays: Int? = null
}

/**
 * NFR-05/S-3 boot drill: an invalid catalog refuses application startup
 * (deploy-time failure, never a live page). Values pass through `getOrThrow`
 * in [validate]; Spring context creation surfaces the failure.
 */
@Component
class CatalogValidator(private val props: CatalogProperties) {
    init {
        validate(props)
    }

    companion object {
        fun validate(props: CatalogProperties) {
            props.yearlyDiscountPct?.let { pct ->
                require(pct in 0..44) {
                    "billing.catalog.yearlyDiscountPct must be a whole percent within 0..44, got $pct"
                }
            }
            props.monthlyVnd?.let { monthly ->
                require(monthly >= 0) {
                    "billing.catalog.monthlyVnd must be an integral non-negative VND amount, got $monthly"
                }
            }
            props.trialDays?.let { days ->
                require(days > 0) {
                    "billing.catalog.trialDays must be a positive whole number of days, got $days"
                }
            }
        }
    }
}
