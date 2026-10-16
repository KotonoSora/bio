# Earnings Dashboard - Contracts and Payloads (feature 15)

Phase 2 TL · BA sources: `docs/01-requirements-ba/15-earnings-dashboard/` (BR-02..BR-12, `05-exception-handling-and-errors.md`). Inherited read conventions: `docs/02-architecture-techlead/12-profile-analytics/04-api-contracts-and-payloads-part-2.md`. Both endpoint paths are defined here.

## Endpoints

| Endpoint | Auth | Notes |
|---|---|---|
| `GET /api/v1/analytics/unified?range=&points=&page=&profile=` | session -> internal JWT | Unified funnel read; `private, max-age=60` |
| `GET /api/v1/analytics/unified.csv?range=&profile=` | session -> internal JWT | Streamed CSV (all rows); stricter throttle |

- `profile` optional; must equal the session-owned profile else `403 foreign_profile` (inherited).
- Ranges `7d|30d|90d` free, `365d` pro; anything else -> `422 range_clamped` + `allowed` (inherited). `points` cap 120 with exact totals.
- `page` default 1; page size fixed 25. CSV ignores paging (full stream).
- Throttles (owner family): JSON 60 req/min/user; CSV 6 exports/min/user; excess -> `429 rate_limited` + `Retry-After`.

## Response DTOs

```kotlin
data class UnifiedResponse(
  val generatedAt: Instant, val requestedRange: String, val appliedRange: String,
  val clamped: Boolean, val allowed: List<String>,
  val rows: List<UnifiedRow>, val totals: UnifiedTotals, val page: PageMeta
)
data class UnifiedRow(
  val blockId: String, val title: String?, val deleted: Boolean,
  val views: Long, val clicks: Long, val ctrX1000: Int?,        // null under 30 views (13 floor)
  val units: Long?, val revenue: Long?, val rpc: Long?,         // null: source absent or unavailable
  val netLabel: String?,                                        // "gross" | "net" verbatim; null without revenue
  val engagement: CellMeta, val money: CellMeta?,               // money null when no flagged source
  val unavailableCells: List<String> = emptyList()              // ids: views|clicks|ctr|units|revenue|rpc
)
data class CellMeta(val source: String, val lagSeconds: Long, val generatedAt: Instant)
data class UnifiedTotals(
  val views: Long, val clicks: Long, val ctrX1000: Int?,
  val units: Long?, val revenue: Long?, val rpc: Long?, val moneyComplete: Boolean
)
data class PageMeta(val number: Int, val size: Int, val totalRows: Int, val totalPages: Int)
```

## Field semantics

| Field | Rule |
|---|---|
| Engagement cells | Views/clicks from 12-13 aggregates; hourly grain; `ctrX1000` consumed from 13 (window sums, round-half-up), null under 30 views |
| Money cells | From the flagged revenue rollup for the same link + window; VND integers; label verbatim; absent when no flag (enforcement by absence) |
| `rpc` | `revenue / clicks` same-window, round-half-up; null when clicks = 0 or revenue absent |
| `unavailableCells` | Flag set but fetch timed out (2 s): listed cells render `temporarily unavailable` with retry; the rest of the row renders |
| `moneyComplete` | True only when every visible row has revenue; otherwise money totals are null (hidden, never partially summed) |
| `engagement` / `money` meta | Tooltip + CSV columns: `{source, lagSeconds, generatedAt}` per group |
| `deleted` / `title` | Deleted block -> `title:null, deleted:true` -> UI `Deleted link` |
| `page.size` | Fixed 25; `totalPages` server-computed; no client-side full-set sorting |

## CSV format

Columns per row: `block_id,title,views,clicks,ctr_x1000,units,revenue,rpc,gross_or_net,source,lag_seconds,generated_at`. `source` = money source when present, else `analytics`. Chunked, pull-based; cancel aborts; per-row timestamps support cell-for-cell reconciliation.

## Errors (RFC 9457 ProblemDetail, stable codes)

```json
{"type":"about:blank","title":"Forbidden","status":403,"code":"foreign_profile"}
{"type":"about:blank","title":"Range outside plan","status":422,
 "code":"range_clamped","allowed":["7d","30d","90d"]}
{"type":"about:blank","title":"Too many requests","status":429,"code":"rate_limited"}
```

Codes are inherited (`foreign_profile`, `range_clamped`, `rate_limited`); none is new here, none is renamed.

## Governance, metrics, grain separation

- `/api/v1` additive-only with the OpenAPI diff gate; flag set closed (new source = spec amendment); no dashboard-only metric exists.
- Metrics: `unified.fanout_ms{source}`, `unified.source_unavailable{source}`, `unified.csv_bytes`.
- Grain separation: live counters never appear in these payloads; ticks render via feature 14's component client-side (BR-07, D-7).
- Forward dependency: `CapabilityReader` + `RevenueRollupPort` stay absent/empty until shop 22-26 / monetize 32-40 publish flags and rollups; the engagement side ships alone (BO-3).
