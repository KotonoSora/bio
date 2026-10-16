# Public Profile - Database Schema Design (feature 06)

Phase 2 TL · Database `profile_db` (PostgreSQL 18.6), owned exclusively by `profile-service`. Implements the `BR-*` rules of `docs/01-requirements-ba/06-public-profile/02-user-stories-and-business-rules.md`.

## Isolation model (database-per-service)

- `profile_db` is a dedicated database; only `profile-service` connects. No cross-service tables, joins, or foreign keys; sibling features consume the row through the internal read API (feature 10) or write through this API (feature 09).
- Flyway owns DDL: `V1__profile_baseline.sql` applies on boot; `V2+` arrive with later features of this service (feature 02 adds `retired_usernames` as V2; feature 04 adds `custom_domains` + `domain_denylist` as V3; feature 07 adds `verification_requests` as V4; feature 08 adds `blocks` as V5; features 27-28 add the leads tables as V6/V7 to this same database).
- Roles: `migration_role` (DDL owner), `app_role` (runtime, RLS-enforced); no superuser in app config.

## DDL (applied once; schema `profile` inside the owned database)

```sql
CREATE SCHEMA IF NOT EXISTS profile;
CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE profile.profiles (
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
  CONSTRAINT chk_username_reserved CHECK (username::text NOT IN
    ('app','api','admin','static','r','cv','shop','login','signup','pricing','blog','go','a')),
  CONSTRAINT chk_verification_status CHECK (verification_status IN
    ('none','pending','verified','rejected','revoked'))
);
CREATE INDEX idx_profiles_user ON profile.profiles (user_id);

CREATE TABLE profile.username_redirects (
  old_username CITEXT PRIMARY KEY,
  new_username CITEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE profile.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE profile.username_redirects ENABLE ROW LEVEL SECURITY;

CREATE POLICY p_owner ON profile.profiles FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY p_public ON profile.profiles FOR SELECT TO app_role
  USING (is_public = TRUE);
CREATE POLICY r_read ON profile.username_redirects FOR SELECT TO app_role USING (true);
CREATE POLICY r_write ON profile.username_redirects FOR INSERT TO app_role WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON profile.profiles, profile.username_redirects TO app_role;
```

## Design notes

| Element | Rule | Why |
|---|---|---|
| `username` | `CITEXT UNIQUE NOT NULL` | case-insensitive uniqueness at the database, not just in code |
| `chk_username_*` | length, charset, reserved list | a bypassed API still cannot corrupt the namespace (BR-01, BR-02) |
| `username_redirects` | insert-only, `old_username` PK, no FK to profiles | permanent retirement; renames don't do constraint gymnastics |
| `verification_*`, `follower_*` | trust columns consumed by feature 07 | one row read serves badge + audience surfaces |
| `theme_id`, `avatar_key` | presentation pointers, written via this API | feature 09 owns the pipeline; the row stays the single read for SSR |
| Deletes | purge-only via feature 02 (user_id-scoped, username tombstone first); no user-facing delete path | BR-05; account deletion is the sole delete path |

Indexes: implicit `username` unique index serves claims, login lookup, and resolve; `idx_profiles_user` serves dashboard reads; the `old_username` PK serves 301 lookups. No LIKE scans in v1 (`pg_trgm` not needed).

## Feature 02 extension (account deletion)

- `V2__retired_usernames.sql`: `profile.retired_usernames{username CITEXT PRIMARY KEY, retired_at TIMESTAMPTZ NOT NULL DEFAULT now()}`, RLS read/insert for `app_role`. The feature 02 purge handler inserts the tombstone and deletes the profile row in one transaction; the `UsernamePolicy` retired check covers both `username_redirects` and `retired_usernames`.
- Deactivated accounts resolve as `missing`: resolve/sitemap consult `profile.acctstat:{userId}` (Redis, 60 s TTL), invalidated by `account.deletion_requested.v1`/`deletion_cancelled.v1` and refreshed via the account-status read on miss.

## Non-postgres state

| Store | Key | TTL | Purpose |
|---|---|---|---|
| Redis | `profile.acctstat:{userId}` | 60 s | account-status parity cache (feature 02 extension); username availability stays uncached by design |
| Redis | `profile.publish.pending` markers | 10 min | UI purge-chip polling aid, owned by feature 10 |

## Growth and operations

- 10M profiles at ~1-2 KB/row = 10-20 GB; `username_redirects` grows by renames only. No partitioning, no archiving in v1.
- Backups: platform PITR; restore drill quarterly. Audit retention 24 months (audit log is platform-owned).
- Migration discipline: never edit an applied migration; sibling features add new `V_n` files.

## Verification drills

1. Fresh apply: `flyway migrate` clean on an empty 18.6 container; `\d` shows all columns, 4 CHECKs, both RLS policies, and the citext unique.
2. Constraint drill: `Alice` vs `alice` collides; `adMin`/`shop`/`x`/`UPPER!` each violate their named CHECK; direct bypass inserts are asserted in integration tests.
3. RLS drill: `SET app.user_id='A'` returns zero B rows; public policy exposes only `is_public=TRUE`; redirect select resolves old -> new.
4. Rename drill: redirect insert + username update in one transaction; re-inserting the retired name violates the PK (permanent reservation proven).
5. No-duplication drill: `grep -rn "CREATE TABLE profile.profiles" docs/` returns exactly this file.
