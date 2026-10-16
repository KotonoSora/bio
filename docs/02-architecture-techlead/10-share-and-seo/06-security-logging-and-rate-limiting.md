# Share and SEO - Security, Logging, and Rate Limiting (feature 10)

Phase 2 TL · Implements `BR-01`, `BR-03`..`BR-06`, `BR-09`..`BR-11` and the security rows of `docs/01-requirements-ba/10-share-and-seo/07-non-functional-requirements.md`. Baseline: `docs/02-architecture-techlead/06-public-profile/05-security-logging-and-rate-limiting.md` (auth model unchanged).

## Authentication and authorization

- Public routes: session-free by design; no cookies accepted or set; the gateway applies WAF + rate rules only.
- Internal calls: service JWT only (`aud` per target service, 60 s TTL, JWKS from gateway); raw client headers never trusted.
- Preview: gateway session required; the forwarded JWT `sub` gates owner mode in the owning services (06/08 validate; this service never receives or passes user ids in query strings). Non-owner -> parity 404 (no oracle).
- No structural write path: the service has no database and no write credentials; zero-write is enforced by absence, not review alone.

## Cache integrity (poisoning controls)

- Cache keys include the request host; an unknown host returns `404` before any cache interaction (host-header poisoning impossible); canonical host is config-driven and asserted in tests.
- Only server-rendered 200s are cacheable; 404s and authenticated responses are `private, no-store`; redirects cache only from insert-only rename rows (no open redirects - targets never come from query parameters).
- Purge is internal-only (event listener); no purge endpoint exists (client probe -> `404`).
- Internal DTO fields are enumerated; no wildcard projections can leak future columns into cached HTML.

## Output safety

- Server-rendered output is fully escaped; CSP header on all public routes; stored payloads are data-only (authoring enforced), and the renderer re-validates `https:` schemes as defense in depth; `embed` markup remains renderer-owned (no third-party script execution in v1 output).
- JSON-LD is generated from a fixed field set with escaped values (no injection via display name or bio).

## Rate limiting (gateway rules)

| Route | Rule | On breach |
|---|---|---|
| Public reads `/{username}`, `/{username}/cv` | 600/min per IP burst (edge cache absorbs steady traffic) | 429 `throttled` |
| `sitemap.xml` | 60/min per IP | 429 |
| `robots.txt` | 120/min per IP | 429 |
| `/app/preview/*` | 120/h per user | 429 |

## Logging, tracing, audit

- Access logs: `traceId, host, slug, status, cacheState (hit|miss|coalesced), durationMs`; no cookies exist, no bodies logged, preview URLs marked `no-store` and excluded from access-log sampling beyond counts.
- Purge audit: `ssr.purge.count{type}` + listener logs (event id, slugs affected); sitemap rebuilds logged with page counts only.
- Tracing: W3C `traceparent`; public sampling 1% (volume), 100% for purge, preview, and errors.

## Degrade matrix (fail safe, never fail open)

| Failure | Behavior |
|---|---|
| Redis down | cache bypassed (render direct); rate rules at the edge still hold; latency degrades, content stays correct |
| Internal service timeout | stale served within SWR; else branded 503 (never tagless HTML) |
| Event bus down | purge heals via 60 s TTL + SWR (<= 2 min promise holds) |
| Edge cache down | origin renders per request; single-flight still coalesces; capacity alarm fires |
| Template deploy regression | `<!-- ssr -->` marker check + fixture tests fail the release gate before rollout |

## Threat checklist

Existence oracle (parity shape/timing + timing-drift alert), cache poisoning (host-keyed keys, unknown-host 404, no-store classes), open redirect (rename-row-only targets), XSS (escaped output + CSP + scheme re-validation), enumeration via sitemap (public-only source), CSRF (no state-changing routes exist), secrets (env/vault only; no DB credentials exist at all), dependency risk (SBOM at release gate T-17).
