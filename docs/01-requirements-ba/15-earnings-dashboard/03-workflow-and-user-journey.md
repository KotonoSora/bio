# Earnings Dashboard - Workflow and User Journey (feature 15)

Phase 1 BA · Flows are normative; rules cite `02-user-stories-and-business-rules.md`, errors cite `05-exception-handling-and-errors.md`.

## Journey 1 - Creator opens earnings (engagement-only phase)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Opens `/app/analytics/earnings` | Route lazy-loads with shared range picker + `FetchState`; default 30d | BR-09 |
| 2 | System | Unified read returns (no flags set) | Engagement-only table: views, clicks, CTR (13's `ctr_x1000`); no money columns, no stubs | BR-04, BR-05 |
| 3 | Creator | Checks a row | CTR under 30 views renders `not enough data`; tooltips show source + lag + timestamp | BR-02, BR-08 |
| 4 | Creator | Looks for revenue | Nothing implies imminent data; upgrade/monetize entry stays on the plan page, not in the grid | BR-04 |

## Journey 2 - Revenue strips light up per shipped source

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Revenue pipeline | First settled order lands (tips/shop/course/service) | Owning pipeline sets its capability flag | BR-04 |
| 2 | System | Next unified read sees the flag | Money cells join the rows with `units`, `revenue`, `rpc`, gross/net label, and money cell meta | BR-06, BR-08 |
| 3 | Creator | Reads tallies | Money totals render only when every visible row has complete money cells (`moneyComplete`); otherwise totals omit money | BR-05 |

## Journey 3 - Trace any figure to its origin

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Hovers/taps a cell group | Tooltip: `source - lag - generated_at` | BR-08 |
| 2 | Creator | Clicks the trace link | Origin (audience tab or order list) opens in the identical range window | BR-08 |
| 3 | Support/ops | Reproduces a figure with the creator | Same origin hop in an owner-shared session; no cross-user read | BR-09 |

## Journey 4 - CSV export

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Clicks `Export CSV` | `GET unified.csv` streams; progress + cancel shown | BR-10 |
| 2 | System | Stream runs | Rows carry `source` + `generated_at`; chunked with backpressure; memory bounded | BR-08, BR-10 |
| 3 | Creator | Cancels mid-stream | Fetch aborts; server stream ends (pull-based) - no orphaned work | BR-10 |

## Journey 5 - Partial failure and retries

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System | One flagged revenue source exceeds the 2 s timeout | Its cells render `temporarily unavailable` with retry; the rest of the view renders | BR-12 |
| 2 | Creator | Clicks retry | One view refetch (same endpoint), not a per-cell call | BR-12 |
| 3 | Ops | Watches `unified.source_unavailable{source}` | Timeout tuning reads the metric, never guesses | BR-12 |

## Journey 6 - Pro: live ticks beside unified figures

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Pro creator | Opens earnings | Unified read (hourly grain) + feature 14's live component embedded in the same screen | BR-07 |
| 2 | System | Ticks poll | Ticks follow 14's poll discipline and bypass the unified cache; server numbers never mix grains | BR-07 |

## Journey 7 - Range clamp and downgrade

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Free creator | Picks 365d | `422 range_clamped {allowed:[7d,30d,90d]}`; picker snaps back with plan text | BR-09 |
| 2 | Pro creator | Downgrades mid-session | Next fetch re-clamps (entitlement per request); both endpoints clamp identically | BR-09 |
