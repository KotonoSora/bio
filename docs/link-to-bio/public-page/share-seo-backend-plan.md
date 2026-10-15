# Share SEO Backend Plan (SSR routes + cache-first — server build steps)

PLAN ref: P1c-1 (SSR `/:username` + `/:username/cv` tags) + P1c-2 (cache-first s-maxage + SWR, purge, single-flight, robots/sitemap) + S-2/S-5/S-7. BA source: `docs/link-to-bio/public-page/share-seo.md`. Phasing per `docs/platform/delivery/execution-overview.md` (B1). Reads per `docs/link-to-bio/public-page/share-seo-database-plan.md` (patterns, indexes, no-write rule). Preview/publish UI in the share-seo frontend plan (splits next to this file).

## Purpose

Give the backend developer an ordered procedure for the crawler-visible read path: routes, tag emission, cache headers, purge wiring, stampede guard, and crawler surfaces. Follow steps 1–8 in order. Dashboard editing stays an uncached SPA; this file owns only the anonymous public surface.

## Users

- Backend developer: executes steps 1–8 in package `com.app.bio.ssr`.
- QA: runs curl-parity, publish, gate, stampede, and container drills.
- Visitor/crawler: cold-loads public URLs with no JS and sees full content plus tags.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — monolith SSR origin inside the Spring artifact behind a cache/CDN proxy. Monolith pros: one renderer reading profile + blocks + theme tokens in one request scope; one purge path; one anchor builder shared by both routes. Microservices cons avoided: splitting SSR would fragment cache keys and purge coordination for a read path whose asymmetry is solved by caching. Split trigger from `docs/platform/architecture/architecture-decision.md`: top-slug ISR pre-render at P5 on measured origin pressure — this slice keeps synchronous render + single-flight so the move is additive. Starters: web, validation, security, actuator, micrometer (+ flagged additions below).
2. Step 1 — `SsrController.kt`: `GET /{username}` (bio: visible in-window blocks ascending, resume section when `show_resume`, badge + cached total, token-derived CSS only) and `GET /{username}/cv` (resume snapshot text via the resume module's read interface). Resolution order per slug: profile read → hit renders; miss → redirect lookup → hit 301s; miss → 404. Private → 404 (same shape as missing). Never serve the `/app/*` SPA fallback with tags. Static `/assets/*` served before this handler.
3. Step 2 — `SeoTagBuilder.kt`: emits `<title>` (seoTitle ?: displayName), meta description, canonical (per serving host), OG/Twitter tags, JSON-LD `Person` — all server-rendered, XSS-escaped. Unit test asserts the exact tag set on a fixture profile; SPA-fallback-without-tags is the named failure.
4. Step 3 — `SsrCacheFilter.kt`: `Cache-Control: public, s-maxage=120, stale-while-revalidate=600` (120 is the launch default inside the 60–300 window); private/404/redirect → `private, no-store`. Key `ssr:{host}:{slug}:{themeRev}:{snapshotRev}`. Realtime/short-poll responses never enter this cache (`private, max-age=10`, owned by analytics).
5. Step 4 — `SsrPurgeListener.kt`: `@EventListener(ProfilePublished)` + snapshot-saved event → `CachePort.purge(ssrKeysFor(username))`. Profile, block, theme, avatar, badge, and rename changes all publish through this one event type — one purge path, not six.
6. Step 5 — `RenderSingleFlight.kt`: `renderOnce(key, ttl=5s)` via cache-layer lock `render:<slug>`; concurrent cold misses coalesce to one render. QR encodes the same `/:username` URL — no parallel short system.
7. Step 6 — `CrawlerController.kt`: `GET /robots.txt` (static + sitemap pointer); `GET /sitemap.xml` (public-only slugs, paginated, cached 1h). Starter addition flagged once here: `thymeleaf` (fragments `profile/head`, `profile/blocks`, `profile/seo-tags`) + `caffeine` (Spring Cache abstraction over the container-portable cache layer).
8. Step 7 — abuse: Turnstile + WAF/rate on anonymous reads (S-5); S-2 strip + CSP on both routes; scheme-injection writes rejected 422 at authoring (B0), so the renderer only rewrites valid `https:`.
9. Step 8 — tests + container: curl-parity test (no-JS fetch asserts tags + block text on both routes); publish test (edit → live <2min); k6 P1 gate (10k concurrent reads, p95 <500ms, >95% hit); stampede test (N cold misses → one render); sitemap test (public-only). Same image via `docker run` + `podman run` per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`.

## Data / API touches

- Reads: database plan above (3 indexed reads, enumerated columns, 5s timeout, zero writes). Writes on this path: none.
- Routes: `GET /:username` → 200 SSR / 301 / 404 with the exact `Cache-Control` per case; `GET /:username/cv` → 200 snapshot; `GET /robots.txt`, `GET /sitemap.xml`. Purge inputs: `ProfilePublished` + snapshot-saved events; lock key `render:<slug>` 5s TTL.

## Acceptance criteria

- Curl drill: cold `curl` on both routes → 200 with title/meta/canonical/OG/Twitter/JSON-LD + block text; SPA fallback without tags is failure; `/assets/*` serves static directly.
- Publish drill: edit → live <2min; private 404 + sitemap absence; renames 301 through cache.
- Gate drill: P1 k6 green with >95% hit including banner payload (deep-links plan).
- Stampede drill: single render executed, rest coalesced.
- Sitemap drill: public-only slugs; robots served.
- Container drill: digest pair boots, compose green, rollback green, OpenAPI diff clean.

## Out of scope

- Read patterns and index contracts (database plan above); preview/publish UI (frontend plan); authoring (profile/blocks/themes plans); deep-link escape + budgets (deep-links plans); beacon/aggregates (analytics plans); resume semantics; shop/domains/leads; P4/P5 except the recorded ISR trigger.

## Open questions

- Q1: Canonical host — apex `/:username` only, or also indexed `/:username/cv`?
- Q2: Launch `s-maxage` 120s — hold, or start 60s while churn is high?
