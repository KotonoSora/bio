# Earnings Dashboard Frontend Plan (unified funnel view — UI build steps)

PLAN ref: P1d-2 unified UX (one money-and-attention view; phase-gated strips; traceability). BA source: `docs/link-to-bio/analytics/earnings-dashboard.md`. Server truth in `docs/link-to-bio/analytics/earnings-dashboard-backend-plan.md` (fan-out, formulas, cache — never redefined here).

## Purpose

Give the frontend developer an ordered UI procedure for the unified view: funnel table, strip gating, traceability links, CSV download, and fast rendering of wide revenue histories. Follow steps 1–6 in order. Arrangement may collapse on small screens; values never differ (same endpoint, same payload, all surfaces).

## Users

- Frontend developer: builds `/app/analytics/earnings` against the backend plan's contracts.
- QA: executes the gating, consistency, speed, and honesty drills.
- Creator: answers "which links earn?" — funnel plus revenue tables in one screen.

## Functional requirements

1. Route and modern UI shell: `/app/analytics/earnings` (owner-only) reuses the overview tab's `FetchState`, footnotes, and range picker (same clamping behavior). Layout responsive-first: funnel table → daily revenue series → per-product units → audience embeds (same components as the analytics tabs, so numbers match exactly between tabs by construction, not by testing).
2. Step 1 — funnel table flow: mount → `GET unified?range=` → rows (link, views, clicks, CTR, units, revenue, revenue-per-click) with per-cell source+lag tooltips (hover/tap reveals `source · lag · generated_at`) → `not_yet_available` cells render as em-dash with `coming when X ships` caption (never zeros) → `source:"unavailable"` cells render `temporarily unavailable` with retry (per-cell retry refetches the view once, not a new endpoint).
3. Step 2 — strip gating flow: payload without money keys → engagement-only view (no greyed panels, no upsell skeletons inside the table — the upsell lives on the plan page, not in the data grid); each shipped strip appears with its source label the moment the payload carries it (no client-side strip registry to drift — rendering follows flags, literally `if (row.revenue !== undefined)`).
4. Step 3 — traceability flow: every figure links one hop to its origin (analytics tab with same range, or order list) — links carry the range param so the origin opens in the identical window (traceability that lands on a different window is broken traceability).
5. Step 4 — CSV flow: `Export CSV` → `GET unified.csv?range=` download (streamed; large histories show progress + cancel; cancel aborts the fetch — no orphaned server work since streaming is pull-based) → file includes `source,generated_at` columns (reconciliation happens in spreadsheets, supported by format).
6. Step 5 — fast-load rules: table paginates at 25 rows (per-product units likewise); series charts honor `?points=`; no interval refetch (data changes hourly — refetch on focus + manual refresh button only); realtime ticks (where shown) follow the realtime plan's poll discipline, never this view's.
7. Step 6 — honesty presentation: lag labels on every widget group; gross-vs-net per row exactly as labeled (no client-side net math — the client lacks fee data by design); `generated_at` shared with the other tabs (cross-tab mismatch at the same timestamp is a P0 bug).

## Data / API touches

- Screen → endpoint map: earnings tab → `GET unified?range=`; export → `GET unified.csv?range=`. Session cookie; `403` → re-login; `422` → clamp-back (shared picker behavior).
- Type mirror: `Unified {generatedAt, rows[{link, views, clicks, ctr, units, revenue, rpc, source, lag, grossOrNet}], totals}`. No client money math exists (grep-verified in review).

## Acceptance criteria

- Gating drill: pre-revenue shows engagement-only, zero money stubs in DOM or payload usage.
- Consistency drill: every figure equals its origin tab/ledger at same `generatedAt`; CSV reconciles.
- Speed drill: 500-link history paginates at 25; no focus-polling storms in request logs.
- Traceability drill: every figure's link lands on the origin in the identical window.
- Honesty drill: lag + gross/net visible per row; `not_yet_available` never zero.

## Out of scope

- Fan-out/formulas/cache (backend plan above); flag/formula contracts (database plan above); analytics tab internals; order/billing tables; campaign/ad accounting; agency reporting.

## Open questions

- Q1: Creator revenue targets with progress bars — v1 or v2 (see database plan Q1)?
- Q2: Per-cell retry — single refetch enough, or jump to origin tab instead?
