# Earnings Dashboard - User Stories and Business Rules (feature 15)

Phase 1 BA · Rules `BR-xx` are local to this feature. Inherited rules: `docs/01-requirements-ba/12-profile-analytics/02-user-stories-and-business-rules.md`, `docs/01-requirements-ba/13-audience-insights/02-user-stories-and-business-rules.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Creator | per-link rows showing views, clicks, CTR, units, revenue, revenue-per-click | I see which links earn | Must |
| US-02 | Creator | phase honesty: no money columns until a revenue source ships for me | I never mistake absence for zero sales | Must |
| US-03 | Creator | every figure labeled with its source, lag, and timestamp, opening at its origin in one hop | I trust and can verify any number | Must |
| US-04 | Creator | a CSV export that reconciles cell-for-cell | I can do finance work in a spreadsheet | Should |
| US-05 | Pro creator | longer history and live sales ticks beside the unified figures | live and longer-window performance stay in one view | Should |
| US-06 | Support/ops | trace any figure to its source aggregate in one hop | support resolves figure disputes reliably | Should |
| US-07 | Creator | the same numbers on mobile/PWA | arrangement changes never change values | Must |

## Business rules

- **BR-01 Read-only unification.** The dashboard owns no money and restates none; every figure is read from its source aggregate (12-13 tables, revenue rollups) and displayed as-is. No dashboard-only metric exists.
- **BR-02 Funnel formula (canonical).** Per-link rows: views, clicks, CTR, units, revenue, `rpc`. CTR = consumed `ctr_x1000` from 13 (null under 30 views -> "not enough data"). Units/revenue come from the revenue rollup for the same link + window. `rpc = revenue / clicks`, rounded half-up, null when clicks = 0 or revenue is absent. Mismatched-window division fails review.
- **BR-03 Attribution.** Purchase day owns the sale, documented once here; a finance change amends this rule line (never branches the code).
- **BR-04 Capability gating.** Money strips appear only for flags set by revenue pipelines: `has_tip_orders`, `has_shop_orders`, `has_course_orders`, `has_service_orders` (union = feature 14's `has_orders`). No flag -> money keys absent from the payload (enforcement by absence, not UI hiding). Pre-revenue profiles render engagement-only: zero money keys, zero stubs, zero greyed panels. The flag set is closed: a new revenue source requires a spec amendment, never an inferred panel.
- **BR-05 Missing-source honesty.** Unshipped source -> `not_yet_available` cell (em-dash + "coming when X ships" caption), never a zero that implies measured absence. Flag set but fetch timed out -> cell in `unavailableCells` ("temporarily unavailable" + retry). Absent and zero are never conflated.
- **BR-06 Gross vs net.** Rows carry the source's label verbatim (`gross` | `net`); the dashboard never computes net (no fee math exists here). A gross-only source renders no net column - absent, not zero.
- **BR-07 Cache and freshness.** Unified reads are `private, max-age=60` (per profile + range; merged views only, never source truth). Live ticks bypass this cache entirely (14's contract) and are never merged into server numbers - one response never mixes hourly aggregates with live counters.
- **BR-08 Traceability.** Every cell group carries `{source, lagSeconds, generatedAt}`; trace links open the origin (analytics tab or order list) with the identical range param. CSV rows carry `source` and `generated_at` columns.
- **BR-09 Owner scoping and ranges.** Session-scoped reads; a `profile` param that is not the session-owned profile -> `403 foreign_profile`; out-of-plan ranges -> `422 range_clamped` with `allowed` (inherited). CSV is owner-scoped identically.
- **BR-10 CSV discipline.** Exports stream (chunked, backpressure-safe); cancel aborts the pull - no orphaned work; CSV uses a stricter throttle than the JSON read; probing `422` is counted, never punished.
- **BR-11 Mobile/PWA parity.** Same endpoint and payload on every surface; layout may collapse, values never differ (feature 11 guarantee).
- **BR-12 Load discipline.** Fan-out is bounded (one local aggregate read + flagged remote rollups, 2 s timeout each); partial failure degrades cells, never the view; table rows paginate at 25 server-side; no interval refetch (hourly data: focus + manual refresh only); 500-link histories stay paginated.
