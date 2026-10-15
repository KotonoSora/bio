# Audience Insights Frontend Plan (segments + CTR views — UI build steps)

PLAN ref: P1d-2 audience UX (splits, UTM, CTR/rank/Movers, period compare; plan-window clamping). BA source: `docs/link-to-bio/analytics/audience-insights.md`. Server truth in `docs/link-to-bio/analytics/audience-insights-backend-plan.md` (derivations, CTR definition, clamping — never redefined here).

## Purpose

Give the frontend developer an ordered UI procedure for audience depth: dimension tabs, CTR/rank/Movers tables, overlay toggles, clamp-back behavior, and fast rendering of high-cardinality UTM data. Follow steps 1–6 in order. Values match the overview tab exactly at the same `generated_at` (same aggregates, same windows).

## Users

- Frontend developer: builds `/app/analytics/audience` against the backend plan's contracts.
- QA: executes the parity, gating, and cardinality drills.
- Creator: learns who returns, on what devices, from where, and which links earn taps.

## Functional requirements

1. Route and data discipline: `/app/analytics/audience` (owner-only) calls only the two audience endpoints (never recomputes CTR client-side from mismatched windows — CTR arrives computed; the client formats `ctr_x1000` as percent). Shared `FetchState` + `generated_at`/lag footnotes inherited from the overview tab's component library.
2. Step 1 — dimension tabs flow: tabs device/geo/referrer/UTM/returning → `GET audience?range=&dimension=` per active tab (one tab fetched at a time; switching cancels in-flight) → bar rows with share percentages + absolute views → referrer groups labeled (social/search/direct/scan) with icons; UTM tab adds campaign drill-down (key → per-campaign rows). New-vs-returning renders as a donut with the seen-before window noted (`returning = seen in window`, copy states the definition once).
3. Step 2 — CTR/rank/Movers flow: `GET links/performance?range=` → table (link, clicks, CTR, rank, delta-vs-previous) → Movers sub-table (biggest gainers/fallers with sparklines from the series). Rank ties share rank numbers (1,2,2,4 — server-computed, client displays).
4. Step 3 — compare flow: overlay toggles (previous-period, year-ago) → charts draw aligned overlays with `aligned` badge when weekday-shifted; deltas show absolute + percentage. The UI labels alignment explicitly so Monday spikes are not misread (copy owned by this step, not a tooltip afterthought).
5. Step 4 — clamp-back flow: shared with the overview tab (out-of-plan range → `422` → snap-back + plan text). Downgrade mid-session re-clamps the picker on next fetch (no stale 365d selection persists).
6. Step 5 — high-cardinality/fast-load rules: UTM tab paginates at 25 rows with server-side ordering (top campaigns first — no client sorting of full keyspaces); long-tail collapses into `Other (n campaigns)` computed server-side; chart points honor the `?points=` cap; tab content virtualizes beyond 100 rows (no full-DOM dumps on viral campaigns).
7. Step 6 — honesty states: empty dimension (no UTM links yet → `Tag links with utm_campaign to unlock this view` with a link to the block editor, not an empty chart); low-traffic CTR (`<30 views` shows `not enough data` instead of a noisy 100% CTR — threshold copy states the rule once).

## Data / API touches

- Screen → endpoint map: tabs → `GET audience?range=&dimension=`; tables → `GET links/performance?range=`. Same session-cookie + `403`/`422` handling as the overview tab.
- Type mirror: `Audience {generatedAt, dimension, rows[{key, views, share}], aligned}`; `LinkPerf {generatedAt, rows[{blockId, title, clicks, ctrX1000, rank, delta}], movers[]}`. CTR displayed from `ctrX1000` only.

## Acceptance criteria

- Parity drill: seeded traffic matches hand-computed splits/UTM/CTR cell-for-cell with the backend fixture.
- Gating drill: free 365d/hourly snaps back with plan text; no bypass via direct tab deep-link.
- Cardinality drill: 5k-campaign fixture paginates at 25 with server-ordered `Other` row; DOM node count bounded.
- Compare drill: weekday-shifted overlay shows the `aligned` badge; deltas show both absolute + percentage.
- Honesty drill: untagged profile shows the unlock CTA, not an empty chart; low-traffic CTR shows `not enough data`.

## Out of scope

- Projection/retention logic (backend plan above); table DDL (database plan above); beacon mechanics (base analytics plans); live feed UI; earnings unification UI; PWA plumbing.

## Open questions

- Q1: `Other` row threshold — top-25 fixed, or creator-adjustable?
- Q2: Low-traffic CTR threshold 30 views — hold, or plan-tiered?
