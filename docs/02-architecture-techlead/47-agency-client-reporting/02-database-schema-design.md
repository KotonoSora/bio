# Agency Client Reporting - Database Schema Design (feature 47)

Phase 2 TL · `analytics_db` migration `V4__agency_reporting.sql` (chain: V1 feature 12 baseline, V2 product clicks, V3 short-link hits). BA sources: `docs/01-requirements-ba/47-agency-client-reporting/`.

## Flyway V4 (`V4__agency_reporting.sql`)

```sql
CREATE TABLE report_views (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  org_id TEXT NOT NULL,
  name TEXT NOT NULL,
  scope JSONB NOT NULL,  -- {profileIds, metrics, periodDays}
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_rv_org ON report_views (org_id);

CREATE TABLE report_shares (
  id TEXT PRIMARY KEY,
  view_id TEXT REFERENCES report_views(id),  -- null for standalone per-profile shares
  org_id TEXT NOT NULL,
  profile_ids TEXT[] NOT NULL,
  scope TEXT NOT NULL CHECK (scope IN ('curated','full')),
  token_hash TEXT NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  created_by TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_rs_expiry CHECK (expires_at > created_at)
);
CREATE INDEX idx_rs_org ON report_shares (org_id, expires_at) WHERE revoked_at IS NULL;

CREATE TABLE leaderboard_snapshots (
  id TEXT PRIMARY KEY,
  org_id TEXT NOT NULL,
  snapshot_day DATE NOT NULL,
  metric TEXT NOT NULL CHECK (metric IN ('views','clicks','ctr','sales')),
  ranks JSONB NOT NULL,  -- [{profileId, value, rank}]
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uniq_ls UNIQUE (org_id, snapshot_day, metric)
);
CREATE INDEX idx_ls_org ON leaderboard_snapshots (org_id, snapshot_day DESC);
```

## RLS and grants

```sql
ALTER TABLE report_views ENABLE ROW LEVEL SECURITY;
CREATE POLICY rv_own ON report_views FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
-- report_shares: own-user + service/ops ALL; leaderboard_snapshots: service/ops ALL
GRANT SELECT, INSERT, UPDATE, DELETE ON report_views, report_shares TO app_role;
GRANT SELECT, INSERT ON leaderboard_snapshots TO app_role;  -- append-only standing (no UPDATE/DELETE grant)
```

Org-wide reads (owner/manager seeing all saved views, leaderboard rows) execute in service context **after** `AccessGuard` authorizes the caller (D-3) — the guard is the authorization, RLS is the backstop; both are drilled.

## Semantics

- AccessGuard (D-3): `role ∈ owner|manager|member|client` + `profileIds[]` from 45 (owner/manager: all linked; member: assigned; client: own); cached ≤ 60 s at `ag:access:{orgId}:{userId}`; purged by 45's events; 45 down → scoped reads fail closed beyond the cache (AC-07).
- Report reads (D-4): `ReportReads` calls 12/15's read components (`profile_views`, `link_clicks`) and 31's short-link reads; no ad-hoc aggregate SQL exists in report code (grep gate: report package imports the read components only); `ParityCheck` fixture runs daily; runtime mismatch marks the metric + alerts.
- Rollups (D-5): read-time sum across `profileIds` (≤ 50/request); per-profile rows always computed first; the rollup is the literal sum; no modeled numbers.
- Leaderboard (D-6): rank function over the period's aggregates; ties clicks desc then profile id; `SnapshotJob` writes one row per (org, day, metric) with `ON CONFLICT DO NOTHING`; sales metric included only when billing's summary is available for ≥ 1 profile; snapshots are append-only.
- Shares (D-7): mint → token shown once, SHA-256 hash stored; open = hash lookup + unexpired + not revoked; curated scope serializes a fixed field set (revenue fields never leave the server); revoke sets `revoked_at`; 45's events revoke shares whose `profile_ids` intersect or whose org membership changed; expiry job sweeps.
- Revenue (D-8): `sales-summary` call per profile/period; `{available:false}` hides the section with a reason; values copied verbatim (VND integers); billing down → hidden with last-good note, never zero-filled.
- Saved views (D-9/BR-07): owner/manager CRUD scoped to their org; members do not see saved views (working default only).
- Purge (D-9): events delete `ag:access:*` keys and revoke shares ≤ 60 s; the next read re-checks fresh facts.
- Config (D-10): `analytics.reporting` values in own `ops_config` (audited 5 s flips); NFR numbers read from it.
- No visitor-level data, tokens, or cross-org refs ever serialize; zero token material in logs.

## Drills

| Drill | Asserts |
|---|---|
| Fresh apply | V4 after V3; three tables + indexes + RLS; snapshot table append-only (no UPDATE/DELETE grant) |
| Parity | Fixture profile renders byte-equal on org report and creator dashboard; seeded mismatch marks + alerts, never restates |
| Rollup exactness | 3-profile fixture: rollup = literal sum; per-profile rows present; 50-profile cap enforced |
| Guard ordering | Reads without a guard pass return nothing; guard cache honors ≤ 60 s; 45-down fails closed beyond cache |
| Snapshot idempotence | Re-runs produce one row per (org, day, metric); ties stable across runs; misses alerted after 2 |
| Share lifecycle | Curated share serializes no revenue fields; full requires owner/manager; expiry ≤ 30 days; revoke + events kill immediately |
| Purge timing | Unlink/membership event → cache gone + shares revoked ≤ 60 s; next read reflects fresh scope |
| RLS probes | Foreign `app.user_id` reads zero rows on views/shares; service-context org reads only after guard (fixture denies) |
