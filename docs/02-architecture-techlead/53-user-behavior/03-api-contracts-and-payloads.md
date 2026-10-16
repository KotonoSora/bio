# User Behavior (Journeys, Conversion, Retention) - Contracts and Payloads (feature 53) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/53-user-behavior/`. GATED: none of these routes may appear in OpenAPI, the gateway, or any image until the spec amendment (AC-01). Reuse contracts: `docs/02-architecture-techlead/52-data-analysis/` (pipeline taxonomy + experiment registry), `docs/02-architecture-techlead/36-subscription-lifecycle/` (cancel flow + reminder execution), `docs/02-architecture-techlead/48-marketing/` (send path + eligibility).

## Endpoints (reserved, absent until the amendment)

| Endpoint | Auth | Notes |
|---|---|---|
| `GET /api/v1/ops/analytics/journeys?segment=&day=` | operator/analyst role | K-anonymized aggregates + marker counts; floor 10 enforced; inferred closures labeled |
| `GET /api/v1/ops/analytics/frictions?period=` | operator/analyst role | Ranked backlog with baselines + playbook links; correlation labeled |
| `GET /api/v1/ops/analytics/segments/{cut}/conversion` | operator/analyst role | Closed cut set only; sub-floor → "other" |
| `GET /api/v1/ops/analytics/retention` | operator/analyst role | Adoption distributions, cancel reasons, win-back results, trial bands |
| `GET /api/v1/ops/analytics/retention/bands` | operator role (internal) | Per-account band + contributing reasons; never buyer-visible |
| `GET /internal/v1/analytics/risk-bands?userKeys=` | service JWT | **36 registers at amendment**: reminder intensity + support readiness only; no other consumer may exist (dependency gate) |
| Experiment handoff | via 52's registry | Proposals enter `POST /api/v1/ops/analytics/experiments` with baseline rates attached |

```kotlin
data class JourneyDayView(val day: String, val segment: Map<String, String>, val journeys: Long,
  val stageTransitions: Map<String, Long>, val frictionCounts: Map<String, Long>, val inferredClosures: Long)
data class FrictionRow(val marker: String, val ordersTouched: Long, val abandonLift: Double,
  val score: Double, val baseline: Double, val playbookRef: String?)
data class SegmentConversion(val cut: String, val value: String, val stageConversion: Map<String, Double>,
  val markerRates: Map<String, Double>, val collapsedToOther: Boolean)
data class BandView(val userKeyHash: String, val band: String, val reasons: JsonNode, val scoreVersion: String)
data class WinbackRow(val cohort: String, val offerKind: String, val recencyBand: String,
  val wonBack: Long, val ignored: Long, val unsubscribed: Long)
```

## Rules

- Readouts are aggregates only; the k-anonymity floor is enforced server-side; no raw-trail route or export exists anywhere.
- Exit-reason capture rides 36's cancel flow (one-click skippable); this feature ships no buyer-facing surface.
- Risk bands drive reminder intensity and support readiness only — the internal route is dependency-gated; any other consumer fails CI.
- Win-back/rescue sends ride 36/48 paths; 48's eligibility arbitrates consent per recipient at send time; promo stacking rules apply.
- Friction findings are correlation-labeled; causal claims must link a 52-registry experiment.
- No behavioral data in gating/pricing/fraud payloads — those schemas never reference behavior tables (CI check).
- Errors: RFC 9457; no buyer PII beyond owner-scoped keys; rate limits per NFR-12.

## Error catalog (reserved)

| Code | Status | UI Behavior |
|---|---|---|
| `segment_too_small` | 200 (collapsed) | "Segment too small to display." |
| `cut_unknown` | 422 | Closed cut set only |
| `correlation_unlabeled` | - | Review checklist blocks causal language |
| `experiment_link_required` | 422 | Causal claim needs a registry link |
| `band_consumer_denied` | 403 | Only the 36 seam may read bands |
| `export_unsupported` | 404/422 | "Not available." (no surface exists) |
| `role_required` | 403 | "Not available." |
| `deletion_propagated` | - | Raw cleared; aggregates stand; buyer sees normal flow |
