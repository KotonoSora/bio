package com.resume.account.domain

import com.resume.account.api.PlanResponse
import com.resume.account.api.SubscriptionProjection
import com.resume.account.api.TrialState
import org.springframework.stereotype.Service
import java.time.temporal.ChronoUnit

/** Domain port: the feature 01 entitlement snapshot (fail-closed, cached <= 30 s). */
interface EntitlementSnapshotSource {
    fun snapshot(userId: String): EntitlementSnapshot
}

/** Domain port: feature 36's projected lifecycle row (absent = nothing to show). */
interface SubscriptionProjectionSource {
    fun fetch(userId: String): SubscriptionLifecycleView?
}

/**
 * Feature 03 plan composition (D-1, T-01): tier from the feature 01
 * entitlement snapshot plus optional subscription rows projected from feature
 * 36. Absence stays the design: free renders plainly; an unreadable projection
 * renders tier-only; nothing is ever invented (BR-03, BR-05, S-9).
 */
@Service
class PlanService(
    private val entitlements: EntitlementSnapshotSource,
    private val projections: SubscriptionProjectionSource,
) {
    fun plan(userId: String): PlanResponse {
        val snapshot = entitlements.snapshot(userId)
        if (snapshot.plan != "pro") return PlanResponse(snapshot.plan)
        // Pro rows compose from feature 36's read for pro states only; any
        // failure degrades to tier-only (NFR-06 note: tier flips still honor
        // the <= 30 s snapshot bound - only the rows below depend on 36).
        val row = projections.fetch(userId)
            ?: return PlanResponse(snapshot.plan)
        val projection = SubscriptionProjection(
            status = row.status,
            term = row.term,
            periodEnd = row.periodEnd!!,
            periodStart = row.periodStart ?: row.periodEnd!!,
            nextAmountVnd = row.nextAmountVnd!!,
            graceEndsAt = row.graceEndsAt,
        )
        // Feature 03's derived trial state (TL 03): days = DAYS.between(periodStart, periodEnd).
        val trial = if (row.status == "trial" && row.periodStart != null) {
            TrialState(days = ChronoUnit.DAYS.between(row.periodStart, row.periodEnd).toInt(), endsAt = row.periodEnd!!)
        } else {
            null
        }
        return PlanResponse(snapshot.plan, projection, trial)
    }
}
