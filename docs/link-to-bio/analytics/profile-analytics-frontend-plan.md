# Profile Analytics Frontend Plan (overview dashboard + integration — UI build steps)

PLAN ref: P1d-2 dashboard UX (totals, compare, per-link, sources/cities; realtime card as locked teaser on free). BA source: `docs/link-to-bio/analytics/profile-analytics.md`. Server truth in `docs/link-to-bio/analytics/profile-analytics-backend-plan.md` (ranges, codes, lag semantics — never redefined here).

## Purpose

Give the frontend developer an ordered UI procedure for the base analytics dashboard: routes, widgets, range handling, lag honesty, and fast-load behavior under traffic spikes (launch-night checking from phones). Follow steps 1–6 in order. Metric data is always network-fresh (never cached in the PWA worker); the shell may cache, numbers may not.

## Users

- Frontend developer: builds `/app/analytics/overview` against the backend plan's contracts.
- QA: executes the same-numbers, gating, and degraded drills.
- Creator: reads launch performance at a glance, on desktop or phone.

## Functional requirements

1. Route and performance shell: `/app/analytics/overview` (owner-only) lazy-loads its chart chunk (route-level code-split; analytics bundle excluded from the initial dashboard payload). Shared `FetchState` component (loading skeleton → content | empty | degraded) backs every widget. First paint shows skeletons, never spinners blocking the whole page; widgets resolve independently so a slow per-link table never holds up totals.
2. Step 1 — overview widgets flow: mount → parallel `GET overview?range=` + `GET links?range=` (default range 30d; range picker: 7/30/90d free, extended on pro per the audience plan's clamping) → render totals row (views, clicks, engagement with period deltas), daily series chart (server `?points=` cap respected — fewer dots on small screens, exact totals), per-link table (clicks, share bar), source/city breakdowns. Every widget footer shows `generated_at` + `hourly data` lag label (aggregates trail real time by design — copy states it once per widget group, not per number).
3. Step 2 — range/gating flow: out-of-plan range selection → server `422 range_clamped` → picker snaps back to the allowed range with `Available on your plan: …` text (no dead-end error state). Free accounts see the realtime card as a locked upsell panel — no realtime fetch is attempted (no 403s by design, no wasted polls).
4. Step 3 — fast-load rules (SEO-adjacent speed discipline applied to the dashboard): chart data downsampled server-side; tables paginated client-side at 25 rows with `show more` (never full-history DOM dumps); background refetch on window focus only (no interval polling on this tab — realtime polling lives in the realtime plan); tab-hidden → no requests at all.
5. Step 4 — modern UI states: empty (new profile: illustrated empty + `Share your page` CTA, never zero-charts that imply failure); degraded (consumer-lag banner from `lag_seconds` when stale: `Data delayed — last updated …`, numbers stay visible with the stale chip); error (`403` → re-login; `429` → cooldown text with retry timer).
6. Step 5 — SEO note (honest scope): dashboard routes are authenticated SPA (no SEO, `noindex`); SEO value flows to the public page (share-seo plans), where these numbers never leak (aggregates are owner-only — no public counters rendered from this data).
7. Step 6 — bot-adjacent UI honesty: bot-filtered traffic never appears (excluded server-side); the dashboard does not offer a `include bots` toggle in v1 (per backend Q1 default) — if the default flips, this step gains a toggle bound to a query param, not a new endpoint.

## Data / API touches

- Screen → endpoint map: overview tab → `GET overview?range=` + `GET links?range=` (identical calls on desktop, mobile browser, installed PWA — the same-numbers guarantee). All with session cookie; `403` → re-login; `422` → clamp-back flow.
- Type mirror: `Overview {generatedAt, totals{views, clicks, engagement, deltas}, series[{day, views, clicks}], links[{blockId, title, clicks, share}], sources[], cities[]}`. No client-side aggregation beyond display formatting (percentages derived from the same payload, never refetched differently).

## Acceptance criteria

- Same-numbers drill: scripted fixture reads identical across desktop, mobile browser, installed PWA at the same `generatedAt`.
- Gating drill: free 365d request snaps back with plan text; realtime card locked with zero realtime fetches in request logs.
- Speed drill: analytics chunk lazy (initial dashboard payload unchanged); 10k-row history paginates at 25; hidden tab issues zero requests.
- Degraded drill: forced consumer lag shows stale chip with numbers intact, never fabricated fresh numbers.
- Bot drill: no bot toggle exists in v1 UI (grep); bot traffic absent from fixtures.

## Out of scope

- Pipeline and endpoint logic (backend plan above); table DDL (database plan above); audience/CTR views (audience plans); live feed UI (realtime plans); unified earnings UI; PWA shell plumbing (mobile-pwa plans).

## Open questions

- Q1: Default range 30d for all plans, or 7d on mobile for speed?
- Q2: CSV export on this tab, or export-only from the unified earnings view?
