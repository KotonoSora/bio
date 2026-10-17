# Realtime Global - Security, Logging, and Rate Limiting (feature 14)

Phase 2 TL · BA sources: `docs/01-requirements-ba/14-realtime-global/` (BR-01, BR-04..BR-08, BR-12). Baseline controls: `docs/02-architecture-techlead/00-platform-baseline/`; inherited intake controls: `docs/02-architecture-techlead/12-profile-analytics/07-security-logging-and-rate-limiting.md`; entitlement pattern: `docs/02-architecture-techlead/13-audience-insights/05-security-logging-and-rate-limiting.md` (S-4).

## Controls

| # | Control | Rule |
|---|---|---|
| S-1 | Pro gate, fail-closed | Free -> `403 realtime_forbidden`; entitlement snapshot miss -> treated as free + `entitlement_snapshot_miss` counter; teaser issues zero fetches (request-log asserted); no client-side check is trusted |
| S-2 | Owner scoping and bucket isolation | Bucket keys embed the server-set profile id; owner check precedes merge; mismatch -> `403 foreign_profile`; no cross-profile read path is representable |
| S-3 | Anti-farm throttling | Per-profile-per-user token bucket 1 req/10 s; excess -> `429 rate_limited` + `Retry-After`; `realtime.poll_throttled` spike alert; server backstop documented (no client cross-tab coordination exists) |
| S-4 | Privacy ceiling | Buckets, responses, and logs hold counters and ISO country codes only - no IPs, no visitor keys, no identifiers, no raw referrer; country-grain geo (DEVIATION CARRIED from feature 12 Q-04) |
| S-5 | No-PG fallback, structural | Cache-down drill asserts zero PG statements on realtime paths; degraded is the only failure mode; grep gate forbids a hidden DB read |
| S-6 | Intake safety | Bucket writes are best-effort after the accept decision; visitor response (202) is never affected; `rt.write_error` alert when error rate > 0.1% over 5 min; an undercounting window is honest, never patched from aggregates |
| S-7 | Ops-only globals | Platform-wide live totals exist only on the ops metrics surface behind the ops role; no creator-facing global endpoint exists (route-inventory grep); leaderboards remain 45+ |
| S-8 | Logging minimization | Access logs carry route, status, and timing only - never counters, country values, or profile ids; metrics are never labeled by profile id |
| S-9 | Sales seam integrity | `has_orders` capability evaluated server-side; order rollup access stays with its owning service (shop/monetize); absent semantics prevent any implied zero-sales claim |

## Threat checklist

| Threat | Mitigation |
|---|---|
| Dashboard-tab farm self-DDoS | S-3 throttle + teaser zero-fetch rule + hidden-tab pause (feature 11 BR-07); 10x burst drill is the gate |
| Cache poisoning via keys | Keys derive from server-side profile id + UTC minute only; no request input appears in any key; trim victims deterministic (lowest count, ties lexicographic) |
| Snapshot staleness abuse (just-downgraded polling) | Bound stated: <= 30 s of stale entitlement, then re-resolved; cannot widen access beyond the window |
| Bypassing the pro gate via deep link/API | Server-side gate only (S-1); direct calls get the same 403; no bypass path exists |
| Cross-profile visitor stitching | Structurally unqueryable: no visitor ids exist anywhere; per-profile key namespace + owner check (S-2) |
| Degraded-mode data leakage | Degraded responses carry zeros + status + `lastGoodAt`; no last-good payload reconstruction server-side; the client renders its own snapshot only |
| Realtime numbers cited as billing truth | Authority rule (BR-05): footnote + runbook cite hourly aggregates; no billing/reporting path reads realtime |

## Logging and degrade

- Metrics: `rt.write_pressure` (increments/s + error rate), `rt.write_error`, `realtime.poll_throttled`, `rt.last_bucket_age`; alerts: error rate > 0.1% over 5 min, `rt.last_bucket_age` > 5 min while platform traffic is active, poll-throttle spike.
- P4 split signal (recorded, not executed): sustained `rt.write_pressure` above 2x baseline for 30 min or `rt.write_error` > 0.1% for 15 min makes beacon ingest the first-split candidate; proposal cites these numbers or does not happen.
- Degrade matrix: merge failure -> `degraded` + `lastGoodAt` (read keeps serving; auto-recovery next merge); bucket write failure -> alert + possible undercount (intake unaffected); entitlement snapshot miss -> fail-closed to free; sales capability absent -> `sales` omitted. No path reads or writes PG in realtime.
