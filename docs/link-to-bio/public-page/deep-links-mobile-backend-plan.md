# Deep Links Mobile Backend Plan (anchors + escape banner — server build steps)

PLAN ref: P1c-1/P1c-2 (public SSR + cache envelope) + S-2/S-5/S-7. BA source: `docs/link-to-bio/public-page/deep-links-mobile.md`. Phasing per `docs/platform/delivery/execution-overview.md` (B1). Storage posture in `docs/link-to-bio/public-page/deep-links-mobile-database-plan.md` (renderer-only, nothing stored). URL allowlist in `docs/link-to-bio/blocks/link-blocks-backend-plan.md`; measurement in the analytics profile backend plan. Dashboard/QA surfaces in the deep-links frontend plan (splits next to this file).

## Purpose

Give the backend developer an ordered procedure for the mobile page contract: anchor builder, escape banner, micro-script serving, cache invariance, and tests. Follow steps 1–7 in order. No new tables, no new endpoints, no per-visitor cache variants.

## Users

- Backend developer: executes steps 1–7 in package `com.app.bio.ssr.links`.
- QA: runs curl, matrix, tap-target, cache, injection, and analytics drills.
- Mobile visitor (usually inside TikTok/Instagram/Zalo webviews): fast page, links that open where checkout/forms/video work.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — monolith renderer addition. Monolith pros: anchor builder shared by `/:username` and `/:username/cv`; banner ships inside the existing cached payload; no new deployable for markup. Microservices cons avoided: a mobile-render service would duplicate the SEO renderer and double cache keys/purge paths for a boolean-class difference. Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; none fire. Starters: web, validation, security, actuator, micrometer (+ thymeleaf from the share-seo plan).
2. Step 1 — `AnchorBuilder.kt#build(dest, label)`: input must already carry the `https:` allowlist pass from authoring (renderer asserts, drops otherwise — defense in depth); emit `<a href target="_blank" rel="noopener ugc">`; rewrite YouTube/TikTok/Instagram `https:` URLs to Universal/App-Link deep-link forms (native app when installed, system browser otherwise). Never emit `intent:`/`javascript:`/custom schemes from user input — only server-side rewrites of valid `https:`. Unit table covers youtube/tiktok/instagram/generic + hostile schemes.
3. Step 2 — `EscapeBanner` Thymeleaf fragment: SSR-included `Open in browser` control; server sniffs known webview UAs (Instagram, TikTok, Facebook, Zalo, Line) but the output is ONE boolean class on `<html>`, applied client-side — advisory only, never blocks content. Primary button uses the platform-correct escape URL (`x-safari-https://` where honored, plain `https:` share/copy elsewhere); clipboard-copy fallback always present (several webviews block programmatic escape).
4. Step 3 — `public/micro-script.js` (<5KB, no framework, CSP-compliant, no cookies/storage writes beyond beacon dedupe): applies the webview class, wires banner + copy fallback, fires `banner_show`/`escape_tap` beacon events. Served as a static asset with immutable headers; size asserted in the build (breach fails `./gradlew build`).
5. Step 4 — mobile-first template contract (enforced in template + build checks, not rows): responsive single column, `viewport-fit=cover`, ≥44px tap targets, system-font default (remote theme fonts opt in with `display=swap`), lazy thumbnails with intrinsic sizes, total blocking JS <50KB (SSR HTML + micro-script).
6. Step 5 — cache invariance: banner + attributes ship inside the existing `public, s-maxage` + SWR envelope from the share-seo backend plan; zero per-visitor SSR variants (byte-identical HTML across webviews — asserted in test). Measurement rides existing beacon event types — no new endpoint, zero per-hit PG writes.
7. Step 6 — security: scheme-injection payloads rejected 422 at authoring (S-2, owned by blocks); S-5 throttles on anonymous reads; CSP holds on both routes.
8. Step 7 — tests + container: curl test (anchors carry `target/_blank noopener ugc`, banner markup present, SEO tags unchanged); scheme table (hostile payloads never emitted); budget test (blocking JS ≤50KB, tap targets ≥44px at 360px); cache test (publish <2min, hit gate holds with banner); same image via `docker run` + `podman run` per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`.

## Data / API touches

- Storage: database plan above (none — renderer-only over `bio.profile_blocks` URLs). Endpoints: none new (banner/escape analytics reuse `POST /api/t/:username` event types). Artifacts: `AnchorBuilder.kt`, `EscapeBanner` fragment, `micro-script.js`, template budget checks.

## Acceptance criteria

- Curl drill: cold `curl` shows correct anchors + banner markup + unchanged SEO tags.
- Injection drill: `intent:`/`javascript:`/custom-scheme payloads → 422 at API; renderer never emits them.
- Budget drill: blocking JS ≤50KB in build output; tap targets ≥44px at 360px.
- Cache drill: byte-identical HTML across webviews; publish <2min; >95% hit gate holds.
- Analytics drill: banner-eligible visits report escape-taps as beacon events, zero extra PG writes.
- Container drill: digest pair boots, compose green, rollback green.

## Out of scope

- Storage posture (database plan above); dashboard mobile tab + matrix execution (frontend plan); URL allowlist (blocks backend plan); SSR tags/sitemap (share-seo backend plan); metrics dashboards (analytics plans); native apps; P4 OAuth; custom domains/shop.

## Open questions

- Q1: Per-link open-mode override in v1, or uniform external always?
- Q2: Banner copy — Vietnamese default with English fallback, or per-creator language?
