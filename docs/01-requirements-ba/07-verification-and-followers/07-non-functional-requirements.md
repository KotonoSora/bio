# Verification Badge and Follower Display - Non-Functional Requirements (feature 07)

Phase 1 BA · Thresholds are v1 gates; changing one requires a spec revision with a measured trigger.

## Performance (client-observed)

| NFR | Target |
|---|---|
| NFR-01 `GET verify-status` | p95 <= 150 ms |
| NFR-02 `PUT socials` (validation + recompute) | p95 <= 300 ms |
| NFR-03 ops decide | p95 <= 300 ms |
| NFR-04 public resolve payload (badge + total fields) | adds <= 10 ms to the feature 10 resolve path |
| NFR-05 badge/total public change | visible <= 2 min (60 s cache TTL + purge event) |
| NFR-06 follower refresh job | background only; never on visitor load (AC-07) |

## Freshness and scheduling

- Auto counts: 24 h scheduled refresh per profile, per-network budget `<= 1 req/s` sustained; on-demand refresh rate-limited to 6/h/user; failures keep last-good + `stale`.
- Evidence: single file <= 10 MB; upload-grant TTL 10 min; viewer grants TTL <= 10 min, sandboxed origin.

## Scale and availability

- Verification volume is ops-bounded (human decisions): <= 100 decisions/day/support resources in v1; history rows are single-digit per profile.
- Monthly availability: 99.95% for trust dashboard endpoints; the public page depends only on cached fields (never on source networks).
- Data growth: `verification_requests` + `follower_count_history` grow with applications/edits only; no partitioning in v1.

## Security and masking

- Evidence bytes never leave the ops path: server-random file names, short-TTL download grants, sandboxed viewer; never rendered inline; never logged.
- Counts and badges are public by design; rejection reasons and reviewer identities are owner/ops-only.
- Rate limits: `verify-request` 3/day/user + Turnstile; `followers/refresh` 6/h/user; `PUT socials` 30/h/user; ops decisions 120/h/reviewer.
- Audit: every decide append-only with actor + reason, retained 24 months; near-match flags are advisory (no auto-block).

## Localization and accessibility

- Badge component carries `aria-label` ("Verified by the platform"); provenance labels (`self-reported`, `auto`, `stale`) are localized strings resolved from stable codes.
- Counts render with locale-aware separators; `last updated` is relative time in the user's locale.

## Observability

- Metrics: `trust.request.created`, `trust.decide.latency`, `trust.decide.total{decision}`, `followers.refresh.failures{network}`, `followers.stale{network}`, `trust.jump_flag.total`.
- Alerts: refresh failure rate > 20% per network for 24 h; badge purge > 2 min after decision; decide latency p95 > 300 ms for 10 min.
