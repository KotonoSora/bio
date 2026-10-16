# Audience Insights - Database Schema Design (feature 13)

Phase 2 TL · BA sources: `docs/01-requirements-ba/13-audience-insights/` (BR-02..BR-05, BR-08, BR-09). Database: `analytics_db` (PostgreSQL 18.6); Flyway V2 on top of feature 12's V1 (`docs/02-architecture-techlead/12-profile-analytics/02-database-schema-design.md`).

## Flyway V2 (single script)

```sql
CREATE TABLE traffic_segments (
  profile_id TEXT NOT NULL, user_id TEXT NOT NULL, day DATE NOT NULL,
  dimension TEXT NOT NULL, key TEXT NOT NULL,
  views BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (profile_id, day, dimension, key),
  CONSTRAINT chk_seg_dim CHECK (dimension IN ('device','geo','referrer','utm','returning'))
);
CREATE INDEX idx_seg_user_day ON traffic_segments(user_id, day);
CREATE TABLE link_perf (
  profile_id TEXT NOT NULL, block_id TEXT NOT NULL, user_id TEXT NOT NULL, day DATE NOT NULL,
  clicks BIGINT NOT NULL DEFAULT 0, ctr_x1000 INT NOT NULL DEFAULT 0,
  PRIMARY KEY (profile_id, block_id, day)
);
CREATE INDEX idx_lp_user_day ON link_perf(user_id, day);
ALTER TABLE traffic_segments ENABLE ROW LEVEL SECURITY;
ALTER TABLE link_perf ENABLE ROW LEVEL SECURITY;
CREATE POLICY seg_owner ON traffic_segments FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY lp_owner ON link_perf FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
GRANT SELECT, INSERT, UPDATE, DELETE ON traffic_segments, link_perf TO app_role;
```

DEVIATION CARRIED: the legacy DDL's `REFERENCES bio.profiles(id) ON DELETE CASCADE` is dropped — no cross-service FK exists (established by feature 12's schema rules); `profile_id` and `block_id` are logical references.

## Semantics

- Dimension keys (BR-02): `device` `{class}-{browser}`; `geo` lowercase ISO 3166 alpha-2 or `unknown`; `referrer` normalized domain or `direct`; `utm` `{field}:{value}`; `returning` `new|returning`. CHECK constraint fences the dimension alphabet; keys are normalized before upsert (BR-10).
- Upsert: `INSERT ... ON CONFLICT (PK) DO UPDATE SET views = views + EXCLUDED.views` (same for `clicks`); PKs are the idempotency keys — replays collapse.
- `ctr_x1000`: per-day value stored for series/sparklines; window CTR recomputed at read from window sums of `link_perf.clicks` ÷ `profile_views.views` (multi-table read in the same database — no cross-service join). Round-half-up; CTR is never computed from raw logs.
- Privacy is structural: no IP or visitor-id column exists; the projection DTO exposes only whitelisted beacon fields; country grain only (DEVIATION CARRIED from feature 12 Q-04).
- Growth: O(profiles x days x (dimensions x small-key-cardinality)); UTM freeform values are the one cardinality risk — bounded by normalization caps, display top-25 + `Other`, and the alert gauge (NFRs).
- Retention: `AggregateRetentionJob` (nightly) deletes rows older than the profile's plan window across V1 + V2 tables. Reads clamp by window too; deletion drift (rows older than window until the next run) is harmless and stated.
- Overview wiring: feature 12's `sources[]`/`countries[]` now read `traffic_segments` (`referrer`, `geo`); contract unchanged.

## Drills

| Drill | Asserts |
|---|---|
| Fresh apply | V2 runs clean on top of V1; `\d` shows PKs, CHECK, indexes, policies |
| Parity | Seeded traffic reproduces hand-computed splits, UTM, CTR exactly within dedupe tolerance |
| Privacy | Column-list assertion: zero IP/visitor-id columns; stitching query returns nothing |
| Retention | Aged rows pruned on schedule (also covers feature 31's link-hit aggregates); downgrade clamps immediately; re-upgrade never resurrects |
| No-duplication | `grep -rn "CREATE TABLE traffic_segments" docs/` matches exactly one file (this one) |
