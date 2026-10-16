# Profile Analytics - Workflow and User Journey (feature 12)

Phase 1 BA · Flows are normative; rules cite `02-user-stories-and-business-rules.md`, errors cite `05-exception-handling-and-errors.md`.

## Journey 1 - Visitor fires the beacon (public page)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Visitor | Loads `/:username` | Micro-script fires `POST /api/t/:username` (kind=view, referrer, coarse hints) before/independent of render | BR-01 |
| 2 | Gateway -> analytics-service | Request arrives | Edge rate bucket passes; dedupe checks 10-min window; fresh hit enqueues; respond `202 {ok:true}` in one fast path | BR-01, BR-02 |
| 3 | Visitor | Taps a block link | Beacon (kind=click, blockId) + navigation proceed in parallel; banner/escape events follow feature 11 | BR-14 |
| 4 | System | Refresh / double-tap | Duplicate collapses in dedupe; no inflation | BR-02 |
| 5 | Consumer | :05 each hour | Batch drains, raw NDJSON file written, aggregates upserted once per natural key | BR-03, BR-04 |

## Journey 2 - Creator reads the overview (default path)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Opens `/app/analytics/overview` | Route lazy-loads the analytics chunk; skeletons per widget (no page-blocking spinner) | BR-12 |
| 2 | System | Widgets mount | Parallel `GET overview?range=30d` + `GET links?range=30d` (default 30d) | BR-07 |
| 3 | System | Responses render | Totals + deltas row, daily series (points cap respected), per-link table (25-row pager, share bars), sources, countries | BR-08, BR-12 |
| 4 | System | Footer | `generatedAt` + "hourly data" lag label per widget group | BR-08 |
| 5 | Creator | Switches tabs / hides window | No interval polling; refetch on focus only; hidden tab issues zero requests | BR-12 |

## Journey 3 - Free creator hits a plan edge

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Free creator | Picks 365d in the range picker | `422 range_clamped {allowed:[7d,30d,90d]}` | BR-07 |
| 2 | System | Clamp response received | Picker snaps back to 90d with "Available on your plan: 7/30/90d" | BR-07 |
| 3 | Free creator | Sees the realtime card | Locked teaser panel; zero realtime fetches in logs | BR-13 |

## Journey 4 - Degraded pipeline (lag honesty)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Ops | Lag metric crosses 90 min | `consumer_lag_seconds` alert fires | BR-04 |
| 2 | Creator | Opens the overview meanwhile | Banner: "Data delayed — last updated ..." with stale chip; numbers stay visible, never fabricated fresh | BR-08 |
| 3 | Consumer | Backlog drains | Stale banner clears; data resumes normally | BR-04 |

## Journey 5 - Abuse and bots

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Attacker | Beacon burst / tampered ids | Bursts throttled (`429` + `Retry-After`); tampered targets dropped post-`202`, counted, no write, no oracle | BR-01, BR-05, BR-10 |
| 2 | System | Sustained 429 ratio > 5% over 5 min | `beacon.abuse` alert; bucket tightens automatically; recovers on decay | BR-01 |
| 3 | Crawler | Requests page with bot UA | Beacon rejected + counted; view absent from aggregates | BR-05 |

## Journey 6 - QR scan at an event

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Visitor | Scans the venue QR | `/:username` opens; beacon fires like any visit | BR-11 |
| 2 | System | Pipeline processes | Exactly one deduped view recorded; no QR-specific path exists | BR-11 |
