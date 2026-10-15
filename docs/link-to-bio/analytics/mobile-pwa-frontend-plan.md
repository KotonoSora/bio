# Mobile PWA Frontend Plan (shell + screens + install — UI build steps)

PLAN ref: P1d-2 mobile UX + P0 PWA note (install prompt, no native apps) + fast-load and modern-UI mandates. BA source: `docs/link-to-bio/analytics/mobile-pwa.md`. Server truth in `docs/link-to-bio/analytics/mobile-pwa-backend-plan.md` (static headers, fallback routing, freshness — never redefined here). Contracts in `docs/link-to-bio/analytics/mobile-pwa-database-plan.md` (same-API guarantee).

## Purpose

Give the frontend developer an ordered UI procedure for the PWA: shell + worker behavior, responsive routes, widget order, install flow, and the speed budget that makes launch-night phone checking feel instant. Follow steps 1–7 in order. Values identical to desktop everywhere — arrangement collapses, numbers never differ.

## Users

- Frontend developer: builds shell, worker, routes, and install UX.
- QA: executes install, same-numbers, performance, worker, and matrix drills.
- Creator on mobile: checks all five analytics surfaces from a phone or installed icon.

## Functional requirements

1. Shell and speed architecture: route-level code-split (analytics chunk lazy, excluded from initial dashboard payload); shared data hooks with desktop layouts (one fetch library → same-numbers by construction); `FetchState` skeletons everywhere (no full-page spinners); `?points=`-aware charts; 25-row pagination with virtualization past 100 rows; realtime polling paused when `document.hidden`; no interval refetch on historical tabs (focus + manual refresh only).
2. Step 1 — manifest: `{name, short_name, icons 192/512 maskable, theme_color, background_color, display:standalone, start_url:/app/analytics}`; installability self-test in CI (Lighthouse PWA threshold as a build check, not a QA checklist item).
3. Step 2 — worker (`sw.js`): precache shell + fingerprinted assets on install (versioned cache name, old caches purged on activate); runtime strategy: shell/assets cache-first, API paths (`/api/v1/analytics/*`, `/api/t/*`) network-only ALWAYS (request interceptor allowlist, not URL-pattern hope — an unlisted path defaults to network, never cache); offline navigation → cached shell + `stale-data` banner (prominent, persistent until online — never a toast that implies freshness).
4. Step 3 — responsive routes: existing `/app/analytics/*` routes collapse per the widget order (overview → now-line (pro) → top links → revenue strip → audience splits); 360px baseline (tap targets ≥44px, thumb-reach primaries); tablet/landscape fluid collapse (no dedicated layouts in v1); charts downsample points, totals exact.
5. Step 4 — install flow: `beforeinstallprompt` deferred to the analytics overview (contextual moment: user checking a launch) → custom `Install app` button → accept/dismiss/ignored outcomes fire the beacon event types (`prompt_shown/accepted/dismissed`); iOS (no prompt API) gets the share-sheet instruction card once (dismissable, never nagging). Install state detected → button hidden (no dead CTA).
6. Step 5 — modern UI details: theme-color matches dashboard chrome (no white flash on launch); splash icon correct at all densities (startup self-check on the server side, visual check here); standalone display gets extra bottom padding for gesture bars;Realtime panel follows the realtime frontend plan's lifecycle (same component as desktop, narrower).
7. Step 6 — degraded/offline matrix: offline → shell + stale banner (numbers from the last successful fetch shown ONLY with the stale banner attached — a number without a freshness label offline is a fabrication bug); expired session → re-login preserving route (deep-link back post-login); worker update available → `Update available — restart` pill (silent update risks mid-read inconsistency during a launch check).
8. Step 7 — SEO isolation (frontend side): dashboard sets `noindex` meta + never renders SSR-friendly content for metrics (no crawler-readable numbers anywhere in `/app/*` — public SEO lives exclusively on `/:username` via the share-seo plans).

## Data / API touches

- Screen → endpoint map: all five surfaces call the identical analytics endpoints with identical ranges (hook library shared with desktop). Install events → beacon types. No new endpoints; no worker-cached API (asserted, not assumed).
- Cache inventory (worker): shell HTML, fingerprinted JS/CSS, icons, manifest. Explicitly NOT cached: any `/api/*`, `sw.js` itself beyond the browser's no-cache revalidation.

## Acceptance criteria

- Install drill: installs from Chrome Android + iOS share-sheet with correct icon/name/theme; prompt outcomes beacon-logged.
- Same-numbers drill: fixture identical across desktop/mobile-browser/installed-PWA on all five surfaces at same `generatedAt`.
- Speed drill: analytics chunk lazy (initial payload delta ≈ chunk size); Lighthouse PWA + performance thresholds green in CI.
- Worker drill: cache-storage holds zero `/api/*` responses; version bump purges old caches; offline shows shell + stale banner.
- Matrix drill: mid-range Android + iOS Safari × free/pro green per release touching this slice.

## Out of scope

- Static/fallback/header implementation (backend plan above); artifact contracts (database plan above); metric logic (analytics backend plans); public-page rendering (deep-links plans); native apps (never); push (v2); agency mobile (P4).

## Open questions

- Q1: PWA-only forever, or thin native wrapper if store presence drives installs?
- Q2: Silent worker updates vs restart pill (current: pill — hold)?
