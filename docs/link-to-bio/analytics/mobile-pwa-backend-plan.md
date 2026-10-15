# Mobile PWA Backend Plan (static serving + SPA fallback — server build steps)

PLAN ref: P0 PWA-install-prompt note + P1d-2 (metrics ride existing endpoints) + S-1/S-6/S-7. BA source: `docs/link-to-bio/analytics/mobile-pwa.md`. Phasing per `docs/platform/delivery/execution-overview.md` (B2). Artifact contracts in `docs/link-to-bio/analytics/mobile-pwa-database-plan.md` (manifest + worker + same-API guarantee; zero tables/endpoints). Shell and screens in the frontend plan (splits next to this file).

## Purpose

Give the backend developer an ordered procedure for serving the PWA: static asset handling with correct cache headers, the SPA fallback that never steals public slugs, security headers for the dashboard, and the proof that API freshness survives the worker. Follow steps 1–6 in order. This is a thin slice — thin by design, because every metric decision lives in the analytics backend plans.

## Users

- Backend developer: executes steps 1–6 (mostly `WebMvcConfigurer` + security headers, no business logic).
- QA: runs fallback-routing, header, worker-cache, and SEO-isolation drills.
- Creator on mobile: installs the PWA and sees identical figures.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — monolith-served PWA (static shell + same REST surface). Monolith pros: one session/RLS story; identical endpoints for all layouts; one release train. Microservices/native cons avoided: iOS/Android binaries triple maintenance for zero metric divergence; a mobile BFF risks divergent formulas. Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; none fire — PWA decision stands. Starters: web, security, actuator, micrometer. High-load posture: static hits are CDN/fingerprint-cached (origin rarely touched); API freshness rules from the analytics plans apply unchanged.
2. Step 1 — static handling (`WebMvcConfigurer#addResourceHandlers`): `/assets/**` + fingerprinted bundles → `CacheControl.maxAge(365d).immutable()`; `manifest.webmanifest` → immutable (content-hashed filename or versioned query); `/sw.js` → `CacheControl.noCache()` (browser MUST revalidate — a long-cached worker strands users on stale shells; this header is the update mechanism). Fast page loads start here: immutable fingerprinted assets + CDN edge hits, zero revalidation traffic.
3. Step 2 — SPA fallback routing (order-critical): static → `/api/**` → SSR `/:username` variants (share-seo backend plan) → `/app/**` forwards to `index.html` → anything else 404. The fallback MUST NOT serve `index.html` for unknown public slugs (that would fake SEO tags — the share-seo curl-parity test guards this boundary). `/app/*` responses carry `X-Robots-Tag: noindex` (dashboard has no SEO; authenticated SPA must never be indexed).
4. Step 3 — dashboard security headers: CSP for the SPA (worker registration requires `worker-src 'self'`; `connect-src 'self'` so analytics calls cannot leak to third parties); cookie session unchanged (`HttpOnly`+`Secure`+`SameSite=Lax`); CSRF tokens for dashboard writes as elsewhere. Service-worker scope limited to `/app/` (a root-scope worker could intercept public SSR traffic — explicitly forbidden).
5. Step 4 — install-prompt support: `beforeinstallprompt` is client-side; the server's only job is correct manifest MIME (`application/manifest+json`) + icons present at the declared paths (startup self-check fails boot on missing icon — broken install flow is a release-blocker, caught here not in QA).
6. Step 5 — API-freshness guarantee (server side): analytics endpoints emit `private, no-store`-family headers where the worker could otherwise cache opportunistically (defense in depth behind the worker's network-only config); `Vary: Cookie` on owner reads so shared caches never cross users.
7. Step 6 — tests + container: fallback-routing test matrix (public slug → SSR/404, never SPA; `/app/deep/link` → index; `/api` untouched); header test (immutable assets, no-cache worker, noindex on `/app/*`, manifest MIME); worker-scope test (registration scope `/app/`); boot self-check test (missing icon fails fast). Same image via `docker run` + `podman run` per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`.

## Data / API touches

- Schema/endpoints: database plan above (none new). Static routes: `/assets/**` (immutable), `/sw.js` (no-cache), `/manifest.webmanifest`, `/app/**` → `index.html` + `noindex`. Metrics: static hit ratio (CDN), worker version adoption (from install-prompt beacon events).

## Acceptance criteria

- Routing drill: unknown slug never receives SPA HTML; `/app/*` deep links boot the SPA; `/api/*` unaffected.
- Header drill: immutable/no-cache/noindex/MIME all exact; worker scope `/app/`.
- Freshness drill: analytics responses carry anti-cache headers; worker cache-storage holds zero API responses.
- Boot drill: missing PWA icon fails startup (not QA).
- Container drill: digest pair boots, compose green, rollback green.

## Out of scope

- Artifact contracts (database plan above); shell/worker/screens (frontend plan); metric logic (analytics backend plans); public SSR (share-seo backend plan); native apps (never); push (v2).

## Open questions

- Q1: Worker update cadence — prompt users on new worker, or silent background update?
- Q2: `/app/` noindex via header enough, or also `robots.txt` disallow (defense in depth)?
