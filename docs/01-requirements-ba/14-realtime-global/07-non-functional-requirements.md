# Realtime Global - Non-Functional Requirements (feature 14)

Phase 1 BA · Quantified thresholds enforced in `docs/02-architecture-techlead/14-realtime-global/`. Extends `analytics-service` (features 12-13); no new service, no PG work.

## Performance and capacity

| # | Requirement | Target |
|---|---|---|
| NFR-01 | Read latency (30-bucket merge + response) | p95 <= 300 ms warm, server-side |
| NFR-02 | Poll cadence | Immediate on mount, then 15-30 s with +/-5 s jitter; server throttle 1 per 10 s per profile-user (>=3x client headroom) |
| NFR-03 | Poll budget | <= 4 requests/min per profile-user (client cadence) and <= 6 requests/min enforced by the server token bucket |
| NFR-04 | Bucket write cost | <= 1 pipelined Redis op family (HINCRBY + EXPIRE) per accepted hit; intake p95 unchanged vs feature 12 |
| NFR-05 | Payload size | <= 8 KB per poll response |
| NFR-06 | Working set | Active profiles x <= 60 keys x <= 4 KB; TTL 3600 s per bucket; eviction reads as correct zero |
| NFR-07 | Load ceiling | 10x poll burst throttles at the token bucket; cache error rate and PG write count unmoved |

## Reliability and degradation

| # | Requirement | Target |
|---|---|---|
| NFR-08 | Degraded detection | Banner within one poll cycle (<= 30 s) of a failed merge; auto-recovery on next successful merge |
| NFR-09 | Intake safety | Bucket write failure never affects the visitor response (202 unaffected); `rt.write_error` alert when error rate > 0.1% over 5 min |
| NFR-10 | Expiry semantics | Quiet profiles read zero, never stale highs; no PG fallback path exists (structural) |
| NFR-11 | Client failure discipline | Backoff ceiling 60 s; polling stops after 3 consecutive errors; manual reconnect only (no infinite retry) |

## Observability and governance

| # | Requirement | Target |
|---|---|---|
| NFR-12 | Metrics | `rt.write_pressure` (incr/s), `rt.write_error`, `realtime.poll_throttled`, `rt.last_bucket_age`; `rt.last_bucket_age` > 5 min while platform traffic is active alerts |
| NFR-13 | P4 split signal | Recorded when sustained bucket-write pressure exceeds 2x baseline for 30 min or `rt.write_error` > 0.1% for 15 min; reviewed at P4 capacity planning |
| NFR-14 | Globals isolation | Platform-wide totals exist only on the ops surface (ops role); no creator-facing global endpoint; leaderboards remain 45+ scope |

## Privacy, security, and localization

| # | Requirement | Target |
|---|---|---|
| NFR-15 | Data minimization | Buckets hold counters and country codes only; no IPs, no visitor keys, no identifiers; country-grain geo (DEVIATION CARRIED from feature 12 Q-04) |
| NFR-16 | Isolation | Bucket key includes profile id; owner check precedes merge; `403 foreign_profile` on mismatch; no cross-profile read path |
| NFR-17 | Plan gating | Pro-only reads; free plans `403 realtime_forbidden` with zero teaser fetches; entitlement resolved per request (<= 30 s staleness, feature 13 contract) |
| NFR-18 | Localization | Server sends ISO country codes and locale-neutral numbers; client localizes names and timestamps; copy VN default with EN fallback |
| NFR-19 | Session/security posture | SSO-only (inherited); reads are `Cache-Control: private, max-age=10`; no realtime data in shared caches or logs |
