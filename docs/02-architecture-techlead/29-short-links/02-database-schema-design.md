# Short Links - Database Schema Design (feature 29)

Phase 2 TL · Database `links_db` (PostgreSQL 18.6), owned exclusively by `link-service`. Implements the `BR-*` rules of `docs/01-requirements-ba/29-short-links/02-user-stories-and-business-rules.md`.

## Isolation model (database-per-service)

- `links_db` is a dedicated database; only `link-service` connects. No cross-service tables, joins, or foreign keys; cross-service truth (profiles, analytics) moves via APIs/events only.
- Flyway owns DDL: `V1__short_links_baseline.sql` applies on boot; no ALTER chains - changes ship as new `V_n` migrations.
- Roles: `migration_role` (DDL owner), `app_role` (runtime, RLS-enforced); no superuser in app config.

## DDL (applied once; schema `links` inside the owned database)

```sql
CREATE SCHEMA IF NOT EXISTS links;

CREATE TABLE links.short_links (
  code          TEXT PRIMARY KEY,
  user_id       TEXT NOT NULL,
  destination   TEXT NOT NULL,
  kind          TEXT NOT NULL DEFAULT 'random',
  campaign_id   TEXT,
  expires_at    TIMESTAMPTZ,
  fallback_url  TEXT,
  status        TEXT NOT NULL DEFAULT 'live',
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_link_kind   CHECK (kind IN ('random','vanity')),
  CONSTRAINT chk_link_status CHECK (status IN ('live','suspended','expired','dormant')),
  CONSTRAINT chk_dest_scheme CHECK (destination LIKE 'https://%'),
  CONSTRAINT chk_fallback    CHECK (fallback_url IS NULL OR fallback_url LIKE 'https://%')
);

CREATE INDEX idx_links_user ON links.short_links (user_id);

ALTER TABLE links.short_links ENABLE ROW LEVEL SECURITY;

CREATE POLICY l_owner ON links.short_links
  FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));

CREATE POLICY l_public_resolve ON links.short_links
  FOR SELECT TO app_role
  USING (status = 'live');

GRANT SELECT, INSERT, UPDATE, DELETE ON links.short_links TO app_role;
```

`app.user_id` is set per request from the gateway identity, never from client input (BR-11). The resolve path reads under `l_public_resolve`; owner paths run under `l_owner`.

## Extension: resume-share refs (feature 20)

`V2__resume_refs.sql` (additive; feature 20 owns the semantics):

```sql
ALTER TABLE links.short_links
  ADD COLUMN resume_ref_user_id     TEXT,
  ADD COLUMN resume_ref_document_id TEXT;
CREATE UNIQUE INDEX uq_links_resume_ref ON links.short_links (resume_ref_user_id, resume_ref_document_id)
  WHERE resume_ref_document_id IS NOT NULL;
```

Idempotent resume-share mint (`POST /internal/v1/links/resume-share`) keys on the ref pair; resume-share mints are quota-exempt (bounded to one row per document by the partial unique); the `username.renamed.v1` consumer rewrites destinations for ref rows. Resume shares keep `kind='random'` - no CHECK change.

## Design notes

| Element | Rule | Why |
|---|---|---|
| `code` PK | base62 >= 7 chars, CSPRNG; insert failure = collision check | unguessable; double-mint fails closed |
| `destination` | `https:` only; blocklist enforced in service, not a table | static scheme backstop; dynamic list lives in cache |
| `campaign_id` | no FK | campaigns (features 48-50) are gated; must never block this table |
| `status` | closed 4-value alphabet | a 5th state cannot persist even if an API is bypassed |
| `expires_at` NULL | never expires | BR-06 |
| Deletes | none for users; account purge (feature 02) hard-deletes rows + objects by `user_id` via the participant contract | BR-08; history retained until purge |

Indexes: the PK serves resolve and collision checks; `idx_links_user` serves owner lists. No further indexes until a query proves itself.

## Service state outside PostgreSQL (owned by link-service)

| Store | Key | TTL | Purpose |
|---|---|---|---|
| Redis | `links:code:<code>` | 300 s | resolve cache; purged on suspend |
| Redis | `links:quota:<userId>:<yyyymm>` | to period end | mint counter (v1 500; rebuilt from DB if lost) |
| Redis | `links:blocklist:snapshot` | 300 s | blocklist snapshot for mint/resolve checks |
| MinIO | `hits/links/<yyyy>/<mm>/<dd>/*.ndjson` (bucket `link-hits`) | 90 days | raw hit-event archive written at resolve by the ~60 s flush; repaired/replayed by feature 31 |

## Growth and operations

- Row growth = mints only (quota-bounded, small rows); clicks never write this database (BR-10). No partitioning in v1; revisit at 50M rows.
- Backups: platform PITR; restore drill quarterly. Tombstones stay forever (evidence for AC-04).
- Migration discipline: never edit an applied migration; new tables arrive with their owning feature.

## Verification drills

1. Fresh apply: `flyway migrate` clean on an empty 18.6 container; `\d` shows PK, 4 CHECKs, index, 2 policies.
2. Collision: forced duplicate insert -> PK violation handled as bounded retry, never a 500.
3. RLS: `app_role` with `app.user_id='A'` cannot see or alter B's rows; the public resolve policy cannot see `expired` rows.
4. Scheme check: direct `http://` insert -> rejected by `chk_dest_scheme`.
5. Resume-ref drill (feature 20): a second resume-share mint for the same `(user, document)` hits the partial unique -> idempotent return, never a duplicate row.
