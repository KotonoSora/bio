# Agency Client Reporting - Architecture and Tech Stack (feature 47)

Phase 2 TL · Service: `analytics-service` (port 8085, `analytics_db`, Redis db 6). Implements `docs/01-requirements-ba/47-agency-client-reporting/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`. Doctrine: reporting is a read-model lens — it owns scoping, rollups, and the leaderboard; it never counts.

## Service boundary

| Owns | Consumes (contract -> owner) |
|---|---|
| `report_views` + `report_shares` + `leaderboard_snapshots` (V4); per-profile report reads; combined rollups; leaderboard + snapshots; shares; saved views; cache purge on 45's events | Engagement aggregates -> 12/15's read components + 31's short-link reads (in-service); access facts -> 45's `GET /internal/v1/agency/access`; revenue/units -> billing's `GET /internal/v1/billing/sales-summary`; events -> 45's `org.client_link_changed.v1` / `org.membership_changed.v1` |

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | Module `com.app.analytics.agency`: `AccessGuard`, `ReportReads`, `RollupService`, `LeaderboardService`, `SnapshotJob`, `ShareService`, `ViewService`, `ParityCheck` | One read path; guard-first ordering |
| D-2 | `V4__agency_reporting.sql`: three tables (`report_views`, `report_shares`, `leaderboard_snapshots`) + indexes + RLS | Shares are standalone (a curated client report needs no saved view); snapshots are the dated standing |
| D-3 | `AccessGuard`: calls 45's `GET /internal/v1/agency/access?orgId=&userId=` → `{role, status, profileIds[]}`; caches ≤ 60 s in Redis (`ag:access:*`); purges on 45's events; fails closed when 45 is down beyond cache | BR-05/BR-09/NFR-03 |
| D-4 | Parity: report values render from the same read components as creator dashboards (12/15/31); a daily `ParityCheck` fixture compares; a runtime mismatch marks the metric and alerts — never restates | BR-01/AC-01 |
| D-5 | Rollups: read-time sums of per-profile truth; no materialized rollup tables exist in v1; combined requests cap at 50 profiles | BR-03/NFR-01; exactness by construction |
| D-6 | Leaderboard: one rank function (ties: clicks desc, then stable profile id); daily `SnapshotJob` writes idempotent rows per (org, day, metric); sales metric appears only when billing data exists | BR-04/Q-03; fair, stated, auditable |
| D-7 | Shares: signed token, SHA-256 hash at rest, expiry ≤ 30 days, `noindex`, read-only; curated scope excludes revenue server-side (fields never serialize); events revoke immediately | BR-06/NFR-06; hide-by-absence |
| D-8 | Revenue delegation: `GET /internal/v1/billing/sales-summary?profileId=&from=&to=` → `{available, revenueVnd, units}`; `available:false` hides sections with a reason; never zero-filled | BR-08; money truth stays billing's |
| D-9 | Event consumers: `org.client_link_changed.v1` + `org.membership_changed.v1` → purge `ag:access:*` + revoke matching shares | BR-09; instant-effect symmetry with 45 |
| D-10 | Config: `analytics.reporting` `{cacheAccessSeconds 60, cacheMetricSeconds 900, maxRangeDays 366, combinedProfileCap 50, shareMaxDays 30}` (own `ops_config`, audited flips) | One tunable surface; NFR numbers live here |

## Cross-edits applied with this feature

| Target | Edit |
|---|---|
| `docs/02-architecture-techlead/32-monetize-overview/03-api-contracts-and-payloads.md` | registry row for `GET /internal/v1/billing/sales-summary` (billing implements; 47 consumes) |
| 45 | TL 03 registers `GET /internal/v1/agency/access` (47 consumes); TL 02 adds `client_assignments` (assignment facts the access read returns) |
| `docs/02-architecture-techlead/12-profile-analytics/02-database-schema-design.md` | chain pointer gains V4 (`V4__agency_reporting.sql`) |
| `docs/02-architecture-techlead/00-platform-baseline/01-architecture-and-service-topology.md` | analytics-service feature list gains 47 |
| `MIGRATION.md` | rows 45–47 + batch line at the batch tail |
