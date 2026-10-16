# Profile Analytics - Database Schema Design (feature 12)

Phase 2 TL · BA sources: `docs/01-requirements-ba/12-profile-analytics/` (BR-03, BR-04, BR-09). Database: `analytics_db` (PostgreSQL 18.6, per-service, Flyway-on-boot, RLS on).

## Conventions

`TEXT` ids; `DATE` day buckets (daily grain is the contract, not timestamps); `BIGINT` counters; every table has `user_id TEXT NOT NULL` + index + RLS + app-level scoping; migration role owns DDL; `app_role` holds grants. No cross-service FK exists: `profile_id` and `block_id` are logical references (profiles live in `profile_db`).

## Flyway V1 (single script; no versions after it in this feature)

```sql
CREATE TABLE profile_views (
  profile_id TEXT NOT NULL, user_id TEXT NOT NULL, day DATE NOT NULL,
  views BIGINT NOT NULL DEFAULT 0, interactions BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (profile_id, day)
);
CREATE INDEX idx_pv_user_day ON profile_views(user_id, day);
CREATE TABLE link_clicks (
  profile_id TEXT NOT NULL, block_id TEXT NOT NULL, user_id TEXT NOT NULL, day DATE NOT NULL,
  count BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (profile_id, block_id, day)
);
CREATE INDEX idx_lc_user_day ON link_clicks(user_id, day);
ALTER TABLE profile_views ENABLE ROW LEVEL SECURITY;
ALTER TABLE link_clicks ENABLE ROW LEVEL SECURITY;
CREATE POLICY pv_owner ON profile_views FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY lc_owner ON link_clicks FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
GRANT SELECT, INSERT, UPDATE, DELETE ON profile_views, link_clicks TO app_role;
```

## Flyway V2 (feature 22: link-out product clicks)

```sql
-- V2__link_clicks_product.sql
ALTER TABLE link_clicks DROP CONSTRAINT link_clicks_pkey;
ALTER TABLE link_clicks ALTER COLUMN block_id DROP NOT NULL;
ALTER TABLE link_clicks ADD COLUMN product_id TEXT;
CREATE UNIQUE INDEX uq_lc_block   ON link_clicks (profile_id, block_id, day)   WHERE block_id IS NOT NULL;
CREATE UNIQUE INDEX uq_lc_product ON link_clicks (profile_id, product_id, day) WHERE product_id IS NOT NULL;
ALTER TABLE link_clicks ADD CONSTRAINT chk_lc_target CHECK ((block_id IS NULL) <> (product_id IS NULL));
```

- Shop-page link-out clicks carry `productId` (feature 22); every click row has exactly one target (`chk_lc_target`); the partial unique indexes carry the dedupe/upsert natural keys (`ON CONFLICT (profile_id, block_id, day) WHERE block_id IS NOT NULL`, `... product_id ...`).
- No target verification for product clicks in v1 (no cheap verification path; bounded by throttle + dedupe; feature 22 TL 01 D-4).
- Growth bound, retention, and purge scope are unchanged.

## Flyway V3 (feature 31: short-link hit aggregates)

Short-link hits land in three sibling tables (`link_hits_daily`, `link_hit_segments` with the `excluded` dimension, `link_hit_quarantine`) via `V3__link_hits.sql` — DDL, semantics, and drills live in `docs/02-architecture-techlead/31-short-link-analytics/02-database-schema-design.md`; same conventions, same RLS shape, no changes to the tables above.

Agency reporting (feature 47) adds `V4__agency_reporting.sql` (`report_views`, `report_shares`, `leaderboard_snapshots`) — DDL, semantics, and drills live in `docs/02-architecture-techlead/47-agency-client-reporting/02-database-schema-design.md`; same conventions, same RLS shape, no changes to the tables above.

## Semantics

- `profile_views`: one row per profile per day; `views` = deduped page views; `interactions` = click-bearing visits + banner/escape events (BR-14).
- `link_clicks`: one row per profile per block per day; `block_id` deliberately not a FK — deleted blocks keep historical clicks (BR-09).
- Consumer upsert: `INSERT ... ON CONFLICT (natural key) DO UPDATE SET views = views + EXCLUDED.views` (same for other counters). PKs are the idempotency keys; replays and double delivery collapse instead of duplicating.
- Raw hits are never tables: NDJSON in MinIO `analytics-raw` (90-day lifecycle), written by the drain; any SQL joining raw data into reads is a layering violation.
- Growth bound: rows = profiles x active-days x (1 + blocks). PG write volume is O(profiles x days), independent of traffic spikes — virality lands in blob storage and Redis, not here.
- Retention: free 90d / pro 365d pruning owned by feature 13's prune job, which covers these tables plus feature 31's link-hit aggregates; no per-feature prune logic here.
- Deletion: owner-scoped purge arrives via the feature 02 participant contract (`docs/02-architecture-techlead/02-account-deletion/03-api-contracts-and-payloads.md`): day buckets, click rows, rollups, and Redis markers deleted with counts — no DB-level cascade across services by design.

## Drills

| Drill | Asserts |
|---|---|
| Fresh apply | Script runs clean on `analytics_db`; `\d` shows PKs, indexes, policies |
| Replay | Same hourly batch twice -> counters increment once per replay batch |
| History | Block deleted -> `link_clicks` rows survive; profile purge -> handled by contract |
| RLS | Foreign `app.user_id` reads zero rows on both tables |
| Growth | 10x beacon replay changes blob bytes only; PG row count unchanged |
| No duplication | `grep -rn "CREATE TABLE profile_views" docs/` matches exactly one file (this one) |
| Product clicks | V2 applies clean; product rows upsert via `uq_lc_product`; two-target and zero-target rows rejected by `chk_lc_target` |
