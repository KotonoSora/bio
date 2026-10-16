# Audience Insights - Contracts and Payloads (feature 13)

Phase 2 TL · BA sources: `docs/01-requirements-ba/13-audience-insights/` (BR-02..BR-07, BR-10). Base intake/reads: `docs/02-architecture-techlead/12-profile-analytics/03-api-contracts-and-payloads-part-1.md` + part 2.

## Additive queue vocabulary (feature 12's governance rule)

| Field | Type | Default when absent | Note |
|---|---|---|---|
| `utm` | `{source?, medium?, campaign?}`, normalized | fallback chain (BR-04) | Captured from the landing URL into 12's `BeaconRequest.utm`; no micro-script change |
| `browser` | `chrome \| safari \| firefox \| edge \| zalo \| other` | `other` | Completes the `device` key `{class}-{browser}` |
| `returning` | boolean | `new` | From the intake seen-check (BR-03); no visitor id rides the queue |

`v` stays 1; this feature records the additive fields here first, per 12's rule. Absent fields only occur for pre-deploy replays (atomic compose deploy).

## Endpoints

| Endpoint | Params | Notes |
|---|---|---|
| `GET /api/v1/analytics/audience` | `range=7d\|30d\|90d\|365d`, `dimension=device\|geo\|referrer\|utm\|returning` | Owner-scoped; clamp per plan |
| `GET /api/v1/analytics/links/performance` | `range=...` | Owner-scoped; CTR, rank, Movers |

Both reuse 12's session handling, `foreign_profile` check, and clause: free 7/30/90d, pro adds 365d.

## Response DTOs

```kotlin
data class AudienceResponse(
  val generatedAt: Instant, val dimension: String, val range: String,
  val lagSeconds: Long, val dataStart: LocalDate?, val aligned: Boolean,
  val prevPeriod: Period?,           // null when the span is not retained
  val rows: List<SegmentRow>
)
data class Period(val from: LocalDate, val to: LocalDate)
data class SegmentRow(val key: String, val label: String, val views: Long,
  val share: Double, val collapsed: Int? = null,
  val prev: Delta? = null, val yoy: Delta? = null)
data class Delta(val abs: Long, val pct: Double)

data class LinkPerfResponse(
  val generatedAt: Instant, val range: String, val lagSeconds: Long,
  val dataStart: LocalDate?, val rows: List<LinkPerfRow>, val movers: List<Mover>
)
data class LinkPerfRow(val blockId: String, val title: String?, val clicks: Long,
  val ctrX1000: Int?, val rank: Int, val deltaRank: Int?, val series: List<Long>)
data class Mover(val blockId: String, val title: String?, val deltaClicks: Long,
  val direction: String)
```

## Field semantics

| Field | Rule |
|---|---|
| `share` | row views ÷ all in-window views; `Other` carries the residual |
| `collapsed` | present only on the `Other (n campaigns)` row (`key="__other"`): number of collapsed keys |
| `ctrX1000` | windowed clicks ÷ windowed views, per-mille, round-half-up; `null` under 30 views (BR-05, BR-11) |
| `rank` / `deltaRank` | competition ranking with ties (1, 2, 2, 4), server-computed; delta vs previous period (`null` when unavailable) |
| `series` | per-day clicks for sparklines, downsampled to <= 120 points (12's rule) |
| `dataStart` | earliest retained day; drives overlay enablement; `null` when empty |
| `aligned` | `true` when overlay windows were weekday-aligned (differing day counts); one-line UI copy |
| `prev` / `yoy` | absolute + percentage deltas; `yoy` stays `null` in v1 (DEVIATION FLAGGED, BR-06) |
| Titles | 12's rule: live enrichment, deleted -> `Deleted link` |

## Errors (RFC 9457 ProblemDetail, stable codes)

```json
{"type":"about:blank","title":"Range outside plan","status":422,
 "code":"range_clamped","allowed":["7d","30d","90d"]}
{"type":"about:blank","title":"Unknown view","status":422,
 "code":"dimension_unknown","allowed":["device","geo","referrer","utm","returning"]}
```

## Governance

- Additive-only `/api/v1`; `dimension_unknown` is a new code recorded here first; `range_clamped`/`foreign_profile` are reused, never redefined.
- UTM inbound vs outbound: landing-URL UTMs are captured for attribution; feature 11's rule stripping `utm_*`/`fbclid`/`igsh` from outbound block-link anchors is untouched — both hold.
- Feature 12's `sources[]`/`countries[]` now return populated groups (empty-until-13 was its documented forward path; contract unchanged).
- No read path joins raw NDJSON; audience aggregates only. Reads stay `private, no-store`; the PWA worker treats `/api/*` as network-only.
