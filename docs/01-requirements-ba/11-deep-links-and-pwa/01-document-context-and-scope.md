# Deep Links and PWA - Context and Scope (feature 11)

Phase 1 BA · Feature 11 of 53 (link-to-bio, final feature of the major) · Implemented by `docs/02-architecture-techlead/11-deep-links-and-pwa/` (5 files). Merges two legacy slices: deep-link escape + mobile-first public page (renderer delta of `public-site-service`) and the PWA shell for the dashboard SPA. Consumers: feature 10 (SSR envelope, cache invariance), features 12-15 (beacon event types).

## Business objectives

| # | Objective | Measure |
|---|---|---|
| BO-1 | Links work where money flows: outbound taps escape in-app webviews into Safari/Chrome or the native app, so checkouts, forms, and videos stop breaking. | Device-matrix drill green with checkout/form success on the reference destination (AC-02). |
| BO-2 | Mobile-first speed: the public page is fast on low-end hardware inside the existing cache envelope. | Blocking JS <= 50 KB; tap targets >= 44 px at 360 px; page weight unchanged in the k6 gate (AC-03, AC-04). |
| BO-3 | Zero creator configuration: links behave correctly with no per-link settings; the banner is uniform. | No open-mode field exists anywhere; uniform-external default (AC-01). |
| BO-4 | Same numbers everywhere: the installed PWA and mobile layout show exactly the desktop figures. | Same-numbers drill across 5 surfaces at one `generatedAt` (AC-09). |
| BO-5 | No cache or privacy regression: webview detection never fragments the cache or persists a user agent. | Byte-identical HTML across webviews; UA never stored (AC-07). |

## In scope

- Mobile-first public page contract: single-column responsive layout, `viewport-fit=cover`, 44 px targets, system fonts (remote theme fonts opt-in with `display=swap`), lazy thumbnails with intrinsic sizes.
- Anchor semantics (`target="_blank" rel="noopener ugc"`) + server-side deep-link rewrites for YouTube/TikTok/Instagram (native app when installed, browser otherwise).
- Escape banner (SSR markup, advisory client-side detection) + copy-link fallback; `<5 KB` CSP-compliant micro-script; `banner_show`/`escape_tap` beacon events.
- PWA shell: manifest, service worker (scope `/app/` only), responsive analytics arrangement, install flow, offline stale-banner honesty, update pill.
- Build gates: JS budgets, tap-target audit, Lighthouse thresholds, icon boot check, device-matrix release gate.

## Out of scope

- Metric definitions, pipelines, dashboards (features 12-15); block/theme authoring (08/09); SSR tags/cache/purge machinery (10); resume, shop, leads, domains (their majors).
- Native iOS/Android apps (never - PWA stands); push notifications and alert thresholds (v2); per-link open-mode override (v1 uniform); agency mobile views (P4).

## Personas and RBAC matrix

| Role | CRUD Permissions | Description |
|---|---|---|
| Mobile Visitor (often in a webview) | Read public page; tap escape/copy | Sees a fast page; links open where they work; banner never blocks content. |
| Creator | Read-only mobile insights; no banner config exists | Links behave correctly without configuration; sees banner eligibility + escape counts. |
| Creator on mobile (PWA) | Same dashboard reads as desktop, identical figures | Checks analytics from a phone or the installed icon; offline shows shell + stale warning. |
| Platform maintainer | One responsive codebase, one release train | No native binaries; budgets and matrix gate every release touching this slice. |

## Open questions (non-blocking; v1 defaults)

| # | Question | v1 default | Revisit trigger |
|---|---|---|---|
| Q-01 | Per-link open-mode override (force in-app vs external) | uniform external always; no field exists | creator demand for in-webview flows (e.g. IG checkout embeds) |
| Q-02 | Banner copy localization | Vietnamese default with English fallback | per-creator language setting ships |
| Q-03 | Worker update UX | `Update available - restart` pill | silent updates prove safe at this release cadence |
| Q-04 | Device-matrix reference destination | first-party test checkout + form | external partner provides a stable sandbox |

Related specs: SSR envelope `docs/02-architecture-techlead/10-share-and-seo/03-api-contracts-and-payloads-part-1.md`; URL allowlist `docs/02-architecture-techlead/08-link-blocks/03-api-contracts-and-payloads.md`; beacon pipeline `docs/01-requirements-ba/12-profile-analytics/`.
