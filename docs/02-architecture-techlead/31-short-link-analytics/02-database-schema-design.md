# Short-Link Analytics - Database Schema Design (feature 31)

Phase 2 TL · `analytics_db` migration `V3__link_hits.sql` (chain: V1 feature 12 baseline, V2 feature 22 product clicks — see `docs/02-architecture-techlead/12-profile-analytics/02-database-schema-design.md`, which carries the V3 pointer). BA sources: `docs/01-requirements-ba/31-short-link-analytics/` (BR-03..BR-16).

## Conventions

House analytics conventions as feature 12: unprefixed tables in `analytics_db`; `TEXT` ids; `DATE` day buckets (daily grain is the contract); `BIGINT` counters; every table has `user_id TEXT NOT NULL` + index + RLS + app-level scoping; no cross-service FK (`short_code` is a logical reference — links live in `links_db` and owner state resolves via the cached internal read).

## Flyway V3 (`V3__link_hits.sql`)

```sql
CREATE TABLE link_hits_daily (
  day DATE NOT NULL, short_code TEXT NOT NULL, user_id TEXT NOT NULL,
  opens BIGINT NOT NULL DEFAULT 0, uniques BIGINT NOT NULL DEFAULT 0, scans BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (day, short_code)
);
CREATE INDEX idx_lhd_user_day ON link_hits_daily(user_id, day);

CREATE TABLE link_hit_segments (
  day DATE NOT NULL, short_code TEXT NOT NULL, user_id TEXT NOT NULL,
  dimension TEXT NOT NULL, key TEXT NOT NULL, count BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (day, short_code, dimension, key),
  CONSTRAINT chk_lseg_dim CHECK (dimension IN ('device','referrer','scan_class','geo','excluded'))
);
CREATE INDEX idx_lseg_user_day ON link_hit_segments(user_id, day);

CREATE TABLE link_hit_quarantine (
  id TEXT PRIMARY KEY, day DATE NOT NULL, short_code TEXT NOT NULL, user_id TEXT NOT NULL,
  source_hash TEXT NOT NULL, reason TEXT NOT NULL,
  held_count INTEGER NOT NULL CHECK (held_count > 0),
  status TEXT NOT NULL DEFAULT 'pending',
  decided_by TEXT, decided_at TIMESTAMPTZ, decide_reason VARCHAR(200),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_lquar_status CHECK (status IN ('pending','released','dropped'))
);
CREATE INDEX idx_lquar_user_day ON link_hit_quarantine(user_id, day);
CREATE INDEX idx_lquar_pending ON link_hit_quarantine(status) WHERE status = 'pending';

ALTER TABLE link_hits_daily ENABLE ROW LEVEL SECURITY;
ALTER TABLE link_hit_segments ENABLE ROW LEVEL SECURITY;
ALTER TABLE link_hit_quarantine ENABLE ROW LEVEL SECURITY;
CREATE POLICY lhd_owner ON link_hits_daily FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY lhd_ops ON link_hits_daily FOR ALL TO app_role
  USING (current_setting('app.context', true) = 'ops')
  WITH CHECK (current_setting('app.context', true) = 'ops');
-- identical owner + ops pairs for link_hit_segments (lseg_*) and link_hit_quarantine (lquar_*)
GRANT SELECT, INSERT, UPDATE, DELETE ON link_hits_daily, link_hit_segments, link_hit_quarantine TO app_role;
```

## Semantics

- Natural keys are the upsert targets: `INSERT ... ON CONFLICT (day, short_code) DO UPDATE SET opens = opens + EXCLUDED.opens` (house pattern); PKs are the idempotency keys, so replays and double delivery collapse instead of duplicating.
- Day rebuild (repair): `DELETE` the affected day's rows for the affected codes, then recompute from raw — convergent by construction; re-running a rebuild is byte-identical (AC-06).
- Raw evidence: NDJSON in MinIO bucket `link-hits`, path `hits/links/<yyyy>/<mm>/<dd>/*.ndjson`, written at resolve by feature 29's ~60 s flush (cross-edit annotates that row); 90-day lifecycle; completeness markers `hits/links/.state/<day>.done` written by this consumer after drain verification; dashboards and readouts never read raw (same layering rule as feature 12).
- Exposure: every raw hit classifies exactly once — `opens ×1` (non-scan), `scans ×1` (scan-classified), or one `excluded` reason (`bot|prefetch|dedupe|quarantine`); `link_hit_quarantine` rows carry the held floods for the banner + ops case; the single addition beyond the legacy sketch (D-6).
- Redis db 6 additions: `lhit:{ipHash}:{code}` (dedupe, TTL 600 s); `lhit:rate:{ipHash}` (per-minute velocity counter, threshold 500); `links:owner:{code}` (owner/state cache, TTL 300 s).
- Growth bound: rows = codes × active-days × (1 + dimension keys); PG write volume is O(codes × days) — floods land in quarantine/Redis, not in aggregates.
- Retention: aggregates pruned by feature 13's prune job (scope extended, cross-edit landed); raw 90 days; quarantine rows until decided; audit 24 months.
- Deletion: purge extends the feature 02 analytics participant — aggregates, segments, and quarantine rows deleted by `user_id` with counts in the certificate; raw carries no identity to purge (hashed only).

## Drills

| Drill | Asserts |
|---|---|
| Fresh apply | V3 runs clean on `analytics_db` after V2; `\d` shows PKs, CHECKs, indexes, all six policies |
| Replay | Same batch processed twice -> counters increment once per batch |
| Rebuild | Simulated queue loss -> repair from raw matches hit-for-hit; rerun byte-identical |
| Flood | 500/min from one source -> quarantine row + zero aggregate delta for the flood; legitimate traffic counted |
| Separation | Query + grep assertions: zero cross-writes between `link_hits_*` and `profile_views`/`link_clicks` |
| RLS | Foreign `app.user_id` reads zero rows on all three tables; ops context reads for audit only |
| No duplication | `grep -rn "CREATE TABLE link_hits_daily" docs/` matches exactly one file (this one) |
