# Audience Insights - Workflow and User Journey (feature 13)

Phase 1 BA · Flows are normative; rules cite `02-user-stories-and-business-rules.md`, errors cite `05-exception-handling-and-errors.md`.

## Journey 1 - Creator explores audience dimensions

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Opens `/app/analytics/audience` | Route lazy-loads; default tab `device`, one fetch at a time | BR-13 |
| 2 | Creator | Switches tabs (device/geo/referrer/UTM/returning) | `GET audience?range=30d&dimension=X`; in-flight tab fetch cancels; bar rows with share % + absolute views | BR-02, BR-04 |
| 3 | System | Rows render | Referrer groups labeled social/search/direct with icons; returning renders as a donut with the seen-before-window definition stated once | BR-03, BR-04 |
| 4 | Creator | Opens UTM tab on an untagged profile | Unlock CTA with editor link, not an empty chart | BR-11 |
| 5 | Creator | Server trims long tail | Top-25 rows + `Other (n campaigns)` computed server-side | BR-10 |

## Journey 2 - CTR, rank, and Movers

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Scrolls to performance | `GET links/performance?range=` returns rows: link, clicks, CTR, rank, delta | BR-05 |
| 2 | System | Ranks computed | Ties share rank numbers (1, 2, 2, 4), server-computed; client displays only | BR-05 |
| 3 | Creator | Checks Movers | Biggest gainers/fallers with sparklines from the per-day series | BR-05 |
| 4 | Creator | Link under 30 views | `not enough data` instead of a noisy 100% CTR | BR-11 |

## Journey 3 - Compare overlays with honesty

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Toggles previous-period overlay | Overlay renders with absolute + percentage deltas; weekday shift flagged `aligned` | BR-06 |
| 2 | System | Comparison span is pruned or beyond retention | Toggle disables with a one-line reason; no zero-filled false comparison | BR-06 |
| 3 | Creator | Looks for year-ago | Toggle ships disabled with the retention reason (DEVIATION FLAGGED, BR-06) | BR-06 |

## Journey 4 - Plan edge and downgrade

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Free creator | Picks 365d | `422 range_clamped {allowed:[7d,30d,90d]}`; picker snaps back with plan text | BR-07 |
| 2 | Pro creator | Downgrades mid-session | Next fetch re-clamps immediately (entitlement per request, invalidation on plan change) | BR-07 |
| 3 | System | Later re-upgrade | Reads clamp to the new window; aged-out rows stay gone | BR-08 |

## Journey 5 - Retention night cycle

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Scheduler | Nightly run starts | Dry-run count `retention.would_delete` recorded first | BR-08 |
| 2 | Job | Deletes rows older than each profile's plan window across base + audience tables | Bounded batches; failure alerts and retries next night | BR-08 |
| 3 | Ops | Watches `retention.deleted` | Drift between would-delete and deleted investigated | BR-08 |

## Journey 6 - Visitor (unchanged)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Visitor | Visits a profile | Same beacon fires; returning check adds one cache lookup, invisible to the page | BR-01, BR-03 |
| 2 | System | Rides the same queue | Segments land in the hourly rollup; no second pipeline, no new PII | BR-01, BR-09 |

## Journey 7 - Segmentation supply for other surfaces

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Feature 11 mobile tab | Needs the profile's device/UA mix | Calls `dimension=device` once; renders banner-eligibility card | BR-14 |
| 2 | Feature 15 earnings | Needs CTR per link | Consumes `ctr_x1000` from the same aggregates | BR-15 |
