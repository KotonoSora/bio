# Link Analytics Database Plan (hit aggregates + quarantine — tables written once)

PLAN ref: P2 delivery (honest short-link numbers per `docs/PLAN.md`) + S-1/S-6. BA source: `docs/url-shortener/tracking/link-analytics.md`. Consumed by the link-analytics backend plan (pipeline, filters, readouts — the only writer; splits next to this file). Link rows live in `docs/url-shortener/links/short-links-database-plan.md` (join target, never redefined here). Creator analytics separation: short-link hits never enter `profile_views`/`link_clicks` (their database plans) — enforced here by the absence of any cross-write path. No migration chains — the SQL below is complete, applied once.

## Purpose

Give every developer the exact short-link analytics tables in one place: daily aggregates with idempotent rebuild keys, dimension breakdowns, the velocity-quarantine store that holds floods out of aggregates pending review, and the evidence rule that makes every reported figure replayable. Exactly one `CREATE TABLE` per table; no ALTERs, no versions.

## Users

- Backend developer: runs this script once; consumer upserts by natural keys; quarantine transitions only through review.
- QA: replays billed counts from evidence; proves floods never touch aggregates.
- Dispute reviewer: decomposes any figure to its underlying hits within TTL.

## Functional requirements

1. Conventions (repo-wide): `TEXT` keys, `DATE` grain, `BIGINT` counters, `user_id` (owner, denormalized from the parent link for RLS without joins) + index + RLS plus app scoping, migration role owns DDL, `app_role` grants, schema `links` (same module as short links + qr).
2. Table `links.link_hits_daily` — one row per short code per day. `day DATE`, `short_code` (logical reference to `short_links(code)` — deliberately not a hard FK: tombstoned/deleted links must keep countable history; a cascade would rewrite reported numbers), `user_id`, `opens BIGINT` (genuine opens post-filter), `uniques BIGINT` (dedupe-window uniques), `scans BIGINT` (scan-leaning heuristic count, labeled estimated at readout — stored separately, never folded silently into opens). PK `(day, short_code)` is the consumer's idempotency key (`ON CONFLICT DO UPDATE` additive — replays and rebuilds converge instead of duplicating).
3. Table `links.link_hit_segments` — breakdowns, one row per code per day per dimension per key. `dimension ∈ device|referrer|scan_class|geo` (CHECK alphabet); `key` (e.g. `mobile-chrome`, `instagram`, `scan-leaning`, `vn`); `count BIGINT`. PK `(day, short_code, dimension, key)` (idempotent rebuild key). No IP/visitor-id columns exist anywhere in these tables (same structural privacy as creator analytics — cross-link identity resolution is unqueryable by construction).
4. Table `links.link_hit_quarantine` — velocity floods held OUT of aggregates pending review. `id` (ulid PK); `day`, `short_code`, `user_id` (owner notified, not публикации); `reason` (`velocity_flood`, …); `held_count BIGINT` (hits excluded from aggregates while held); `status` (`pending|released|dropped`); `decided_by/decided_at` (reviewer + timestamp — every release/drop is attributable). Release adds the held hits to aggregates (idempotent re-application, not double-count); drop discards with the evidence retained in blob logs. Floods never touch aggregates through this table's existence — the consumer writes quarantine OR aggregates, never both for the same hit batch (branch asserted in test).
5. Raw hits are NOT tables: resolve-path hits append to blob NDJSON (standard TTL discipline) — dispute replay and idempotent rebuilds read raw logs, dashboards never do. Filter-exclusion math (`raw − excluded = reported`) must hold exactly within TTL; exclusion counts (bot/prefetch/dedupe/quarantine per reason) are first-class readout data, not footnotes.
6. Indexes: `idx_lhd_user(user_id, day)` (owner ranges); `idx_lseg_user(user_id, day)`; `idx_lq_status(status, day)` (review queue). PKs serve consumer upserts.
7. RLS: owner-only policies on all three tables; ops dispute path through the audited service role (reviewer sees held batches for decision, never raw visitor data beyond the aggregate-safe fields). Raw-hit blob access restricted to ops dispute work (no dashboard reads raw logs — rule, enforced by the absence of any raw-read endpoint for creators).

## Data / API touches

- Full tables, applied once (same baseline run, after `short_links` + `qr_codes`):
```sql
CREATE TABLE links.link_hits_daily (
  day DATE NOT NULL, short_code TEXT NOT NULL, user_id TEXT NOT NULL,
  opens BIGINT NOT NULL DEFAULT 0, uniques BIGINT NOT NULL DEFAULT 0, scans BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (day, short_code)
);
CREATE INDEX idx_lhd_user ON links.link_hits_daily(user_id, day);
CREATE TABLE links.link_hit_segments (
  day DATE NOT NULL, short_code TEXT NOT NULL, user_id TEXT NOT NULL,
  dimension TEXT NOT NULL, key TEXT NOT NULL, count BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (day, short_code, dimension, key),
  CONSTRAINT chk_lseg_dim CHECK (dimension IN ('device','referrer','scan_class','geo'))
);
CREATE INDEX idx_lseg_user ON links.link_hit_segments(user_id, day);
CREATE TABLE links.link_hit_quarantine (
  id TEXT PRIMARY KEY, day DATE NOT NULL, short_code TEXT NOT NULL, user_id TEXT NOT NULL,
  reason TEXT NOT NULL, held_count BIGINT NOT NULL DEFAULT 0, status TEXT NOT NULL DEFAULT 'pending',
  decided_by TEXT, decided_at TIMESTAMPTZ,
  CONSTRAINT chk_lq_status CHECK (status IN ('pending','released','dropped'))
);
CREATE INDEX idx_lq_status ON links.link_hit_quarantine(status, day);
ALTER TABLE links.link_hits_daily ENABLE ROW LEVEL SECURITY;
ALTER TABLE links.link_hit_segments ENABLE ROW LEVEL SECURITY;
ALTER TABLE links.link_hit_quarantine ENABLE ROW LEVEL SECURITY;
CREATE POLICY lhd_owner ON links.link_hits_daily FOR ALL TO app_role USING (user_id = current_setting('app.user_id', true)) WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY lseg_owner ON links.link_hit_segments FOR ALL TO app_role USING (user_id = current_setting('app.user_id', true)) WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY lq_owner ON links.link_hit_quarantine FOR ALL TO app_role USING (user_id = current_setting('app.user_id', true)) WITH CHECK (user_id = current_setting('app.user_id', true));
GRANT SELECT, INSERT, UPDATE, DELETE ON links.link_hits_daily, links.link_hit_segments, links.link_hit_quarantine TO app_role;
```
- Consumed by: analytics backend plan (only writer); per-link readouts + affiliate/ad scoped reads (same aggregates, owner-scoped); ops dispute replay (raw logs + quarantine rows).

## Acceptance criteria

- Fresh-apply drill: script runs clean; `\d` shows PKs, CHECKs, indexes, three policies.
- Counting drill: scripted genuine opens reconcile hit-for-hit; prefetch/bot excluded per posture; 302 path counts every open.
- Quarantine drill: single-source flood held out (aggregates clean) with an ops case; legitimate same-window traffic unaffected; release adds once; drop discards with evidence intact.
- Separation drill (under load): zero short-link rows in creator analytics tables and vice versa; bio-block dual-count case verified as designed (short-link open + block click = two true events).
- Replay drill: any reported figure replays from evidence within TTL; `raw − excluded = reported` holds exactly.
- RLS drill: foreign `app.user_id` reads zero rows on all three tables.
- No-duplication drill: `grep -rn "CREATE TABLE links.link_hits_daily\|CREATE TABLE links.link_hit_segments\|CREATE TABLE links.link_hit_quarantine" docs/` returns exactly this file.

## Out of scope

- Pipeline, filters, and readout logic (backend plan); dashboards and dispute screens (frontend plan); mint/resolve (short-links plans); QR rendering (qr plans); creator analytics content (link-to-bio plans); affiliate/ad measurement (consumers); custom short domains (future); P4/P5.

## Open questions

- Q1: Bot-exclusion posture — shared decision across creator/short-link/ad counting, or strictest-wins for money-tied traffic (BA Q1)?
- Q2: Quarantine auto-release thresholds (velocity decay) vs human-only release?
