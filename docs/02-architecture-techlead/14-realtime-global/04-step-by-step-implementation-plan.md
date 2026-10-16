# Realtime Global - Step-by-Step Implementation Plan (feature 14)

Phase 2 TL · BA sources: `docs/01-requirements-ba/14-realtime-global/` (ACs in `06-acceptance-criteria.md`). Contracts: `docs/02-architecture-techlead/14-realtime-global/03-api-contracts-and-payloads.md`. Execute in order; T-IDs are stable.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `MinuteBucketWriter` + intake hook: pipelined `HINCRBY` (`v`, `c`, `l:{blockId}`, `g:{CC}`) + `EXPIRE` 3600 + trim-on-new-field (16 link / 10 geo caps); failure -> `rt.write_error`, 202 unaffected | 12 intake | Liveness (AC-01); intake safety (NFR-09) |
| T-02 | BE | `RealtimeMergeService`: pipelined 30-bucket merge, sums, top-8/top-5 ranking, `cpmSeries` oldest->newest, `windowS:1800` | T-01 | Liveness + expiry (AC-01, AC-04) |
| T-03 | BE | `RealtimeController`: `PlanGate` snapshot check (fail-closed), owner check, `private, max-age=10` header | T-02 | Teaser/gating (AC-02); isolation (AC-09) |
| T-04 | BE | `RtTokenBucket` 1 req/10 s (`rtpoll:{profileId}:{userId}`) + `429 rate_limited` + `Retry-After` + `realtime.poll_throttled` | T-03 | Throttle (AC-06); burst proof (AC-03) |
| T-05 | BE | Degraded shape: `rtgood:{profileId}` written on successful merge; merge failure -> `status:"degraded"` + `lastGoodAt`; no-PG assertion test on read/write paths | T-02 | Degraded (AC-08); no-DB + authority (AC-03, AC-05) |
| T-06 | BE | `SalesTickReader` seam: `has_orders(profileId)` capability check; v1 always absent; contract-shape test against a fixture payload | T-03 | Sales-conditional (AC-12) |
| T-07 | BE | Metrics + alerts (`rt.write_pressure`, `rt.write_error`, `rt.last_bucket_age`) + P4 split-signal recording rule | T-01, T-05 | Ops drill (AC-14); split signal recorded (NFR-13) |
| T-08 | FE | `RealtimePanel` lifecycle: mount fetch, 15-30 s cadence +/-5 s jitter, `document.hidden` pause, unmount clears timers | T-03 | Hidden-tab (AC-07); liveness (AC-01) |
| T-09 | FE | Cards: VisitorsStat, canvas sparkline + small-screen downsample, TopLinks, GeoList, AsOfStamp, aggregates footnote | T-08 | Rendering + same-numbers (AC-01, AC-11) |
| T-10 | FE | Backoff/reconnect/throttled copy; degraded last-good banner + 60 s stretch; zero-state copy | T-09 | Backoff (AC-06, AC-10); degraded UX (AC-08) |
| T-11 | FE | Locked teaser + demotion swap (`403` -> teaser, no error copy, zero fetches) | T-03 | Teaser drill (AC-02) |
| T-12 | DevOps | Drill battery: liveness, teaser, no-DB, expiry, authority, throttle, hidden-tab, degraded, isolation, reconnect, same-numbers, sales, privacy, globals; same image via `docker run` + `podman run`; 10x burst k6; PG-write-zero proof; route-inventory grep | T-04..T-11 | AC-01..AC-14 green |

## Notes

- All BE work lands in the existing `analytics-service` image (12); no new container, dependency, queue, or migration. Gradle/compose unchanged.
- Feature 11's PWA already hosts the panel position; its same-numbers contract covers realtime (T-09/T-12); its tasks are unaffected.
- Feature 15 consumes `ctr_x1000` + window sums from `13 T-04` for earnings; the realtime feed is never a data source for 15.
- Sales strip stays absent until shop 22-26 / monetize 32-40 supply order rollups (`has_orders`); no placeholder ships in the interim (D-6).
- Release gates (container digest pair, rollback, contract diff) run per feature; platform pins land with `docs/02-architecture-techlead/00-platform-baseline/`.
