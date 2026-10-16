# Verification Badge and Follower Display - Step-by-Step Implementation Plan (feature 07)

Phase 2 TL · Dependency-ordered chain; each task's acceptance goal gates the next. Subsystems: BE, FE, DevOps. Criteria cited from `docs/01-requirements-ba/07-verification-and-followers/06-acceptance-criteria.md`.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | Flyway `V4__verification_baseline.sql` (two tables, FK cascade, CHECKs, indexes, RLS) | feature 06 module | Drills 1-5 of `02-database-schema-design.md` pass |
| T-02 | BE | Entities + repositories (`VerificationRequestEntity`, `FollowerCountHistoryEntity`; queue/history queries) | T-01 | RLS drill: cross-owner visibility denied |
| T-03 | BE | `VerificationService`: state machine (5 states, legal transitions only), rename hook wired into `ProfileService.changeUsername`, purge event on every transition | T-02 | AC-02, AC-03 green |
| T-04 | BE | Evidence path: presign endpoint, random blob keys (`verification/{ulid}.ext`), type/size gate, short-TTL viewer URLs | T-03 | AC-01 family: no inline render path exists; key never half-sent |
| T-05 | BE | Ops endpoints: queue list, evidence-url, approve/reject/revoke with mandatory reason + audit row | T-04 | AC-11 green; 403 without role; audit excerpt matches |
| T-06 | BE | `FollowerService.updateSocials`: per-network handle/URL validation, cap 100,000,000, history row per edit, 10x jump flag (`jumpFlagged`) | T-02 | AC-05, AC-06, AC-10 green |
| T-07 | BE | `refreshAuto`: keyless `RestClient` fetches, per-network budget (1 req/s), failure -> last-good + `stale` | T-06 | AC-08 green; no token storage anywhere |
| T-08 | BE | `FollowerRefreshJob` (`@Scheduled`, 24 h cron from env): iterate profiles, swallow per-profile failures with metrics | T-07 | AC-07 green: no fetch on visitor load; public p95 unchanged |
| T-09 | BE | Near-match advisory: Levenshtein <= 2 against verified usernames -> ops queue flag; never an auto-block | T-05 | AC-12 green |
| T-10 | BE | Controllers + DTOs (`TrustController`, `OpsVerifyController`); additive `PublicProfileResponse` fields | T-05, T-07 | AC-13 green; contract tests exact per code |
| T-11 | DevOps | Gateway rules: rate limits (request 3/day, refresh 6/h, socials 30/h, ops 120/h) + Turnstile on request | T-10 | Rate drills: `429` + `Retry-After` correct |
| T-12 | BE | Drill suite: state machine, follower integrity, outage, IDOR/RLS, injection; grep gate (zero vault tables/endpoints/deps) | T-03..T-11 | AC-01..AC-13 green; grep gate fails the build on vault shapes |
| T-13 | FE | `/app/trust`: verification card states, evidence step (upload progress), pending poll (60 s), badge preview, rename/review notice | T-10 | AC-02, AC-03 UI paths green |
| T-14 | FE | Socials editor: rows, cap hint, row-level error map, jump notice, stale chip, breakdown tooltip, refresh cooldown | T-13 | AC-05, AC-06, AC-08 UI paths green |
| T-15 | FE | Ops route `/app/ops/trust`: queue, sandboxed evidence viewer entry, decide panel (typed revoke confirm), audit excerpt | T-14 | AC-11 UI path green; 403 leaks no queue data |
| T-16 | BE | Observability: metrics + tracing + structured logs per `05-security-logging-and-rate-limiting.md` | T-07 | Dashboards show decide latency, refresh failures, staleness |
| T-17 | DevOps | Image + release gate: digest pin, SBOM, rollback, blue/green deploy | T-12, T-15 | `docker run` + `podman run` cold boot; rollback drill <= 10 min |

Definition of done: AC-01..AC-13 green, five schema drills green, vault grep gate green, release gate passes, legacy verification-followers docs deleted (git history is the archive).
