# Tier Presentation - API Contracts and Payloads (feature 03)

Phase 2 TL · Two reads: the account plan read (account-service) and the frozen public plans contract (served by features 32-40). Auth/rate rules: `docs/02-architecture-techlead/03-tier-presentation/05-security-logging-and-rate-limiting.md`. JSON only; problems follow RFC 9457.

## Endpoint map

| Method & path | Auth | Success | Errors (code) |
|---|---|---|---|
| `GET /api/v1/plan` | session | 200 `PlanResponse` | 401 `unauthenticated` |
| `GET /api/v1/plans` | public, cache-first | 200 `PlansResponse` (`Cache-Control: public, max-age=300`) | 429 `throttled` |
| `GET /internal/v1/billing/subscriptions/{userId}` (consumed; feature 36) | service JWT | 200 `SubscriptionProjection` (+ additive `periodStart`, `graceEndsAt`) | 401 |
| `GET /internal/v1/entitlements/{userId}` (consumed; feature 01) | service JWT | 200 snapshot | 401 |

## Kotlin DTOs (source of truth for the SPA type mirror)

```kotlin
data class PlanResponse(
    val plan: String,                                   // free | pro (feature 01 snapshot)
    val subscription: SubscriptionProjection? = null,   // composed from 36's read for pro rows; null when effective free
    val trial: TrialState? = null,                      // derived from 36's read when status = trial
)

data class SubscriptionProjection(
    val status: String,                                 // active | grace | cancelled | trial (feature 36)
    val term: String,                                   // monthly | yearly
    val periodEnd: java.time.Instant,
    val nextAmountVnd: Long,                            // buyer's own next amount
)

data class TrialState(val days: Int, val endsAt: java.time.Instant)

data class PlansResponse(
    val version: String,                                // active catalog version id
    val tiers: List<Tier>,
    val prices: Prices,
    val trialDays: Int? = null,                         // omitted when unset
)

data class Tier(val id: String, val name: String, val claims: List<String>) // claim ids, registry-backed
data class Prices(val monthlyVnd: Long, val yearlyVnd: Long, val yearlyDiscountPct: Int? = null)
```

`yearlyVnd` equals 12x monthly minus the configured discount, computed server-side to the VND; `yearlyDiscountPct` is omitted when unset (absence rule). Tier claim ids resolve through `plans-claims.json`; free text never ships without a registry row.

## Codes

No new runtime codes. Consumed: `step_up_required` (403, feature 01) on cancel/change; `throttled` (429). Build-time failures (drift, invalid config) are CI/boot failures, not API codes.

## Claim registry format (web repo artifact)

```json
{ "claimId": "yearly_saving_badge",
  "textPattern": "Save {yearlyDiscountPct}% with yearly billing",
  "enforcement": "docs/02-architecture-techlead/03-tier-presentation/02-database-schema-design.md",
  "key": "yearlyDiscountPct" }
```

Rules: a claim row without an enforcement path fails; an enforcement without a claim row fails (extra-claim check); rendered copy containing a literal digit for a keyed claim fails.

## Governance

- Additive-only contract changes (G4); `version` field carries the catalog version for cache keys.
- The catalog payload shape is frozen here; features 35-36 implement it as the matrix truth and may extend additively (new claim ids), never redefine meaning.
- No events are emitted by this feature; config/catalog changes self-heal through the 300 s public cache TTL.
