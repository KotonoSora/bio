# Share and SEO - Database and Cache Design (feature 10)

Phase 2 TL · **Zero migrations, zero database**: the public-site service owns no schema (the chain from V1-V3 lives in `profile_db` and stays untouched). This file documents the cache topology and the read path it replaces. Implements the `BR-*` rules of `docs/01-requirements-ba/10-share-and-seo/02-user-stories-and-business-rules.md`.

## No-database assertion

- `grep -rn "CREATE TABLE" docs/02-architecture-techlead/10-share-and-seo/` returns nothing; no datasource is configured in production topology; no JDBC driver is on the classpath (build gate). Data-modifying statements are impossible, not merely forbidden.

## Cache topology (Redis database 5; final numbering pinned by the platform baseline)

| Key | Value | TTL | Purged by |
|---|---|---|---|
| `ssr:{host}:{slug}` | rendered HTML for the bio page | 60 s | `profile.published.v1`, `username.renamed.v1` (old + new slug) |
| `ssr:{host}:{slug}:cv` | rendered HTML for the resume view | 60 s | same events + `resume.cv_changed.v1` (feature 17) |
| `ssr:{host}:{slug}:shop` | rendered HTML for the shop page (feature 22) | 60 s | `profile.published.v1`, `username.renamed.v1`, `shop.catalog_changed.v1` |
| `ssr:{host}:{slug}:shop:{productId}` | rendered product/paywall preview page (feature 23) | 60 s | `shop.catalog_changed.v1` (product prefix), `profile.published.v1`, `username.renamed.v1` |
| `ssr:cert:{code}` | certificate verification page (feature 24) | 60 s | TTL-only; revocation heals ≤ 60 s |
| `render:{slug}` | single-flight lock (`SET NX PX 5000`) | 5 s | natural expiry |
| `sitemap:xml` | full sitemap document | 1 h | any `profile.published.v1` (visibility changes) |
| `themes:catalog` | theme catalog from feature 09 | 300 s | TTL (registry is deploy-versioned) |

L1: Caffeine per replica (<= 10 s TTL, 10k entries) absorbs micro-stampedes; purge deletes L2 only - L1 self-heals within 10 s, inside the 2 min promise. HTTP layer: `s-maxage=120` + SWR 600 handled by the Nginx edge, not by the service.

## Read path (internal APIs only; no cross-service reads)

1. Resolve `GET /internal/v1/profiles/{username}` (06): `profile` / `redirect` / `missing`; private = `missing` parity. On `redirect` -> `301` immediately (no further calls).
2. Blocks `GET /internal/v1/profiles/{username}/blocks` (08): windowed, ordered, render-ready.
3. Theme catalog `GET /internal/v1/themes` (09): cached in `themes:catalog`; unknown `themeId` -> one fresh fetch -> default theme.
4. Resume view `/{username}/cv`: consumed contract `GET /internal/v1/resumes/{username}/published` (feature 17); `404 resume_absent` (missing, private, draft-only, retired - parity shape) renders the `/cv` parity `404`.
5. Shop page `/{username}/shop` (feature 22): `GET /internal/v1/shops/by-profile/{profile_id}/products` after the resolve (live, sorted; empty list renders the empty state). Bio renders with `product|course|tip` blocks spend one extra batch call `GET /internal/v1/products:public?ids=` (<= 50 ids; omitted ids render the unavailable state).
6. Product/paywall page `/{username}/shop/{productId}` (feature 23): `GET /internal/v1/products:public?ids=` (single id) after the resolve; preview-only render (feature 23 BR-10); the locked body is never part of this response - it is fetched as a session fragment (`/api/v1/digital/{productId}/body`, `private, no-store`). Course products add one call `GET /internal/v1/courses/by-product/{productId}/syllabus` (preview lessons only; never gated media). Tip products add one call `GET /internal/v1/tips/{productId}/supporters` (approved wall only).

Budget: <= 3 calls per render (resolve -> [redirect] -> blocks; catalog cached), 2 s timeout per call, 5 s total render deadline; shop render <= 2 calls (resolve -> products); bio render with product blocks <= 4 calls (resolve -> blocks -> products:public). Fields arrive enumerated in the internal DTOs - no `SELECT *` equivalent exists.

## Purge path

`SsrPurgeListener` consumes `profile.published.v1`, `username.renamed.v1`, `resume.cv_changed.v1` (cv keys only), and `shop.catalog_changed.v1` (shop page + product-page + bio keys for block availability, features 22-23): deletes the slug keys and slug-prefixed subkeys (both usernames on rename), deletes `sitemap:xml` on visibility changes, emits `ssr.purge.count{type}`. Lost events heal via TTL + SWR: worst case 60 s service TTL, re-render, revalidate.

## Verification drills

1. Curl-parity drill: cold `curl` on both routes -> tags + block text; SPA fallback without tags fails the drill.
2. Zero-write drill: sustained load with database monitoring -> zero data-modifying statements; build gate verifies no JDBC driver.
3. Oracle drill: private vs missing vs retired slugs -> identical status, headers, and timing class.
4. Cache drill: hit ratio >95% at the k6 gate; purge removes keys; TTL-only path heals <= 2 min.
5. Stampede drill: N concurrent cold misses -> one render; waiters served.
6. Violation drill: any PR adding a datasource or a render-path write fails review and the build gates above.
