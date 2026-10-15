# Deep Links + Mobile-First Page (open outside in-app browsers)

PLAN ref: P1c-1, P1c-2 (public SSR + cache). No new tables; behavior + markup contract on the existing `/:username` page.

## Purpose

Make every Link-to-Me page work where its traffic actually comes from: inside TikTok/Instagram/Zalo in-app browsers, on low-end mobile hardware. Two guarantees: the page itself is mobile-first fast, and outbound links escape the in-app browser into Safari/Chrome (or the destination's native app) so checkouts, forms, and videos work instead of breaking inside a webview.

## Users

- Visitor (mobile, usually inside an in-app webview): *As a mobile Visitor inside an in-app webview, I want the page to load fast and links to open where they work so that checkouts, forms, and videos do not break inside a webview.* Page loads fast, taps links that open where they work.
- Creator: *As a Creator, I want links to behave correctly without per-link configuration so that every outbound tap lands in a real browser or app context.* Links behave correctly without per-link configuration.
- Destination owner (shop checkout, video host): *As a Destination owner, I want traffic in a real browser/app context so that cookies and payments stay intact.* Receives traffic in a real browser/app context with cookies and payments intact.

## Functional requirements

1. Mobile-first page contract: responsive single-column layout, `viewport-fit=cover`, minimum 44 px tap targets, system-font stack default (theme fonts must opt into remote fonts with `display=swap`), image thumbnails lazy-loaded with intrinsic sizes, total blocking JS <50 KB on the public page. Dashboard SPA weight budgets do not apply here — this page is SSR HTML + a micro-script only.
2. In-app-browser detection + escape banner: server sniffs known webview UAs (Instagram, TikTok, Facebook, Zalo, Line) and SSR-includes an `Open in browser` banner (deep-link button + copy-link fallback). Detection is advisory only — banner never blocks content if detection misses.
3. Outbound-link semantics: all block links render `target="_blank" rel="noopener ugc"`; YouTube/TikTok/Instagram URLs additionally use provider deep-link forms where safe (`https:` Universal Links / App Links that open the native app when installed, system browser otherwise). No custom-scheme `intent://` hacks that break iOS; Android `intent:` form only where it degrades cleanly to `https:`.
4. `x-safari-https:` / Chrome-Intent escape: the banner's primary button uses the platform-correct escape URL (`x-safari-https://` on iOS contexts that honor it, plain `https:` share/copy elsewhere); clipboard-copy fallback always present because several webviews block programmatic escape.
5. Performance under cache: escape banner + link attributes are part of the SSR payload, so the cache-first story (`public, s-maxage=60-300, SWR=600`, purge on publish) is unchanged. No per-visitor SSR variants that would fragment the cache — detection output is one boolean-class on `<html>`, applied client-side from the micro-script, not separate cached pages.
6. Measurement: banner shows/impressions and escape-taps ride the existing beacon pipeline (`POST /api/t/:username` event types, see `analytics/profile-analytics.md`) — no new endpoint, no per-hit PG writes.

## Data / API touches

- No new tables or columns. Renderer changes only: link-anchor builder (shared by `/:username` and `/:username/cv`), banner component, and the public-page micro-script (`<5 KB`, no framework, CSP-compliant, no cookies/localStorage writes beyond the beacon dedupe already specified).
- URL policy: destinations must already pass the `https:` allowlist from `blocks/link-blocks.md`; deep-link forms are rewrites of valid `https:` URLs, never new accepted schemes from user input.
- QA matrix inputs (documented, not code): iOS Safari/Chrome/IG/TikTok/Zalo webviews × Android Chrome/IG/TikTok/Zalo webviews, each checked for banner presence, escape behavior, and checkout/form success on a reference destination.

## Acceptance criteria

- Cold-curl (no JS) on `/:username`: anchors carry `target="_blank" rel="noopener ugc"`; banner markup present; SEO tags from `public-page/share-seo.md` unchanged. *GWT: Given a cold-curl with no JS on `/:username`; When the SSR payload is inspected; Then anchors carry `target="_blank" rel="noopener ugc"`, banner markup is present, and SEO tags are unchanged.*
- Device-matrix drill: on each listed webview, banner appears and its primary action lands the reference URL in the system browser or native app; copy-link fallback always succeeds. *GWT: Given each listed webview; When the banner primary action or copy-link fallback is used; Then the banner appears and the reference URL lands in the system browser or native app with copy-link always succeeding.*
- Tap-target audit: every public-page interactive element ≥44 px on a 360 px viewport; blocking JS ≤50 KB verified in build output. *GWT: Given a 360 px viewport and the build output; When audited; Then every interactive element is ≥44 px and blocking JS is ≤50 KB.*
- Cache drill: publish → change live <2 min; cache-hit ratio gate (>95% at P1 k6) still met with banner included. *GWT: Given a publish with the banner included; When the cache is measured; Then the change is live in <2 min and the cache-hit ratio stays >95% at P1 k6.*
- No-scheme-injection probe: block payload with `intent:`/`javascript:`/custom-scheme URL rejected 422 at API (only server-side rewrites of valid `https:` URLs may emit deep-link forms). *GWT: Given a block payload with `intent:`/`javascript:`/custom-scheme URL; When submitted; Then the API rejects with 422 and only server-side rewrites of valid `https:` URLs emit deep-link forms.*
- Analytics drill: banner-eligible visits report escape-taps as beacon events without extra PG writes per hit. *GWT: Given banner-eligible visits with escape-taps; When reported; Then they arrive as beacon events without extra PG writes per hit.*

## Out of scope

- Block content model and scheduling (see `blocks/link-blocks.md`); social-account linking and follower totals (see `profile/verification-followers.md`); SSR tags/sitemap/single-flight (see `public-page/share-seo.md`); metrics dashboards (see `analytics/profile-analytics.md`).
- Native iOS/Android apps (PWA install prompt instead, per PLAN); full OAuth connect and posting (P4); custom domains and shop checkout internals (separate majors).

## Open questions

- Q1: Per-link open-mode override (force in-app vs external per block) — needed in v1, or uniform external always?
- Q2: Banner copy/localization — Vietnamese default with English fallback, or per-creator language setting?
