package com.resume.billing.api

import com.resume.billing.domain.PlanCatalog
import com.resume.billing.domain.PlanCatalogView
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

/**
 * Feature 03 public read (D-2, T-04): the frozen `GET /api/v1/plans` contract.
 * Public (gateway-auth surface), cache-first at the edge with a versioned
 * payload; no per-user data ever personalizes this read (S-7). The payload
 * shape is rename-protected (G4): additive extensions only.
 */
@RestController
class PlansController(private val catalog: PlanCatalog) {

    @GetMapping("/api/v1/plans")
    fun plans(): ResponseEntity<PlanCatalogView> {
        val body = catalog.view()
        // Edge TTL is honored from this header (NFR-01: p95 <= 300 ms served
        // from the cache; origin fallback only on misses).
        return ResponseEntity.ok()
            .header("Cache-Control", "public, max-age=300")
            .body(body)
    }
}

/**
 * The ledger plan read the feature 01 entitlement snapshot consumes
 * (`PlanLedgerClient`): pre-feature 35 no ledger rows exist, so the free floor
 * materializes structurally; `profiles.limit` is 1 for free / unlimited for
 * pro (BR-10, D-3). Mesh-only: the public edge never reaches /internal/.
 */
@RestController
class InternalPlanController(private val catalog: PlanCatalog) {

    @GetMapping("/internal/v1/billing/plan/{userId}")
    fun plan(@PathVariable userId: String): Map<String, Any> {
        val plan = catalog.planFor(userId)
        return mapOf("plan" to plan, "capabilities" to catalog.capabilities(plan))
    }
}
