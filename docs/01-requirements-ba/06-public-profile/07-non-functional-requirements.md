# Public Profile - Non-Functional Requirements (feature 06)

Phase 1 BA · Thresholds are v1 gates; changing one requires a spec revision with a measured trigger.

## Performance (client-observed)

| NFR | Target |
|---|---|
| NFR-01 `GET /api/v1/profile` | p95 <= 300 ms |
| NFR-02 `PUT /api/v1/profile` | p95 <= 300 ms |
| NFR-03 `POST username-check` | p95 <= 150 ms (debounced by client at 300 ms) |
| NFR-04 `POST username-change` (transaction) | p95 <= 400 ms |
| NFR-05 public pagerefresh after mutation | <= 2 min worst case (60 s TTL bound + purge event) |
| NFR-06 internal resolve call (SSR) | p95 <= 50 ms from cache-served path |

## Scale and availability

- 10M profiles at ~1-2 KB/row = 10-20 GB; reads are cache-absorbed by feature 10, writes are human-paced.
- Monthly availability target: 99.95% for profile dashboard endpoints and the internal resolve API.
- Database outage: dashboard mutations `503`; public page serves cached content up to TTL, then a branded 503.
- Growth: username_redirects grows by renames only (insert-only); no partitioning in v1.

## Security and masking

- Usernames are public by design; `bio`/`displayName` content is never logged; logs carry codes, ids, and usernames only, never field bodies.
- All dashboard endpoints session-scoped; `user_id` server-set; RLS on both tables; sanitization per BR-06; CSP enforced on dashboard and public page.
- Rate limits: `username-check` 60/min/user; `username-change` 5/h + 20/day/user; profile `PUT` 30/min/user; claim 5/h/user; Turnstile on claim and rename (BR-12).
- Audit events (claim, update, rename, visibility change) append-only, retained 24 months.

## Session and quotas

- Dashboard session: 30-day rolling SSO cookie, revocable; re-verified per request at the gateway.
- One profile per user in v1; limits beyond that are entitlements (feature 03), never hardcoded here.

## Localization

- Usernames are ASCII `[a-z0-9_]`, locale-independent by construction; bio and display names are user-authored and rendered as-is (sanitized).
- UI copy localized per the platform locale set; error `messages` are resolved client-side from stable codes.

## Observability

- Metrics: `profile.update.latency`, `profile.username.rejected{reason}`, `profile.rename.duration`, `profile.publish.pending_age`, `profile.resolve.result{result}`.
- Tracing: W3C traceparent gateway -> service; 100% mutations, 5% reads sampled; alert when p95 breaches NFR-01..NFR-04 for 10 min.
