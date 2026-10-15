# Ad Serving Measurement Gated Frontend Plan (flight reports + dispute views — buildable at plan update)

PLAN ref: none — P4-growth, GATED. BA source: `docs/monetize/advertising/ad-serving-measurement.md`. Server truth in the serving-measurement backend plan above (decisioning, pipeline, equation — never redefined here).

## Purpose

Hold the buildable report UX: flight reports with exclusion transparency, dispute decomposition, and collapse-behavior verification surfaces. Nothing here renders before the plan update.

## Users

- Frontend developer (future): builds flight reports + ops dispute views.
- QA (now): asserts zero ad-report UI exists; (future) transparency/dispute drills.
- Advertiser/dispute reviewer (future): trusts numbers enough to renew; replays any billed event.

## Functional requirements

1. Surfaces (future): advertiser flight report (impressions, clicks, CTR, exclusion counts per reason, evidence-decompose entry); ops dispute view (raw sample within TTL, filter decisions per class, quarantine decide); collapse-verification panel (slot render telemetry: decided/collapsed/timeouts — the proof ads never slow pages).
2. Step 1 — report flow: flight select → aggregates (curve + breakdowns) + exclusion panel (`raw − excluded = reported` balancing visibly) + evidence-decompose per figure (one click to the bundle: creative, flight, placement, segment, timestamps, dedupe outcome). No metric without its decomposition (rule, asserted by link coverage test — every number links somewhere).
3. Step 2 — quarantine flow: held traffic appears as `under review` with the ops-case reference (owners see the hold, not missing numbers); release/drop updates the exclusion panel on next fetch with decision attribution.
4. Step 3 — collapse proof flow: render telemetry shows decided vs collapsed vs timed-out slot renders with the 50ms budget line (advertisers see performance honesty alongside counting honesty — slow pages churn buyers faster than disputed counts).
5. Step 4 — modern UI discipline: responsive-first reports (360px baseline); CSV export with exclusion columns (reconciliation travels); empty flights show `No delivery yet` (never zero-charts implying failure).

## Data / API touches

- Screens → endpoints map (all GATED, absent now): reports → ops/advertiser report reads; dispute → replay lookup + quarantine decide. Session cookie + CSRF; cross-flight reads 403 by construction.

## Acceptance criteria

- Absence drill (now): zero ad-report UI and routes.
- Transparency drill (at build): exclusion math balances on-screen; every number decomposes.
- Quarantine drill (at build): holds visible with case refs; decisions propagate.
- Collapse drill (at build): telemetry proves budget adherence; no layout-shift regressions.
- Security drill (at build): cross-advertiser reads 403; raw browsing ops-only.

## Out of scope

- Pipeline/equation (backend plan above); aggregate/evidence contracts (database plan above); inventory/earnings; creator analytics; affiliate chains.

## Open questions

- Q1: Report granularity — daily grain enough, or hourly for live flights?
- Q2: Collapse telemetry public to advertisers, or ops-only?
