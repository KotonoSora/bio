# Link Analytics Frontend Plan (per-link dashboards + dispute views — UI build steps)

PLAN ref: P2 readout UX (per-link dashboard, CSV, exclusion transparency, affiliate/ad scoped views, dispute decomposition). BA source: `docs/url-shortener/tracking/link-analytics.md`. Server truth in `docs/url-shortener/tracking/link-analytics-backend-plan.md` (pipeline, filters, equation — never redefined here).

## Purpose

Give the frontend developer an ordered UI procedure for honest short-link numbers: per-link dashboards with exclusion transparency, CSV export, scan-share estimation labels, the dual-count explainer, and ops dispute decomposition. Follow steps 1–6 in order. Every number decomposes to evidence — the UI makes that decomposition one click away, always.

## Users

- Frontend developer: builds `/app/links/:code/stats` + ops dispute views.
- QA: executes the counting-display, transparency, and separation-label drills.
- Link owner / affiliate / dispute reviewer: reads, exports, and challenges figures.

## Functional requirements

1. Route and data honesty shell: `/app/links/:code/stats` (owner-only, code preselected from the manager handoff) renders opens/uniques curve, top referrers, device split, scan share, geo-country, and the exclusion panel (dropped counts per reason — bot, prefetch, dedupe, quarantine — with `raw − excluded = reported` shown as the reconciling equation, not a footnote). Shared `FetchState` + `generated_at`/lag footnotes with the other dashboards.
2. Step 1 — dashboard flow: mount with code param → `GET stats` → curve + breakdowns + scan share (labeled `estimated` wherever heuristic-derived — the label is part of the number's rendering, never a tooltip-only caveat) + exclusion panel → quarantined links show a `traffic held for review` banner with the ops-case reference (owners see the hold, not just missing numbers — missing numbers without explanation read as data loss).
3. Step 2 — scan-share flow: event-pack location codes render exact per-location splits (no estimation labels — attribution by code, not heuristic); organic QR traffic renders the estimated split with the method note (`absent referrer + mobile + camera-class UA`) one click away. The two are visually distinct (exact vs estimated never share a style).
4. Step 3 — CSV flow: `Export CSV` → streamed download with per-row source + `generated_at` + exclusion columns (exports reconcile in spreadsheets — the equation travels with the data); large histories paginate server-side, client never assembles full histories in memory.
5. Step 4 — dual-count explainer flow: links embedded in bio blocks carry an info line (`Opens here count short-link opens; bio clicks count block taps — both true, billed separately per your plan`) linking both dashboards with matched windows. This line prevents double-billing disputes by design (support answers from one matrix, per the BA contract).
6. Step 5 — affiliate/ad scoped views: `/r/` and `/a/` consumers see the same dashboard component filtered to their codes (same numbers, no cross-link visibility — scope enforced server-side; the client passes the caller context and renders what returns, never filtering client-side what the server already scoped).
7. Step 6 — dispute flow (ops): figure → `Decompose` → evidence view (raw-hit sample within TTL, filter decisions per hit class, quarantine rows with decide actions) → release/drop with reason (decisions write audit + update the dashboard's exclusion panel on next fetch). Raw-hit browsing stays ops-only (no creator path exists — asserted by route absence, not by hiding).

## Data / API touches

- Screen → endpoint map: stats tab → `GET /api/v1/links/:code/stats`; export → `GET /:code/stats.csv`; affiliate/ad → scoped stats endpoints; dispute → ops replay lookup + quarantine decide. Session cookie + CSRF; `403` → re-login (cross-link); `429` → cooldown.
- Type mirror: `LinkStats {generatedAt, opens, uniques, scans{estimated}, series[], referrers[], devices[], geo[], excluded[{reason, count}], quarantine?}`. Estimation labels are data (`estimated: true`), not presentation guesses.

## Acceptance criteria

- Display drill: seeded traffic matches backend aggregates cell-for-cell; exclusions listed per reason with the equation balancing.
- Estimation drill: heuristic scan shares labeled estimated; location-code splits unlabeled-exact; styles distinct.
- CSV drill: export reconciles with on-screen figures including exclusion columns.
- Dual-count drill: bio-embedded link shows the explainer with matched-window links to both dashboards.
- Dispute drill: decompose → evidence → release/drop updates the exclusion panel; raw browsing unreachable by creators (route probe 404s).
- Speed drill: large histories stream/paginate; no full-history DOM dumps.

## Out of scope

- Pipeline/filters/equation (backend plan above); aggregate/quarantine DDL (database plan above); mint/resolve (short-links plans); QR rendering (qr plans); creator dashboards (link-to-bio plans); affiliate/ad measurement internals; custom short domains.

## Open questions

- Q1: Exclusion panel placement — inline table section (current) vs separate transparency tab?
- Q2: Real-time (non-aggregated) short-link view — ever, or hourly aggregates suffice for spend decisions?
