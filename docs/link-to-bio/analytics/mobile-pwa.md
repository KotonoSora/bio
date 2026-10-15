# Mobile Analytics (PWA shell, same numbers everywhere)

PLAN ref: P1d-2 (metrics) + P0 stack note (PWA install prompt instead of native mobile app — flagged reconciliation: link.me's "full-featured mobile apps" are met with one PWA + responsive dashboard, not iOS/Android binaries).

## Purpose

Deliver the full analytics experience on phones without building or maintaining native apps: the same endpoints, the same figures, arranged for a small screen and installable as a PWA. A creator checking a launch from the venue floor sees exactly what the desktop dashboard shows — arrangement collapses, values never differ.

## Users

- Creator on mobile: *As a Creator on mobile, I want to check overview, per-link performance, audience splits, realtime, and earnings from a phone so that a launch can be followed from the venue floor with exactly desktop figures.* Checks overview, per-link performance, audience splits, realtime (pro), and earnings (where shipped) from a phone, including from an installed PWA icon.
- Creator on desktop: *As a Creator on desktop, I want the identical figures in a wider arrangement so that no mobile-only or desktop-only metrics cause mismatched reads.* Sees the identical figures in a wider arrangement — no mobile-only or desktop-only metrics.
- Platform (maintenance): *As a Platform maintainer, I want one dashboard codebase with responsive layouts so that web, iOS, and Android do not require three shipped codebases.* Ships one dashboard codebase with responsive layouts instead of three (web + iOS + Android).

## Functional requirements

1. PWA shell: install prompt, app manifest (icon, name, theme), service worker caching the dashboard shell + static assets only — metric data is never cached in the service worker (always fresh from the APIs with their own cache headers). Offline shows the shell with a stale-data warning, never fabricated numbers.
2. Same-API guarantee: every mobile widget calls the identical endpoints as desktop (`overview`, `links/performance`, `audience`, `realtime`, `unified`) with identical range-clamping and plan gating. No mobile-specific aggregation endpoints, no divergent formulas.
3. Mobile-first widget order: overview → now-line (pro) → top links → revenue strip (where available) → audience splits; charts downsample points for small screens (server `?points=` cap) while totals stay exact — fewer dots, same sums.
4. Touch + performance contract: 44 px minimum targets, thumb-reach primary actions, dashboard interactive on a mid-range Android device within the public-page performance spirit (code-split analytics bundles, realtime polling paused when the tab is hidden).
5. Push in v1: none. Launch-night checking is pull (open the PWA, see the now-line). Alert/push thresholds are named v2 work (see the realtime doc's Q1) — no notification infrastructure is specified or built here.
6. Parity checklist with link.me mobile analytics: views, per-link clicks, source/city, engagement, earnings + sales performance, realtime window — each verified present on a 360 px viewport at feature exit.

## Data / API touches

- No new tables or endpoints. New artifacts only: `manifest.webmanifest`, service-worker script (shell + assets precache, API paths explicitly network-only), and responsive dashboard routes under the existing `/app/*` SPA. All data reads reuse the analytics endpoints with owner scoping and plan clamping unchanged.
- Install-prompt analytics (prompt shown/accepted/dismissed) ride the existing beacon event types — no new pipeline.
- QA matrix: mid-range Android Chrome + iOS Safari (installed + browser) × free/pro accounts, each verifying identical figures vs desktop at the same `generated_at`.

## Acceptance criteria

- Install drill: PWA installs from Chrome Android and iOS Safari share-sheet with correct icon/name; offline open shows shell + stale warning, never numbers presented as fresh. *GWT: Given an offline open of the installed PWA; When the shell renders; Then it shows the shell + stale warning and never presents numbers as fresh.*
- Same-numbers drill: scripted fixture reads identical across desktop, mobile browser, and installed PWA for all five analytics surfaces (overview, links, audience, realtime, unified). *GWT: Given scripted fixtures at the same `generated_at`; When read across desktop, mobile browser, and installed PWA; Then all five analytics surfaces are identical.*
- Performance drill: analytics bundle for the mobile route stays within budget; background-tab polling pauses (verified via request logs). *GWT: Given a backgrounded dashboard tab; When request logs are checked; Then realtime polling pauses and the mobile-route bundle stays within budget.*
- Service-worker drill: API responses are never served from worker cache (cache-storage inspection); shell/assets are. *GWT: Given cached shell/assets and fresh API data; When cache storage is inspected; Then API responses are never served from worker cache while shell/assets are.*
- Security drill: session handling unchanged (cookie session, RLS/owner scoping); IDOR probes on mobile routes behave identically to desktop. *GWT: Given IDOR probes on mobile routes; When evaluated; Then they behave identically to desktop with cookie-session RLS/owner scoping intact.*

## Out of scope

- Metric definitions and pipelines (see `analytics/profile-analytics.md`, `audience-insights.md`, `realtime-global.md`, `earnings-dashboard.md`); public-page mobile rendering and deep links (see `public-page/deep-links-mobile.md`).
- Native iOS/Android apps (explicitly not built — PWA decision stands); push notifications and alert thresholds (v2); agency mobile views (P4).

## Open questions

- Q1: PWA-only forever, or revisit a thin native wrapper if store presence proves to drive installs?
- Q2: Tablet/landscape arrangements — dedicated layouts, or fluid desktop-grid collapse?
