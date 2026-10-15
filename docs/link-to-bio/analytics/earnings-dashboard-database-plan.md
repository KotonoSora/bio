# Earnings Dashboard Database Plan (capability flags + read-only contracts — no new tables)

PLAN ref: P1d-2 engagement side + P2-2/P2-3/P3 revenue side (phase-gated; this slice builds no shop/order/billing tables) + honesty rules. BA source: `docs/link-to-bio/analytics/earnings-dashboard.md`. Consumed by the earnings backend plan (aggregator reads; splits next to this file). Engagement aggregates live in the profile/audience database plans; revenue rollups are owned by shop/monetize majors. No migration — this slice creates zero tables.

## Purpose

Define the read-only unification contract without owning a single table: which capability flags gate each money strip, the canonical funnel formulas, gross-vs-net labeling rules, and why the dashboard may never compute money itself. No `CREATE TABLE` here by design — a dashboard-owned money table would fork the ledger truth.

## Users

- Backend developer: reads flags + rollups through ports owned elsewhere; adds no tables.
- Finance reviewer: verifies the dashboard restates nothing (every figure traces to a source aggregate).
- QA: asserts pre-revenue profiles show zero money panels (not greyed stubs).

## Functional requirements

1. Capability flags (read contract, storage owned by revenue pipelines): flag names `has_tip_orders`, `has_shop_orders`, `has_course_orders`, `has_service_orders` (per profile, maintained by each revenue source's own pipeline when its first settled order lands — shop/monetize majors own the storage mechanism; this slice defines the names and the read timing, nothing else). The dashboard reads flags before rendering each strip; an unknown future revenue source with no flag renders nothing (closed set — a new strip requires a spec amendment, never an inferred panel).
2. Funnel formulas (canonical, all server-side): per-link rows `views → clicks → CTR → units → revenue → revenue_per_click`, where views/clicks/CTR come from the analytics aggregates for the same link + window (CTR definition inherited from the audience database plan), and units/revenue come from the revenue rollup for the same link + window. `revenue_per_click = revenue ÷ clicks` (same-window; mismatched-window division fails review). Attribution window: purchase-day owns the sale (documented once here; a finance override would amend this line, not branch the code).
3. Gross-vs-net rule: rows state gross vs net exactly as the order source labels them; the dashboard never computes net (no fee math here — fees live in `docs/shop/` pricing). A row whose source exposes only gross renders `gross` with no net column (absent, not zero).
4. Missing-source honesty: unshipped revenue sources render `not_yet_available` cells (never zeros that imply measured absence). Pre-revenue profiles render a pure engagement dashboard with zero money-panel stubs (asserted in test — greyed-out panels implying imminent data are a Qualification failure).
5. Traceability columns: every figure carries `{source, lag, generated_at}`; CSV exports add `source,generated_at` per row. There is no dashboard-only metric — anything displayed opens at its origin (analytics tab or order list) in one hop.
6. Cache posture: unified responses `private, max-age=60` (per-profile, per-range); realtime ticks bypass cache per the realtime plans. Cache holds merged views, never source truth (sources re-read on expiry).
7. Growth: this slice adds zero write load and bounded read load (one fan-out per dashboard view, per-source timeouts in the backend plan). Revenue-side growth (order volume) is absorbed by the owning pipelines, never here.

## Data / API touches

- DDL: none — assertion: no `CREATE TABLE` for dashboard state anywhere under `docs/link-to-bio/analytics/` for this slice (review greps it). Flag names cataloged in rule 1 (storage owned by revenue pipelines). Reads: analytics aggregates + revenue rollups via `CapabilityReader` + `RevenueRollupPort` (interfaces in the backend plan).
- Funnel formulas: rule 2 (canonical). Labeling: rule 3. Freshness: rule 6.

## Acceptance criteria

- Gating drill: pre-shop profile shows engagement-only with zero money-panel stubs in DOM or payload; each shipped source lights its strip within pipeline lag.
- Consistency drill: every unified figure matches its origin at the same `generated_at`; CSV reconciles cell-for-cell including source columns.
- Attribution drill: RPC divides same-link same-window aggregates (fixture-verified).
- Honesty drill: lag labels present; gross-vs-net stated per row; no silent restatement anywhere.
- Violation drill: any dashboard-side net computation or money table fails review.

## Out of scope

- Aggregation fan-out and CSV streaming (backend plan); unified table screens (frontend plan); analytics aggregates (profile/audience database plans); order/product/payout/billing storage (shop/monetize majors); campaign/ad accounting; agency reporting (P4).

## Open questions

- Q1: Creator-set revenue targets with progress bars — v1 motivator or v2 gamification?
- Q2: Purchase-day attribution confirmed, or does finance want click-day?
