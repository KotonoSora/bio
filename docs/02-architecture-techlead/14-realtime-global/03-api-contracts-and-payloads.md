# Realtime Global - Contracts and Payloads (feature 14)

Phase 2 TL · BA sources: `docs/01-requirements-ba/14-realtime-global/` (BR-01..BR-12, `05-exception-handling-and-errors.md`). Base contracts: `docs/02-architecture-techlead/12-profile-analytics/04-api-contracts-and-payloads-part-2.md`. The endpoint path was reserved by 12 part 2 (governance note) and is defined here.

## Endpoint

| Endpoint | Auth | Notes |
|---|---|---|
| `GET /api/v1/analytics/realtime?profile=` | session -> internal JWT | Pro-only live window; throttled; `Cache-Control: private, max-age=10` |

- Optional `profile` param must equal the session-owned profile, else `403 foreign_profile` (inherited).
- Pro gate from the platform entitlement snapshot (13 D-7, <= 30 s staleness); free -> `403 realtime_forbidden`. Snapshot miss fails closed to free + `entitlement_snapshot_miss` counter. Demotion mid-session surfaces the same `403`; the client swaps to the teaser silently.
- Throttle: per-profile-per-user token bucket `rtpoll:{profileId}:{userId}`, 1 request per 10 s; excess -> `429 rate_limited` + `Retry-After: 10`; counter `realtime.poll_throttled`.

## Response DTOs

```kotlin
data class RealtimeResponse(
  val generatedAt: Instant,
  val windowS: Int = 1800,
  val visitors: Long,                // sum of deduped views in the window
  val cpmSeries: List<Long>,         // 30 clicks-per-minute points; [0] oldest, [29] current minute
  val topLinks: List<RealtimeLink>,  // server-ranked desc, top 8
  val geo: List<GeoCount>,           // server-ranked desc, top 5 countries, ISO 3166-1 alpha-2
  val sales: SalesBlock? = null,     // absent in v1 (D-6)
  val status: String? = null,        // "degraded" when present; null = ok
  val lastGoodAt: Instant? = null    // present with status = "degraded"; null when never good
)
data class RealtimeLink(val blockId: String, val title: String?, val clicks: Long, val deleted: Boolean)
data class GeoCount(val country: String, val count: Long)
data class SalesBlock(              // forward contract shape; data supply owned by shop/monetize
  val lagSeconds: Long, val revenueSeries: List<Long>, val unitsPerLink: List<RealtimeLink>
)
```

## Field semantics

| Field | Rule |
|---|---|
| `visitors` | Sum of `v` across merged buckets (each already deduped at intake); labeled "last 30 min", never "concurrent" |
| `cpmSeries` | Clicks (`c`) per bucket minute; missing minutes = 0; downsample never changes totals |
| `topLinks` | Server-ranked; ties broken by blockId; deleted/missing title -> `title:null, deleted:true` -> UI `Deleted link` |
| `geo` | Country-grain only (DEVIATION CARRIED, feature 12 Q-04); the legacy `city` field has no source |
| `status`/`lastGoodAt` | On merge failure: `"degraded"` + last successful merge instant (null if never good); both absent otherwise; a quiet profile stays `ok` |
| `generatedAt` | Response build time; all as-of stamps derive from it; same-numbers contract key |

## Degraded shape

`200` with `status:"degraded"` + `lastGoodAt`; data arrays come back contract-stable but zeroed. The client must render its own last-good snapshot behind the banner, never the degrading response's zeros as current - and on a cold degraded load shows `awaiting first update` when `lastGoodAt` is null. Server reconstructs no last-good payload (no hidden cache of responses exists).

## Errors (RFC 9457 ProblemDetail, stable codes)

```json
{"type":"about:blank","title":"Pro feature","status":403,"code":"realtime_forbidden"}
{"type":"about:blank","title":"Forbidden","status":403,"code":"foreign_profile"}
{"type":"about:blank","title":"Too many requests","status":429,"code":"rate_limited"}
```

`Retry-After` (seconds) accompanies `429`.

## Metrics and governance

- Metrics: `rt.write_pressure`, `rt.write_error`, `realtime.poll_throttled`, `rt.last_bucket_age`; `consumer_lag_seconds` remains the hourly path's signal (12) and does not gate realtime (D-2).
- `/api/v1` is additive-only with the OpenAPI diff gate; codes are never renamed. `realtime_forbidden` and `rate_limited` are defined here; `foreign_profile` is inherited.
- Sales governance: the `sales` shape is fixed here; supply is owned by shop 22-26 / monetize 32-40 via the `has_orders` capability; v1 returns the field absent - never zero-filled, never placeholder-rendered.
- Ops-only globals: no creator-facing endpoint for platform-wide totals exists; ops surface only (BR-12), leaderboards remain 45+.
