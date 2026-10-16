# Share and SEO - API Contracts and Payloads (part 1: public surfaces)

Phase 2 TL · Public SSR routes + preview surfaces. Part 2 (`04-api-contracts-and-payloads-part-2.md`): internal consumption, additive extensions, events, governance. Problem bodies follow RFC 9457; the public error surface is intentionally minimal.

## Route map

| Method & path | Auth | Success | Errors | Cache-Control |
|---|---|---|---|---|
| `GET /{username}` | none | 200 SSR bio | 301 rename; 404 parity; 503 origin | 200: `public, s-maxage=120, stale-while-revalidate=600`; 301: `public, max-age=3600`; 404/503: `private, no-store` |
| `GET /{username}/cv` | none | 200 SSR resume view | 404 parity (no snapshot per feature 17's `resume_absent`); 503 | 200: same as bio + text parity with the featured template (layout cues only; feature 18); `/cv` head carries `noindex` + canonical to the bio URL |
| `GET /{username}/shop` | none | 200 SSR shop page (live products, sorted, feature 22) | 404 parity (private/missing/retired per feature 22 BR-09); 503 | 200: same as bio; canonical `https://{host}/{username}/shop` |
| `GET /{username}/shop/{productId}` | none | 200 SSR product page (preview; paywall locked body never included, feature 23) | 404 parity (missing/not-live/foreign target, feature 23); 503 | 200: `s-maxage=120` + SWR; no personalization |
| `GET /c/{code}` | none | 200 certificate verification page (name, course, date, feature 24) | 404 parity (unknown/revoked); 503 | 200: `s-maxage=60`; no personalization |
| `GET /robots.txt` | none | 200 `text/plain` | - | `public, max-age=3600` |
| `GET /sitemap.xml` | none | 200 `application/xml` (public slugs only, `lastmod`) | - | `public, max-age=3600` (purged on visibility changes) |
| `GET /app/preview/{username}` | session (owner) | 200 SSR preview HTML | 401; 404 parity for non-owner | `private, no-store`, `X-Robots-Tag: noindex` |
| `GET /app/preview/{username}.json` | session (owner) | 200 `SeoHeadModel` | 401; 404 parity | `private, no-store` |

Note on the 301 conflict in legacy sources: redirect responses were listed both as `no-store` and as cacheable. Resolution (normative): a rename is permanent and insert-only (feature 06 rows), so `301` is cached `public, max-age=3600`; only 404s and previews are `no-store`.

## Tag model (all server-rendered, all escaped)

| Tag | Value |
|---|---|
| `<title>` | `seoTitle` else display name |
| meta description | `seoDesc` else derived from bio (truncated <= 160 chars) |
| canonical | `https://{host}/{username}` (bio); on a verified custom host, canonical = that host (feature 04); `/cv` -> canonical to the bio URL |
| OG | `og:type=profile`, `og:title`, `og:description`, `og:url`, `og:image` (avatar else default) |
| Twitter | `twitter:card=summary_large_image` + title/description/image |
| JSON-LD | `Person`: `name`, `url`, `image`, `description` (v1 field set; `sameAs` revisit) |

Fixture tests assert the exact tag set per profile state (public, missing description, no avatar). The SPA-fallback-without-tags response is the named failure mode: route tests fail if any public 200 lacks the marker comment `<!-- ssr -->`.

## Preview surfaces (dashboard-only usage)

- `GET /app/preview/{username}` renders owner-scoped **saved** state (owner mode; see part 2), badged `Preview - not public`, sandboxed by the embedding SPA. Unsaved editor selections preview only in dashboard chrome (feature 09); no draft store exists.
- `GET /app/preview/{username}.json` returns the head model:

```kotlin
data class SeoHeadModel(
    val title: Boolean, val description: Boolean, val canonical: Boolean,
    val og: Boolean, val twitter: Boolean, val jsonLd: Boolean,
    val inSitemap: Boolean, val isPublic: Boolean,
)
```

- Neither surface is linked from public pages; both are `no-store`; the JSON form never includes profile content.

## Rendering contract

Zero JS ships on public routes (no hydration markers). Block text is escaped text-with-links; theme output is token-derived CSS only; schemed URLs (`javascript:`, `data:`) cannot reach render (authoring rejects them; renderer re-validates `https:` as defense in depth). `lang` attribute from host config.

## What the client never gets

No purge endpoint (probe -> 404), no cache-admin route, no internal URLs. Share/QR are client-side utilities that encode the canonical URL only.
