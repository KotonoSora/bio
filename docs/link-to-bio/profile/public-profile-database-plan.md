# Public Profile Database Plan (profiles + redirects — tables written once)

PLAN ref: P1a-2/P1a-3 (profile row, reserved slugs, 301 renames) + P1a-2 trust extension (verification + follower columns, consumed by `docs/link-to-bio/profile/verification-followers.md`) + S-1/S-6. BA sources: `docs/link-to-bio/profile/public-profile.md`, `docs/link-to-bio/profile/verification-followers.md`. Consumed by `docs/link-to-bio/profile/public-profile-backend-plan.md` (JPA entities map 1:1; endpoints are the only writers). No migration chains — the SQL below is the complete schema, applied once on an empty database.

## Purpose

Give every developer the exact tables behind profile identity in one place: what each table stores, why each column, constraint, index, and RLS policy exists, and how the tables relate. There is exactly one `CREATE TABLE` per table in this file; no ALTERs, no versions. If a column needs to change, this file changes — the table is never redefined anywhere else.

## Users

- Backend developer: runs this script once, then maps entities 1:1 (no invented columns).
- QA / reviewer: probes constraints and RLS directly in `psql` per Acceptance criteria.
- Future slices: read this before adding any profile-adjacent table (FK targets live here).

## Functional requirements

1. Conventions (all tables): `TEXT` ids (ulid strings, app-generated); `TIMESTAMPTZ` timestamps in ISO8601; `CITEXT` for usernames (case-insensitive unique without app-level lower() bugs); JSONB for schemaless breakdowns; every business table carries `user_id TEXT NOT NULL` + index + RLS; app-level `WHERE user_id` plus RLS both enforce ownership (either layer denies alone). Roles: DDL owned by the migration role; runtime `app_role` gets `GRANT SELECT, INSERT, UPDATE, DELETE` on these tables only.
2. Table `bio.profiles` — one row per creator page. Column groups: identity (`id`, `user_id`, `username`, `display_name`, `bio`) — the public address plus human-readable names; appearance (`avatar_key`, `theme_id`, `show_resume`, `seo_title`, `seo_desc`) — consumed by the themes-avatar and share-seo slices, stored here so one read serves the public page; trust (`verification_status`, `verified_at`, `follower_total_cached`, `follower_breakdown_json`, `follower_updated_at`) — badge state machine plus cached audience total (raw evidence lives in `verification_requests` + blob storage, never here); lifecycle (`is_public`, `created_at`, `updated_at`). `username` is `CITEXT UNIQUE NOT NULL` so `Alice` and `alice` collide at the database, not just in code.
3. Constraints on `bio.profiles` and why: `chk_username_len` (3–30, keeps URLs and QR payloads short) + `chk_username_chars` (`^[a-z0-9_]+$`, URL-safe without encoding) + `chk_username_reserved` (platform slugs that would shadow routes) — all three duplicate the app validator so a bypassed API still cannot corrupt the namespace; `chk_verification_status` (5-state machine, transitions enforced in service code, alphabet enforced here).
4. Table `bio.username_redirects` — permanent old→new mapping for 301s. `old_username CITEXT PRIMARY KEY` (never re-issuable: no delete path exists by design); `new_username` is a logical pointer to `profiles.username`, deliberately not a FK (usernames change; a FK would couple rename to constraint gymnastics). Rows are insert-only.
5. Indexes: `idx_profiles_user(user_id)` (dashboard reads); username unique index is implicit (login + public lookup hot path); `old_username` PK serves the 301 lookup. No extra indexes — exact-match lookups only, no LIKE scans in v1 (`pg_trgm` not needed here).
6. RLS: `p_owner` (all operations, owner-only via `current_setting('app.user_id', true)`); `p_public` (anonymous `SELECT` where `is_public`, the only rower the SSR path may see); redirects get `r_read` (public select for 301 resolution) plus insert restricted to the service path (ownership checked in code before insert, since redirects carry no `user_id`). Direct `psql` as `app_role` with a foreign `app.user_id` returns zero profile rows — verified in Acceptance criteria.
7. Growth: one profile row ≈ 1–2KB; 10M users ≈ 10–20GB — never the bottleneck (blocks and snapshots dominate per `docs/PLAN.md` size math). No partitioning, no archiving of these tables in v1.

## Data / API touches

- Full schema, applied once (baseline script run by Flyway on empty PG `18.6`; re-runnable only on empty databases — plain `CREATE`, no `IF NOT EXISTS` hedging, no ALTERs):
```sql
CREATE SCHEMA IF NOT EXISTS bio;
CREATE EXTENSION IF NOT EXISTS citext;
CREATE TABLE bio.profiles (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  username CITEXT NOT NULL UNIQUE,
  display_name VARCHAR(80) NOT NULL DEFAULT '',
  bio VARCHAR(500) NOT NULL DEFAULT '',
  avatar_key TEXT,
  theme_id TEXT NOT NULL DEFAULT 'default',
  is_public BOOLEAN NOT NULL DEFAULT TRUE,
  show_resume BOOLEAN NOT NULL DEFAULT FALSE,
  seo_title VARCHAR(70),
  seo_desc VARCHAR(160),
  verification_status TEXT NOT NULL DEFAULT 'none',
  verified_at TIMESTAMPTZ,
  follower_total_cached INT NOT NULL DEFAULT 0,
  follower_breakdown_json JSONB NOT NULL DEFAULT '{}',
  follower_updated_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_username_len CHECK (char_length(username::text) BETWEEN 3 AND 30),
  CONSTRAINT chk_username_chars CHECK (username::text ~ '^[a-z0-9_]+$'),
  CONSTRAINT chk_username_reserved CHECK (username::text NOT IN ('app','api','admin','static','r','cv','shop','login','signup','pricing','blog','go','a')),
  CONSTRAINT chk_verification_status CHECK (verification_status IN ('none','pending','verified','rejected','revoked'))
);
CREATE INDEX idx_profiles_user ON bio.profiles(user_id);
CREATE TABLE bio.username_redirects (
  old_username CITEXT PRIMARY KEY,
  new_username CITEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE bio.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE bio.username_redirects ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_owner ON bio.profiles FOR ALL TO app_role USING (user_id = current_setting('app.user_id', true)) WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY p_public ON bio.profiles FOR SELECT TO app_role USING (is_public = TRUE);
CREATE POLICY r_read ON bio.username_redirects FOR SELECT TO app_role USING (true);
CREATE POLICY r_write ON bio.username_redirects FOR INSERT TO app_role WITH CHECK (true);
GRANT SELECT, INSERT, UPDATE, DELETE ON bio.profiles, bio.username_redirects TO app_role;
```
- Consumed by: backend plan entities (`ProfileEntity`, `UsernameRedirectEntity` — field-for-field); repository queries assume the indexes above (add a query, check `EXPLAIN` uses them). No client, beacon, or SSR path writes here — writes go through the backend plan's endpoints only.

## Acceptance criteria

- Fresh-apply drill: script runs clean on empty PG `18.6`; `\d bio.profiles` shows all columns, 4 CHECKs, both policies; `\d bio.username_redirects` shows PK + 2 policies.
- Constraint drill: `Alice` vs `alice` collides (unique); `adMin`/`shop`/`x`/`UPPER!` inserts each violate their named CHECK; `verified` without ops flow is storable only via service (DB permits the alphabet, service permits the transition — separation asserted in review).
- RLS drill: `SET app.user_id='A'; SELECT * FROM bio.profiles` returns zero B rows; anonymous select sees only `is_public` rows; redirect select resolves old→new.
- Rename drill: insert redirect + update profile in one transaction; re-insert of the old name violates PK (permanent reservation proven).
- No-duplication drill: `grep -rn "CREATE TABLE bio.profiles" docs/` returns exactly this file.

## Out of scope

- Server logic and endpoints (`docs/link-to-bio/profile/public-profile-backend-plan.md`); screens (`docs/link-to-bio/profile/public-profile-frontend-plan.md`); block/analytics/shop tables (their own database plans); verification request rows (verification slice; table defined once there); blob storage for avatars/evidence.

## Open questions

- Q1: Second profile on pro — keep one-row-per-`user_id` (unenforced today) or add a partial unique index when pro ships?
- Q2: `r_write` is permissive at DB level by necessity (no `user_id` on redirects) — promote the ownership check into a trigger, or keep it code-only with audit?
