# Profile Analytics - Contracts and Payloads, Part 2: Owner Reads (feature 12)

Phase 2 TL · BA sources: `docs/01-requirements-ba/12-profile-analytics/` (BR-07, BR-08, BR-09, BR-12). Intake in part 1: `docs/02-architecture-techlead/12-profile-analytics/03-api-contracts-and-payloads-part-1.md`.

## Endpoints

| Endpoint | Auth | Notes |
|---|---|---|
| `GET /api/v1/analytics/overview?range=&points=&profile=` | session -> internal JWT | totals, deltas, series, sources, countries, lag |
| `GET /api/v1/analytics/links?range=&profile=` | session -> internal JWT | per-link clicks, share, title |

Both scope to the server-set `user_id`. The optional `profile` param must equal the session-owned profile, else `403 foreign_profile` (IDOR probe contract; agency act-as arrives in feature 45). Ranges: `7d|30d|90d` free, `365d` pro; anything else -> `422 range_clamped` with `allowed`. `points` capped at 120; totals stay exact at any points.

## Response DTOs

```kotlin
data class OverviewResponse(
  val generatedAt: Instant, val requestedRange: String, val appliedRange: String,
  val clamped: Boolean, val allowed: List<String>, val lagSeconds: Long,
  val totals: Totals, val series: List<DayPoint>,
  val sources: List<GroupCount>, val countries: List<GroupCount>
)
data class Totals(val views: Long, val clicks: Long, val engagement: Double,
  val viewsDeltaPct: Double, val clicksDeltaPct: Double)
data class DayPoint(val day: LocalDate, val views: Long, val clicks: Long)
data class GroupCount(val label: String, val count: Long)

data class LinksResponse(
  val generatedAt: Instant, val range: String, val lagSeconds: Long,
  val links: List<LinkRow>
)
data class LinkRow(val blockId: String?, val productId: String? = null, val title: String?,
  val clicks: Long, val share: Double, val deleted: Boolean)
```

## Field semantics

| Field | Rule |
|---|---|
| `engagement` | clicks / views, `0.0` when views = 0 |
| Deltas | equal-length previous period; `0.0` when the previous period is empty |
| `share` | clicks / total clicks, `0.0` when no clicks |
| `sources` / `countries` | top-N groups read from audience tables (feature 13) — empty arrays until 13 ships; contract stable |
| `lagSeconds` | now - last rollup watermark; drives the stale chip |
| `generatedAt` | response build time; the same-numbers contract keys off it |
| Titles | enriched via the cached windowed blocks read; product rows (feature 22) enrich via the shop `products:public` read when available; deleted/unavailable -> `title:null, deleted:true` -> UI label `Deleted link` |

## Errors (RFC 9457 ProblemDetail, stable codes)

```json
{"type":"about:blank","title":"Range outside plan","status":422,
 "code":"range_clamped","allowed":["7d","30d","90d"]}
{"type":"about:blank","title":"Forbidden","status":403,"code":"foreign_profile"}
```

## Governance

- `/api/v1` is additive-only with an OpenAPI diff gate; codes are never renamed.
- No read path may join raw NDJSON; aggregate tables are the only source.
- Cache: owner reads are `private, no-store` (session-scoped); the PWA worker must treat all `/api/*` as network-only (feature 11 BR-07).
- Realtime endpoints (`/api/v1/analytics/realtime`) and audience endpoints do not exist here — owned by 14 and 13 respectively; the locked teaser issues zero calls.
