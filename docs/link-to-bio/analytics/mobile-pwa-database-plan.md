# Mobile PWA Database Plan (artifacts + same-API guarantee — no tables)

PLAN ref: P1d-2 metrics + P0 PWA-install-prompt-instead-of-native-apps note. BA source: `docs/link-to-bio/analytics/mobile-pwa.md` (reconciliation: one PWA + responsive dashboard instead of iOS/Android binaries). Metric definitions live in the profile/audience/realtime/earnings database plans (owned there, never redefined here). Serving in the mobile-pwa backend plan; screens in the frontend plan (both split next to this file). No migration — this slice creates zero tables and zero endpoints.

## Purpose

Record what the PWA slice stores (almost nothing) and the guarantee that makes it trustworthy: every widget calls the identical endpoints with identical clamping as desktop, so arrangement collapses while values never differ. No `CREATE TABLE` here by design — a mobile-specific aggregation table would fork the metric truth.

## Users

- Frontend developer: ships manifest + worker + responsive routes against the artifact contracts below.
- QA: executes the install, same-numbers, worker-cache, and matrix drills.
- Platform (maintenance): keeps one dashboard codebase instead of three.

## Functional requirements

1. Stored state: none in PG. New artifacts only: `manifest.webmanifest` (icon, name, theme — static, fingerprinted), service-worker script (shell + static-assets precache; API paths explicitly network-only), responsive dashboard routes under `/app/*` (client-side, no new server routes beyond the SPA fallback owned by the backend plan).
2. Same-API guarantee (the load-bearing rule): every mobile widget calls the identical endpoints as desktop (`overview`, `links/performance`, `audience`, `realtime`, `unified`) with identical range-clamping and plan gating. No mobile-specific aggregation endpoints, no divergent formulas — verified by the same-numbers drill, enforced by sharing the data-fetching layer between layouts (one hook library, two arrangements).
3. Metric data is never cached in the service worker: shell + static assets precache; API responses are network-only always. Offline opens the shell with a stale-data warning, never fabricated numbers (offline + cached API would present old numbers as fresh — the exact failure this rule prevents).
4. Install-prompt analytics (`prompt_shown/accepted/dismissed`) ride the existing beacon event types — counted by the analytics pipeline with its bot filtering, stored as aggregates there. No new pipeline, no new table.
5. QA matrix data needs: mid-range Android Chrome + iOS Safari (installed + browser) × free/pro accounts, each verifying identical figures vs desktop at the same `generatedAt` across all five surfaces (overview, links, audience, realtime, unified).
6. Growth: PWA adds zero server load beyond the same API calls desktop makes (plus negligible manifest/worker static hits, CDN-cached). Launch-night phone checking scales exactly like desktop dashboard reads.

## Data / API touches

- DDL: none — assertion: no `CREATE TABLE` for mobile state anywhere under `docs/link-to-bio/analytics/` for this slice (review greps it). Endpoints: none new — all five analytics read surfaces reused with owner scoping + plan clamping unchanged. Artifacts: `manifest.webmanifest`, `sw.js`, responsive `/app/*` routes.

## Acceptance criteria

- Absence drill: schema + OpenAPI diff before/after this slice shows zero tables, zero endpoints.
- Same-numbers drill: scripted fixture reads identical across desktop, mobile browser, installed PWA on all five surfaces.
- Worker drill: API responses never served from worker cache (cache-storage inspection); shell/assets are.
- Offline drill: installed PWA opened offline shows shell + stale warning, never numbers presented as fresh.
- Violation drill: any mobile-specific aggregation endpoint or worker-cached API response fails review.

## Out of scope

- Static serving and fallback routing (backend plan); shell/worker/screens (frontend plan); metric definitions and pipelines (their database plans); public-page mobile rendering (deep-links plans); native apps (explicitly not built); push/alerts (v2); agency mobile views (P4).

## Open questions

- Q1: PWA-only forever, or revisit a thin native wrapper if store presence drives installs?
- Q2: Tablet/landscape — dedicated layouts or fluid collapse?
