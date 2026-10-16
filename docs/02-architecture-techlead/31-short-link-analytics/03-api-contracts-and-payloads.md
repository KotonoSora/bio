# Short-Link Analytics - API Contracts and Payloads (feature 31)

Phase 2 TL · Endpoints in `analytics-service` (gateway prefix `/api/v1/analytics/*` per feature 12's routing; ops routes `ROLE_OPS` re-verified per request per the links-module convention). Security: `docs/02-architecture-techlead/31-short-link-analytics/05-security-logging-and-rate-limiting.md`. Producer contract: `docs/02-architecture-techlead/29-short-links/` — never redefined here.

## Endpoint map

| Method & path | Auth | Request | Success | Errors (code) |
|---|---|---|---|---|
| `GET /api/v1/analytics/links/{code}/stats?from=&to=` | session | - | 200 `LinkStatsView` | 404 `link_not_found`; 429 |
| `GET /api/v1/analytics/links/{code}/stats.csv?from=&to=` | session | - | 200 CSV stream | 404; 429 |
| `GET /api/v1/ops/analytics/links/{code}/replay?day=` | ops (`ROLE_OPS` re-verified) | - | 200 `ReplayView` | 403; 404; 410 `evidence_expired`; 429 |
| `POST /api/v1/ops/analytics/quarantine/{id}/decide` | ops + CSRF | `{action, reason}` | 200 `QuarantineView` | 403; 404 `quarantine_not_found`; 422 `decide_invalid`; 429 |

Path mapping: legacy `GET /:code/stats` + `stats.csv` map here under the analytics prefix (service-prefix ownership, feature 12's routing); the owner UI route stays `/app/links/{code}/stats`.

## Kotlin DTOs

```kotlin
data class LinkStatsView(val code: String, val from: LocalDate, val to: LocalDate,
  val series: List<DayCounts>, val referrers: List<SegmentCount>, val devices: List<SegmentCount>,
  val geo: List<SegmentCount>, val scanShare: ScanShare, val excluded: List<SegmentCount>,
  val equation: Equation, val quarantine: List<QuarantineRef>,
  val generatedAt: Instant, val lagMinutes: Int)
data class DayCounts(val day: LocalDate, val opens: Long, val uniques: Long, val scans: Long, val excluded: Long)
data class SegmentCount(val dimension: String, val key: String, val count: Long)
data class ScanShare(val scans: Long, val estimated: Boolean)     // estimated=false only for event-pack codes
data class Equation(val raw: Long, val excluded: Long, val reported: Long)
data class QuarantineRef(val id: String, val heldCount: Int, val reason: String, val status: String)
data class QuarantineView(val id: String, val status: String, val decidedBy: String?, val decideReason: String?)
data class ReplayView(val code: String, val day: LocalDate, val rawSample: List<RawHitSample>,
  val filterDecisions: Map<String, Long>, val quarantineRows: List<QuarantineView>)
data class RawHitSample(val ts: Instant, val ipHash: String, val userAgentClass: String,
  val referrerHost: String?, val decision: String)
```

## Rules

- Owner scoping: reads resolve `user_id` from the session/RLS; foreign code → 404 with zero rows (no existence oracle); tombstoned code → 200 with `retired` note (history survives, per feature 12's block-deletion semantics).
- Equation: every raw hit classifies exactly once, so `opens + scans + excluded = raw` and `raw − excluded = reported` hold per day and in aggregate (AC-05); the equation fields are computed from stored counts, never from raw reads (layering rule).
- `estimated` is data-level: `scanShare.estimated = true` for heuristic splits; `false` only for exact event-pack location codes; the UI must render from this field (AC-07).
- CSV (machine-stable, no localized headers): fixed union schema `row_type, day, dimension, key, opens, uniques, scans, excluded, generated_at` — day rows (`row_type=day`) plus segment rows (`row_type=segment`, `dimension` including `excluded` reasons); streamed (NFR-03).
- Quarantine: pending rows surface in `LinkStatsView.quarantine` (banner + case ref); decide requires `action in (release|drop)` and a reason ≤ 200 chars; release replays held hits through the full pipeline (dedupe applies; additive aggregation) and decrements the `excluded/quarantine` counter by the held count; drop discards with evidence intact until TTL; both audited with operator + reason (AC-03, AC-08).
- Replay: sample view only (hashed ids), day within the 90-day raw window; beyond → 410 `evidence_expired`; every lookup audited (AC-05).
- Freshness: `generatedAt` + `lagMinutes` on every readout (NFR-02).
- Events: this feature consumes `link.hit.v1` (stream `links.hits`) and emits none; quarantine alerting is a metric + the quarantine row itself (no new event type); ops actions are audited without a bus event.

## Forward seams recorded

| Seam | Owner | Fixed contract |
|---|---|---|
| affiliate `/r/` scoped readouts | 32-40 (forward) | same aggregates via their own endpoints; posture matrix honored |
| ad measurement readouts | gated 48-53 | same aggregates; ad-attributed counting uses the strictest posture |
| dual-count explainer | feature 12 | bio-block case: matched windows + explainer line in both dashboards; no data change |
| prune scope | feature 13 | job extended to these tables (cross-edit landed) |
| owner resolution | feature 29 | internal read `GET /internal/v1/links/{code}/owner` added this batch (cross-edit) |

## Cross-edits in this batch

| File | Change |
|---|---|
| `docs/02-architecture-techlead/00-platform-baseline/01-architecture-and-service-topology.md` | link-service row 29-31 → 29-30 (+QR); analytics-service row 12-15 → 12-15, 31 (+link-hit intake) |
| `MIGRATION.md` | service rows mirrored; rows 30/31 `done`; batch 8 complete |
| `docs/02-architecture-techlead/12-profile-analytics/` | Redis row gains `links.hits`; retention line; V3 pointer section |
| `docs/02-architecture-techlead/13-audience-insights/02-database-schema-design.md` | prune row notes the link-hit aggregates scope |
| `docs/02-architecture-techlead/02-account-deletion/03-api-contracts-and-payloads.md` | link-service + analytics-service purge rows |
| `docs/02-architecture-techlead/29-short-links/` | event row gains `purposeHint?`; internal owner-read row; raw-archive annotation |
| `docs/02-architecture-techlead/30-qr-codes/03-api-contracts-and-payloads.md` | cross-edit table already logs the QR-side items (rows land here) |

## Governance

- Codes appended to the analytics catalog; no new event types.
- Contract tests: equation equality, rebuild rerun byte-identity, flood isolation, RLS probes, CSV reconciliation vs screen, estimated-flag coverage, replay TTL boundary.
