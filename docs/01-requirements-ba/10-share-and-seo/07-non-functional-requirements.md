# Share and SEO - Non-Functional Requirements (feature 10)

Phase 1 BA · Thresholds are v1 gates; changing one requires a spec revision with a measured trigger.

## Performance (public surface)

| NFR | Target |
|---|---|
| NFR-01 public read p95 at 10k concurrent | <= 500 ms (k6 P1 gate) |
| NFR-02 cache-hit ratio | > 95% steady state; cold-miss storms coalesced by single-flight |
| NFR-03 render origin budget | <= 3 internal calls (resolve -> [redirect] -> blocks; theme catalog cached); 2 s per-call timeout; 5 s total render deadline |
| NFR-04 publish -> publicly visible | <= 2 min (purge + 60 s TTL heal + SWR revalidation) |
| NFR-05 sitemap fetch | p95 <= 1 s for up to 10M profiles (narrow scan, cached 1 h) |

## Caching

- HTTP: `s-maxage=120` (launch default inside the 60-300 s window) + `stale-while-revalidate=600` on public 200s; `private, no-store` on previews, 404s, and all authenticated responses.
- Service: page cache TTL 60 s, keyed by host + slug; purge deletes affected keys (old and new on rename); sitemap cache 1 h purged on visibility changes.
- Negative results are never cached publicly (no 404 poisoning); redirects are cached as permanent (301).

## Availability and scale

- Public read path: 99.95% monthly; degraded mode serves stale within the SWR window instead of failing; without stale, a branded 503 (never tagless HTML).
- Scale model: reads scale by cache, not origin; renderers are stateless and horizontally scalable (3 baseline replicas).
- The service holds no database; internal APIs are the only data dependency, each with bounded timeouts.

## Security and privacy

- Privacy parity: private/missing/retired are indistinguishable in status and timing class; no existence oracle on any public route.
- Zero writes on the render path (structural: no database); counting is the beacon pipeline (features 12-15).
- Server-rendered output is fully escaped (CSP header on public routes); stored payloads are data-only by authoring design.
- Public routes are session-free; preview is owner-scoped and `no-store` + `noindex`; the dashboard has no purge API.

## Accessibility and localization

- Public pages are semantic HTML with correct `lang`; all content readable with JS disabled; avatar `alt` from display name; contrast from theme tokens is asserted by the theme fixture tests (feature 09 registry).
- Canonical host and locale derive from the serving host config; no per-visitor cookies on public routes.

## Observability

- Metrics: `ssr.render.duration`, `ssr.cache.hit|miss`, `ssr.render.coalesced`, `ssr.purge.count{type}`, `ssr.render.deadline_exceeded`, `sitemap.age_seconds`, `ssr.result{result}`.
- Alerts: cache hit <90% for 15 min; p95 >500 ms for 10 min; purge lag >2 min after an event; oracle timing drift between private and missing 404s; sitemap older than 2 h.
