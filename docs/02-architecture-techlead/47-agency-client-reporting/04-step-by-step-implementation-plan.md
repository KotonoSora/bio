# Agency Client Reporting - Step-by-Step Implementation Plan (feature 47)

Phase 2 TL · Package `com.app.analytics.agency`. BA sources: `docs/01-requirements-ba/47-agency-client-reporting/`. Order: guard + parity first (nothing renders without them); rollups/leaderboard second; shares third.

## Tasks

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `V4__agency_reporting.sql`: three tables + indexes + RLS + drills | V3 applied | V4 drills green |
| T-02 | BE | `AccessGuard`: 45 access-read client, ≤ 60 s cache, event purge, fail-closed | T-01 | AC-03, AC-07 |
| T-03 | BE | `ReportReads` over 12/15/31 components + `ParityCheck` fixture job + runtime mismatch marking | T-01 | AC-01 |
| T-04 | BE | `RollupService`: literal sums, per-profile rows, 50-profile cap, range validation | T-03 | AC-02 |
| T-05 | BE | `LeaderboardService` + `SnapshotJob`: rank function (ties clicks desc, id), idempotent daily rows, sales metric gating | T-03 | AC-04 |
| T-06 | BE | Revenue delegation: billing `sales-summary` client + `available:false` hide path | T-03 | AC-08 |
| T-07 | BE | `ViewService`: owner/manager saved views per org | T-02 | UI flows complete |
| T-08 | BE | `ShareService`: mint/hash/verify, curated serialization (revenue absent), expiry ≤ 30 days, revoke + event revocation | T-01, T-02 | AC-05, AC-06 |
| T-09 | BE | Event consumers: `org.client_link_changed.v1` / `org.membership_changed.v1` → purge + share revoke | T-02, T-08 | AC-07 |
| T-10 | BE | Billing cross-service: implement `GET /internal/v1/billing/sales-summary` (orders-derived, VND integers, `available` honesty) + 32 TL03 registry row | T-06 | AC-08 |
| T-11 | FE | Per-profile report + combined view (rollup + rows, save view) | T-03, T-04, T-07 | UI flows complete |
| T-12 | FE | Leaderboard (method note, tabs, snapshot date) + share dialog + client share page | T-05, T-08 | UI flows complete |
| T-13 | BE/DevOps | Cross-edits: 12 TL02 V4 chain pointer; 45 TL03 access route + TL02 `client_assignments`; baseline 01 feature list; MIGRATION at batch tail | T-01, T-02 | Diff review green |
| T-14 | BE/DevOps | Drills: accuracy match, rollup exactness, scoping, leaderboard fairness, share lifecycle, IDOR, purge timing, honesty | T-01..T-12 | AC-01..AC-08 |
| T-15 | DevOps | Metrics + alerts: reads by surface, cache hit rate, purge latency, share states, snapshot results, parity mismatches, degraded sections | T-02..T-14 | NFR-11 |

## Sequencing notes

1. T-02/T-03 gate everything: no report route ships without guard-first ordering and parity wiring.
2. T-05's rank function is single-sourced; ties must be stable across runs (fixture).
3. T-08's curated serialization is a server-side field set — review with the privacy checklist; revenue fields never serialize for curated shares.
4. T-10 lands the billing internal read (47's only money touch); values copied verbatim, `available:false` honest.
5. T-13's `client_assignments` edit to 45 is required for member scoping — land with T-02.
6. Feature gate: E4 scope audit — zero agency reporting tables/endpoints in E0–E3 images.

## Definition of done

AC-01..AC-08 green; no-ad-hoc-SQL + curated-field-set + guard-ordering greps green in CI; full-tree validation (word caps + backticked path resolution) green; `MIGRATION.md` row 47 flips to `done` at the batch tail with `agency/reporting/client-reporting.md` deleted.
