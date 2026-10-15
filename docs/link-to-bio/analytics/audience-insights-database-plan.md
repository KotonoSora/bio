# Audience Insights Database Plan (segments + link performance — tables written once)

PLAN ref: P1d-2 audience depth (same beacon/queue, deeper reads) + S-1/S-6. BA source: `docs/link-to-bio/analytics/audience-insights.md`. Consumed by the audience backend plan (segment rollup + clamped reads; splits next to this file). Base aggregates live in `docs/link-to-bio/analytics/profile-analytics-database-plan.md`. No migration chains — the SQL below is complete, applied once.

## Purpose

Give every developer the exact audience tables in one place: how segments and per-link performance derive from the same beacon messages without new tracking, how CTR is defined once, and how plan-window retention bounds growth. Exactly one `CREATE TABLE` per table; no ALTERs, no versions.

## Users

- Backend developer: extends the existing hourly consumer to write these tables; reads them through clamped endpoints.
- QA: verifies hand-computed splits and CTR against seeded traffic.
- Privacy reviewer: confirms no raw IPs, no cross-profile stitching exists at the schema level.

## Functional requirements

1. Conventions (same as the base analytics database plan): `TEXT` ids, `DATE` grain, `BIGINT` counters, `user_id` + index + RLS plus app scoping, migration role owns DDL.
2. Table `bio.traffic_segments` — audience cuts, one row per profile per day per dimension per key. `dimension ∈ device|geo|referrer|utm|returning` (CHECK alphabet); `key` (e.g. `mobile-chrome`, `vn-hcmc`, `instagram`, `utm:campaign:launch`, `returning:true`). Derivation inputs only (never new PII): new-vs-returning from dedupe-key seen-before windows; device/browser from coarse client hints; geo from `cf-ipcountry` + city; referrer groups (social/search/direct/scan-class); UTM from query passthrough with referrer/scan fallbacks (no `unknown` dead-ends where a fallback exists). PK `(profile_id, day, dimension, key)` doubles as the consumer's idempotency key.
3. Table `bio.link_perf` — per-link performance, one row per profile per block per day. `clicks` (same count as `link_clicks`, projected for CTR joins); `ctr_x1000 INT` (per-mille CTR = clicks ÷ profile-views in-window — defined once here, reused by the earnings slice; integer storage avoids float drift across rollups). `block_id` is logical (history survives block deletion, same rule as `link_clicks`).
4. CTR definition (canonical): windowed clicks for (profile, block, day-range) ÷ windowed profile views for (profile, day-range), computed by the consumer from aggregates, never from raw logs. Every CTR display in every UI divides these two aggregates for the same link + window — mismatched-window division fails review by rule.
5. Indexes: `idx_seg_user_day(user_id, day)` and `idx_lp_user_day(user_id, day)` for owner range reads; PKs serve consumer upserts. No new-PII columns exist to index — review asserts the column list.
6. RLS: owner-only policies on both tables; no public policy. Privacy ceiling is structural: no IP column exists, no visitor-id column exists — cross-profile stitching is unqueryable by construction, not merely forbidden.
7. Retention: `AggregateRetentionJob` (specified in the backend plan) deletes rows older than the profile's plan window (free 90d daily; pro 365d + hourly-7d granularity note: hourly grain lives in ephemeral rollups consumed by the realtime/pro views, not in these daily tables). Downgrade re-clamps reads immediately; aged rows never resurrect on re-upgrade. Growth stays O(profiles × days × small-key-cardinality) — UTM freeform values are the one cardinality risk (see Q2).

## Data / API touches

- Full tables, applied once (same baseline run, after base analytics tables):
```sql
CREATE TABLE bio.traffic_segments (
  profile_id TEXT NOT NULL REFERENCES bio.profiles(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL, day DATE NOT NULL, dimension TEXT NOT NULL, key TEXT NOT NULL,
  views BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (profile_id, day, dimension, key),
  CONSTRAINT chk_seg_dim CHECK (dimension IN ('device','geo','referrer','utm','returning'))
);
CREATE INDEX idx_seg_user_day ON bio.traffic_segments(user_id, day);
CREATE TABLE bio.link_perf (
  profile_id TEXT NOT NULL REFERENCES bio.profiles(id) ON DELETE CASCADE,
  block_id TEXT NOT NULL, user_id TEXT NOT NULL, day DATE NOT NULL,
  clicks BIGINT NOT NULL DEFAULT 0, ctr_x1000 INT NOT NULL DEFAULT 0,
  PRIMARY KEY (profile_id, block_id, day)
);
CREATE INDEX idx_lp_user_day ON bio.link_perf(user_id, day);
ALTER TABLE bio.traffic_segments ENABLE ROW LEVEL SECURITY;
ALTER TABLE bio.link_perf ENABLE ROW LEVEL SECURITY;
CREATE POLICY seg_owner ON bio.traffic_segments FOR ALL TO app_role USING (user_id = current_setting('app.user_id', true)) WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY lp_owner ON bio.link_perf FOR ALL TO app_role USING (user_id = current_setting('app.user_id', true)) WITH CHECK (user_id = current_setting('app.user_id', true));
GRANT SELECT, INSERT, UPDATE, DELETE ON bio.traffic_segments, bio.link_perf TO app_role;
```
- Consumed by: backend plan (consumer writes, clamped reads). No beacon/queue changes — same messages, deeper projections.

## Acceptance criteria

- Fresh-apply drill: script runs clean; `\d` shows PKs, CHECK, indexes, policies.
- Parity drill: seeded traffic reproduces hand-computed splits/UTM/CTR exactly within dedupe tolerance.
- Privacy drill: zero IP/visitor-id columns (`\d` inspection); cross-profile stitching query returns nothing by construction.
- Retention drill: aged rows pruned on schedule; downgrade clamps immediately; re-upgrade never resurrects.
- No-duplication drill: `grep -rn "CREATE TABLE bio.traffic_segments\|CREATE TABLE bio.link_perf" docs/` returns exactly this file.

## Out of scope

- Consumer and read logic (backend plan); audience screens (frontend plan); beacon/dedupe mechanics (base analytics plans); live feed; earnings unification; PWA shell.

## Open questions

- Q1: Hourly granularity teaser for free last-24h, or pro-only entirely?
- Q2: UTM allowlist (creator campaign registry) vs freeform — typo-split campaigns argue for a registry; friction argues freeform?
