# Profile Analytics - Step-by-Step Implementation Plan (feature 12)

Phase 2 TL · BA sources: `docs/01-requirements-ba/12-profile-analytics/` (ACs in `06-acceptance-criteria.md`). Contracts: `docs/02-architecture-techlead/12-profile-analytics/03-api-contracts-and-payloads-part-1.md` + part 2. Execute in order; T-IDs are stable.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | Service scaffold: compose slice, build.gradle.kts (starters + Flyway + Redis + MinIO client), gateway routes, health | platform baseline | Boots; health green; route smoke test |
| T-02 | BE | Flyway V1 (`profile_views`, `link_clicks`) + indexes + RLS + grants | T-01 | Fresh-apply drill; `\d` contract |
| T-03 | BE | `BeaconController`: validation, no-oracle `202`, counters, challenge claim | T-01 | Beacon matrix (valid/malformed/dropped) |
| T-04 | BE | Resolver cache: profile resolve with redirects + blocks windowed read, 300 s TTL | T-03 | Rename + unknown-target drills |
| T-05 | BE | `DedupeFilter`: 10-min visitor window, HMAC daily salt, Redis keys | T-03 | Refresh/double-tap collapse drill |
| T-06 | BE | Queue enqueue (`beacon:q`) + drain-time raw NDJSON flush + MinIO lifecycle | T-03 | Queue drill; blob outage drill |
| T-07 | BE | `RollupConsumer` at :05: idempotent additive upserts, retry-the-hour, `consumer_lag_seconds` | T-02, T-06 | Replay single-increment; lag drill |
| T-08 | BE | `AnalyticsController`: overview + links, clamp, deltas, downsample, title enrichment, `foreign_profile` check | T-02, T-04 | Reads drill; IDOR drill |
| T-09 | BE | Bot denylist + abuse auto-tightening + `beacon.rejected{reason}` counters | T-03 | Bot + burst drills |
| T-10 | DevOps | k6 beacon-soak: 10x rate, banner payload rows, zero-write query-log assertion | T-07 | AC-01, AC-10 |
| T-11 | FE | Overview tab: `FetchState`, widgets, range picker clamp UX, empty/degraded/error states | T-08 | AC-03, AC-04, AC-08 |
| T-12 | FE | Realtime locked teaser (zero fetches; upgrade path to feature 14) | T-11 | Gating drill |
| T-13 | DevOps | Observability: metrics, dashboards, alerts (lag > 90 min, abuse, rejection spike) | T-07, T-09 | Ops dashboards green |
| T-14 | DevOps | Container drill: one image, `ROLLUP_ENABLED` split, podman parity, rollback | all | Container + rollback green |

## Notes

- Feature 11's mobile arrangement tasks (its T-07..T-10) attach to this feature's overview surface when the surfaces here land; the beacon transport contract was already fixed by feature 11.
- Ranges beyond `365d`, audience depth, and the realtime endpoint are explicitly out (features 13-15); anything added here would contradict their approved scope.
- Same image serves intake replicas (`ROLLUP_ENABLED=false`) and the scheduler replica (`true`); horizontal scaling adds intake replicas only.
