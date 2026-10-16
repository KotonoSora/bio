# Short Links - Non-Functional Requirements (feature 29)

Phase 1 BA · All thresholds are v1 gates; changing one requires a spec revision with a measured trigger.

## Performance (client-observed)

| NFR | Target |
|---|---|
| NFR-01 resolve end-to-end | p95 <= 100 ms warm; p99 <= 250 ms |
| NFR-02 resolve cache-hit at origin | p50 <= 20 ms |
| NFR-03 mint | p95 <= 300 ms |
| NFR-04 suspension effective platform-wide | <= 5 min (cache purge + 300 s TTL worst case) |
| NFR-05 list `/api/v1/links` | p95 <= 200 ms at 100 rows |

## Scale and availability

- Resolve sustains 10,000 rps peak read-only on stateless replicas; monthly availability target 99.95% for `/go/*`.
- Redis outage: resolve degrades to direct database reads (p95 <= 300 ms), availability preserved; quota checks fail closed (503).
- Data growth: one small row per mint (quota-bounded); clicks never write the link database; tombstones retained.

## Security and masking

- No raw IPs stored; throttle keys are salted SHA-256(IP) with 24 h window rotation; logs carry code + destination host only, no full URLs, no PII.
- TLS 1.2+ everywhere; HSTS; codes from a CSPRNG (no enumerable sequence).
- Rate limits: resolve 600 rpm/IP; mint 20/min/account + 60/min/IP; report 5/min/IP; Turnstile on mint and report.
- Audit events (bulk mint, suspend, quota override) append-only, retained 24 months.

## Session and quotas

- Dashboard session: 30-day rolling SSO cookie, revocable; ops actions re-verify role per request; ops session cap 12 h.
- v1 default mint quota: 500/account/month, plan-configurable; counter resets 00:00 UTC on the 1st; counter lives in Redis and is rebuilt from mints if lost.

## Localization

- Gone/suspension/404 pages localized from `Accept-Language` (en default, vi supported); copy includes creator contact when public.
- API timestamps are ISO-8601 UTC; the UI renders in the user's locale; short URLs are locale-independent.

## Observability

- Metrics: `links.resolve.latency`, `links.resolve.state{state}`, `links.mint.rejected{reason}`, `links.cache.hit_ratio`, `links.sweep.lag`.
- Tracing: W3C traceparent gateway -> service; sampling 100% mint, 1% resolve; alert when p95 exceeds its gate for 10 min.
