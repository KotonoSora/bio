# Sales Fees and Profit Calculator - API Contracts and Payloads (feature 26)

Phase 2 TL · Endpoints in `shop-service`; checkout/ledger seams are forward contracts (32-40); security: `docs/02-architecture-techlead/26-shop-fees-and-calculator/05-security-logging-and-rate-limiting.md`.

## Endpoint map

| Method & path | Auth | Request | Success | Errors (code) |
|---|---|---|---|---|
| `GET /api/v1/fees/schedule` | none (public) | - | 200 `ScheduleResponse` (published version only) | 404 `schedule_not_published` |
| `POST /api/v1/fees/quote` | session | `FeeQuoteRequest` | 200 `FeeQuoteResponse` | 409 `schedule_not_published`; 422 `quote_invalid`; 429 |
| `GET /internal/v1/fees/quote` | service JWT | query (`grossVnd, kind, tier?, railPassthroughVnd?`) | 200 `FeeQuoteResponse` | 409 `schedule_not_published`; 401 |
| `POST /api/v1/merch/quote` | session | `MerchQuoteRequest` | 200 `MerchQuoteResponse` | 422 `quote_invalid`, `cost_unknown`; 409 `schedule_not_published`; 429 |
| `GET /api/v1/merch/scenarios?cursor=` | session | - | 200 `ScenarioPage` | 401 |
| `POST /api/v1/merch/scenarios` | session + CSRF | `ScenarioSave` | 201 `ScenarioView` | 422 `quote_invalid`; 409 `schedule_not_published`; 429 |
| `GET /api/v1/merch/scenarios/{scenarioId}?version=` | session | - | 200 `ScenarioView` | 404 `scenario_not_found`; 401 |
| `POST /api/v1/merch/publish-price` | session + CSRF | `PublishPriceRequest` | 200 `PublishPriceResponse` | 404; 422 `below_cost_blocked`; 429 |
| `POST /internal/v1/ops/fees/versions` | service JWT + ops role | `RateVersionDraft` | 201 draft view | 403 `finance_role_required`; 422 `schedule_invalid` |
| `POST /internal/v1/ops/fees/versions/{v}/approve` | service JWT + ops role (different operator) | - | 200 published view | 403 `finance_role_required`; 409 `dual_control_violation` |
| `POST /internal/v1/ops/merch/costs` | service JWT + ops role | `CostUpsert` | 200 cost view | 403; 422 `quote_invalid` |
| `GET /internal/v1/billing/plans/current-prices` | service JWT (provided by 35) | - | 200 plan price list (empty = "pending") | consumed seam; never stale copy |

## Kotlin DTOs

```kotlin
data class ScheduleRow(val tier: String, val kind: String, val pctBp: Int,
  val railPassthrough: String, val payoutDelayDays: Int?, val payoutMinVnd: Long?)
data class ScheduleResponse(val rateVersion: Int, val roundingRule: String, val rows: List<ScheduleRow>)
data class FeeQuoteRequest(val grossVnd: Long, val kind: String, val tier: String? = null,
  val railPassthroughVnd: Long = 0)
data class FeeLine(val label: String, val amountVnd: Long, val atCost: Boolean)
data class FeeQuoteResponse(val grossVnd: Long, val feeLines: List<FeeLine>, val netVnd: Long,
  val rateVersion: Int, val tier: String, val kind: String)
data class MerchQuoteRequest(val mode: String, val template: String, val variant: String,
  val shippingEstimateVnd: Long = 0, val railEstimateVnd: Long = 0, val tier: String? = null,
  val desiredProfitVnd: Long? = null, val targetPriceVnd: Long? = null, val compareTiers: Boolean = false)
data class MerchLine(val label: String, val amountVnd: Long, val contractual: Boolean)
data class TierPoint(val tier: String, val profitVnd: Long, val marginBp: Long)
data class MerchQuoteResponse(val listPriceVnd: Long, val profitVnd: Long, val marginBp: Long,
  val feeQuote: FeeQuoteResponse, val lines: List<MerchLine>, val tiers: List<TierPoint>,
  val breakEvenUnits: Long?, val rateVersion: Int)
data class ScenarioSave(val name: String, val productId: String? = null,
  val inputs: JsonObject, val rateVersion: Int)
data class ScenarioView(val scenarioId: String, val version: Int, val name: String,
  val productId: String?, val inputs: JsonObject, val outputs: JsonObject,
  val rateVersion: Int, val createdAt: Instant)
data class ScenarioPage(val items: List<ScenarioView>, val nextCursor: String?)
data class PublishPriceRequest(val productId: String, val priceVnd: Long,
  val rateVersion: Int, val scenarioId: String? = null)
data class PublishPriceResponse(val productId: String, val priceVnd: Long, val pinnedRateVersion: Int)
data class RateVersionDraft(val rows: List<ScheduleRow>, val roundingRule: String)
data class CostUpsert(val template: String, val variant: String, val baseCostVnd: Long)
```

## Rules

- Purity: quote endpoints write nothing; the same function backs `fees/quote`, the internal seam, and the calculator; margin is `marginBp: Long` (basis points; UI formats) — zero floats anywhere.
- Pending state: any quote without a published version → 409 `schedule_not_published`; `GET /api/v1/fees/schedule` → 404 until then (BR-03).
- Tier: request may pass `tier` for comparison; default = the caller's current tier via the feature 01 snapshot (ledger projection by 35); unresolved -> `free`.
- Free/tip/linkout: zero-price quotes return zero fee lines (no minimum floor); tips use the standard rows; link-outs never quote.
- Snapshot: checkout (33/34) stores `{grossVnd, feeLines[], netVnd, rateVersion}` on the order at creation; the version locks there (BR-06); reconciliation is 38's execution of BR-04's identity.
- Calculator: forward mode = cost + estimates + fee → list price (rounded) and profit/margin; reverse = target → profit/margin; price < total cost → `below_cost_blocked` on publish-price with the shortfall in the problem body.
- Ops: draft validates invariants (all pairs present, free ≥ pro, tips present); approve requires a different operator (DB CHECK + route check); both actions audited; published versions are append-only.
- publish-price: owner-only; writes price + pinned version (+ scenario ref in audit); never invoked implicitly by rates.

## Events

| Direction | Event | Notes |
|---|---|---|
| emits | none | checkout pulls quotes synchronously; no push surface exists |
| consumes | none | break-even pulls plan prices on demand (36) |

## Forward seams recorded

| Seam | Owner | Fixed contract |
|---|---|---|
| order fee snapshot `{grossVnd, feeLines[], netVnd, rateVersion}` | 33/34 | written at checkout creation; immutable |
| reconciliation (quote vs charged vs ledger) | 38 | exact to the VND; mismatch pages finance instead of settling |
| plan prices for break-even | 35 | `GET /internal/v1/billing/plans/current-prices`; empty list renders "pending"; never stale copy |
| tier read | 01 snapshot (projection by 35) | quote default tier; `free` when unresolved |
| dashboard decomposition labels | 32-40 rollups → 15 | labels pass through verbatim; 15 keeps zero math |

## Cross-edits landed in this change

| File | Change |
|---|---|
| `docs/02-architecture-techlead/02-account-deletion/` | shop-service roster row says fees extend it (22-26) |
| `MIGRATION.md` | row 26 `done`; batch 6 required-complete (5/5) |

## Governance

- Codes appended to the shop catalog; no fee value is written anywhere in this tree (grep gate); fixtures are labeled and test-database-only.
- Contract tests: identity fixtures (tiers × kinds × versions), rounding boundaries, dual-control, pre-publication refusal, display drift, scenario immutability.
