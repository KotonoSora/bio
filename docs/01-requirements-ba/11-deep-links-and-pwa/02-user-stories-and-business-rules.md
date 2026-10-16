# Deep Links and PWA - User Stories and Business Rules (feature 11)

Phase 1 BA · Stories seed `06-acceptance-criteria.md`; rules `BR-xx` are quoted by every later file.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Mobile visitor in an in-app webview | an `Open in browser` banner and working outbound links | checkouts, forms, and videos do not break inside the webview | Must |
| US-02 | Mobile visitor | fast page loads and easy-to-tap controls on a small screen | the page is usable on low-end hardware | Must |
| US-03 | Creator | links that behave correctly with zero configuration | I do not manage open modes per link | Must |
| US-04 | Creator | per-link deep-link feedback in the editor (read-only) | I know what a YouTube/TikTok/Instagram tap will do | Should |
| US-05 | Creator on mobile | the same analytics figures on my phone | I can follow a launch from the venue floor | Must |
| US-06 | Creator on mobile | install the dashboard as an app icon | launch-night checking is one tap | Should |
| US-07 | Creator on mobile | honest offline behavior | I am never shown stale numbers as if fresh | Must |
| US-08 | Platform maintainer | one responsive codebase, no native binaries | one release train and no metric divergence | Must |
| US-09 | QA / release owner | a device matrix and build budgets that block promotion | broken escape flows never ship | Must |

## Business rules

- **BR-01 Mobile-first contract.** Public page: single column, `viewport-fit=cover`, tap targets >= 44 px at 360 px, system-font stack default (remote theme fonts opt in with `display=swap`), lazy thumbnails with intrinsic sizes, total blocking JS <= 50 KB (SSR HTML + micro-script).
- **BR-02 Anchor semantics.** Every block link renders `target="_blank" rel="noopener ugc"`. YouTube/TikTok/Instagram `https:` URLs are rewritten server-side to Universal/App-Link forms (native app when installed, browser otherwise). No `intent:`/`javascript:`/custom schemes are ever emitted from user input; Android `intent:` only where it degrades cleanly to `https:`.
- **BR-03 Escape banner.** The SSR payload always includes the banner markup and copy-link fallback; webview detection is a single boolean applied client-side from `navigator.userAgent` (advisory - a miss never blocks content; no-JS webviews simply see no banner). Primary action uses the platform-correct escape form (`x-safari-https://` where honored); the copy fallback is always present.
- **BR-04 Cache invariance.** The same slug returns byte-identical SSR HTML across Safari, Instagram, and Zalo webviews: no per-visitor variants, no UA dimension in any cache key, detection output applied client-side only.
- **BR-05 Measurement.** `banner_show`, `escape_tap`, and install-prompt outcomes ride the existing beacon event types on the analytics pipeline (features 12-15); no new endpoint, no per-hit database writes attributable to this feature.
- **BR-06 Same-API guarantee (PWA).** Every mobile widget calls the identical analytics endpoints as desktop with identical range-clamping and plan gating; no mobile-specific aggregation endpoint or divergent formula exists. Charts may downsample points; totals stay exact.
- **BR-07 Worker rules.** The service worker is scoped to `/app/` only (a root-scope worker could intercept public SSR traffic - forbidden), precaches shell + fingerprinted assets, and treats every `/api/*` path as network-only always (allowlist interceptor; unlisted defaults to network). `/app/sw.js` is served `no-cache` (revalidation is the update mechanism). Offline shows the shell with a persistent stale-data warning - a number without a freshness label is a fabrication bug.
- **BR-08 Install flow.** `beforeinstallprompt` is deferred to a contextual moment (analytics overview); outcomes fire beacon events (`prompt_shown/accepted/dismissed`); iOS (no prompt API) gets a one-time dismissable share-sheet instruction card; install state hides the CTA.
- **BR-09 SEO isolation.** `/app/*` is `noindex` (`X-Robots-Tag` + meta) and never contains crawler-readable numbers; public SEO exists exclusively on `/:username` (feature 10). The SPA fallback never serves unknown public slugs.
- **BR-10 Budgets and gates are build-time.** Blocking-JS budget, tap-target audit, Lighthouse thresholds, and icon presence are automated checks; a missing PWA icon fails the build/boot, and the device matrix blocks promotion like the k6 gate.
- **BR-11 No new tables or endpoints.** A mobile-specific aggregation table or endpoint fails review; storage is zero and the API surface adds nothing.
- **BR-12 No push in v1.** Launch checking is pull (open the PWA, read the now-line); notification infrastructure is v2 and is never stubbed.
