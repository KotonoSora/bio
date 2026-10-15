# Profile Analytics Database Plan (views + clicks aggregates — tables written once)

PLAN ref: P1d-1 (aggregates-only PG, R2 raw 90d, zero per-hit PG writes) + S-1/S-6. BA source: `docs/link-to-bio/analytics/profile-analytics.md`. Consumed by the profile-analytics backend plan (intake, consumer, reads — the only writers; splits next to this file). No migration chains — the SQL below is complete, applied once.

## Purpose

Give every developer the exact base analytics tables in one place: what each aggregate stores, how hourly idempotent upserts make replays safe, why raw hits never touch PG, and how retention bounds growth. Exactly one `CREATE TABLE` per table; no ALTERs, no versions. Load design is a schema property here: per-hit writes are structurally impossible (no row exists to write until the hourly rollup).

## Users

- Backend developer: runs this script once, maps aggregate entities 1:1, writes only through upserts keyed below.
- QA: replays queue messages and asserts single increments; probes RLS.
- Capacity reviewer: verifies the zero-per-hit-write claim from the schema, not just code.

## Functional requirements

1. Conventions (same as the profile database plan): `TEXT` ids, `DATE` day buckets (not timestamps — daily grain is the contract), `BIGINT` counters, `user_id TEXT NOT NULL` + index + RLS plus app-level scoping, migration role owns DDL, `app_role` table grants.
2. Table `bio.profile_views` — one row per profile per day. `profile_id` (FK → profiles, cascade), `user_id`, `day DATE`, `views BIGINT` (deduped page views), `interactions BIGINT` (click-bearing visits and banner/escape events). PK `(profile_id, day)` is the idempotency key: the consumer upserts `ON CONFLICT (profile_id, day) DO UPDATE SET views = views + EXCLUDED.views, …`, so replayed messages increment exactly once per replay batch, and double-delivery collapses instead of duplicating.
3. Table `bio.link_clicks` — one row per profile per block per day. `profile_id`, `block_id` (logical reference to `profile_blocks.id` — deliberately not a hard FK: deleted blocks must keep their historical clicks; a cascade would rewrite history), `user_id`, `day`, `count BIGINT`. PK `(profile_id, block_id, day)` is the idempotency key with the same upsert contract.
4. Raw hits are NOT tables: beacon payloads append to blob-storage NDJSON (90-day TTL), never PG. Dashboards, exports, and audits read aggregates only; raw logs exist for dispute forensics and pipeline verification, then age out. Any query joining raw logs into a dashboard is a layering violation.
5. Indexes: `idx_pv_user_day(user_id, day)` and `idx_lc_user_day(user_id, day)` serve owner range reads (`WHERE user_id AND day BETWEEN`); PKs serve the consumer upserts. No per-hit indexes needed — there are no per-hit writes.
6. RLS: owner-only policies on both tables (`user_id = current_setting('app.user_id', true)`); no public policy — analytics rows are never anonymous-readable. Cross-user reads fail in app code (403) and at the DB (zero rows).
7. Retention and growth: aggregates older than the profile's plan window roll off (free 90d daily, pro 365d + hourly-7d lives in the audience slice; the prune job is specified there and covers these tables too). Steady state ≈ rows = profiles × active-days × (1 + blocks) — bounded, never viral-scaled, because virality lands in blob logs, not here. This is the high-load story at the schema level: PG write volume is O(profiles × days), independent of traffic spikes.

## Data / API touches

- Full tables, applied once (same baseline run, after profile/blocks scripts):
```sql
CREATE TABLE bio.profile_views (
  profile_id TEXT NOT NULL REFERENCES bio.profiles(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL, day DATE NOT NULL,
  views BIGINT NOT NULL DEFAULT 0, interactions BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (profile_id, day)
);
CREATE INDEX idx_pv_user_day ON bio.profile_views(user_id, day);
CREATE TABLE bio.link_clicks (
  profile_id TEXT NOT NULL REFERENCES bio.profiles(id) ON DELETE CASCADE,
  block_id TEXT NOT NULL, user_id TEXT NOT NULL, day DATE NOT NULL,
  count BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (profile_id, block_id, day)
);
CREATE INDEX idx_lc_user_day ON bio.link_clicks(user_id, day);
ALTER TABLE bio.profile_views ENABLE ROW LEVEL SECURITY;
ALTER TABLE bio.link_clicks ENABLE ROW LEVEL SECURITY;
CREATE POLICY pv_owner ON bio.profile_views FOR ALL TO app_role USING (user_id = current_setting('app.user_id', true)) WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY lc_owner ON bio.link_clicks FOR ALL TO app_role USING (user_id = current_setting('app.user_id', true)) WITH CHECK (user_id = current_setting('app.user_id', true));
GRANT SELECT, INSERT, UPDATE, DELETE ON bio.profile_views, bio.link_clicks TO app_role;
```
- Consumed by: backend plan (hourly consumer upserts, dashboard range reads). No other writers exist; no raw-log table will ever be added (rule 4).

## Acceptance criteria

- Fresh-apply drill: script runs clean; `\d` shows PKs, FKs, indexes, policies.
- Idempotency drill: replay the same hourly batch twice → counters increment once per replay (asserted with `ON CONFLICT` semantics, not application dedupe).
- History drill: delete a block → its `link_clicks` rows survive (logical reference proven); delete a profile → its rows cascade.
- RLS drill: foreign `app.user_id` reads zero rows on both tables.
- Growth drill: 10x beacon replay changes blob bytes only; PG row count unchanged (zero-per-hit-write proven at the schema level).
- No-duplication drill: `grep -rn "CREATE TABLE bio.profile_views\|CREATE TABLE bio.link_clicks" docs/` returns exactly this file.

## Out of scope

- Intake, dedupe, consumer, and read endpoints (backend plan); dashboard screens (frontend plan); audience/UTM tables (audience database plan); realtime buckets (realtime database plan — ephemeral, not PG); unified earnings (read-only, no tables).

## Open questions

- Q1: Bot/crawler filtering — exclude known crawler UAs from aggregates in v1 (denylist, configurable), or count all hits?
- Q2: `interactions` definition — click-bearing visits only, or include banner/escape events (current: includes)?
