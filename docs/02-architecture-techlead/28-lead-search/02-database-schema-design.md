# Lead Search - Database Schema Design (feature 28)

Phase 2 TL · `profile_db` migration `V7__lead_workspace.sql` (chain: V6 feature 27; canonical numbering per `docs/02-architecture-techlead/27-lead-forms/02-database-schema-design.md`). BA sources: `docs/01-requirements-ba/28-lead-search/` (BR-01..BR-16).

## DDL

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_leads_email_trgm ON profile.leads USING gin (email gin_trgm_ops);
CREATE INDEX idx_leads_name_trgm  ON profile.leads USING gin (name gin_trgm_ops);

CREATE TABLE profile.lead_notes (
  id             TEXT PRIMARY KEY,
  lead_id        TEXT NOT NULL,
  user_id        TEXT NOT NULL,
  author_user_id TEXT NOT NULL,
  author_kind    TEXT NOT NULL DEFAULT 'creator',
  body           VARCHAR(2000) NOT NULL,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_note_author CHECK (author_kind IN ('creator','support'))
);
CREATE INDEX idx_leadnotes_lead ON profile.lead_notes (lead_id, created_at);

CREATE TABLE profile.lead_tags (
  lead_id    TEXT NOT NULL,
  user_id    TEXT NOT NULL,
  tag        VARCHAR(40) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (lead_id, tag)
);
CREATE INDEX idx_leadtags_user ON profile.lead_tags (user_id, tag);

CREATE TABLE profile.lead_tag_vocabulary (
  user_id    TEXT NOT NULL,
  tag        VARCHAR(40) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, tag)
);

CREATE TABLE profile.lead_queries (
  id          TEXT PRIMARY KEY,
  user_id     TEXT NOT NULL,
  query       VARCHAR(120) NOT NULL,
  filters_json JSONB,
  searched_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_queries_user ON profile.lead_queries (user_id, searched_at DESC);

CREATE TABLE profile.lead_export_jobs (
  id               TEXT PRIMARY KEY,
  user_id          TEXT NOT NULL,
  mode             TEXT NOT NULL,
  filters_json     JSONB,
  selection_ids    JSONB,
  row_count        INTEGER CHECK (row_count >= 0),
  status           TEXT NOT NULL DEFAULT 'queued',
  file_path        TEXT,
  grant_expires_at TIMESTAMPTZ,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  finished_at      TIMESTAMPTZ,
  expires_at       TIMESTAMPTZ NOT NULL DEFAULT now() + interval '7 days',
  CONSTRAINT chk_export_mode   CHECK (mode IN ('filters','ids')),
  CONSTRAINT chk_export_status CHECK (status IN ('queued','running','done','failed')),
  CONSTRAINT chk_export_payload CHECK (
    (mode = 'filters' AND filters_json IS NOT NULL) OR
    (mode = 'ids' AND selection_ids IS NOT NULL))
);
CREATE INDEX idx_exports_user ON profile.lead_export_jobs (user_id, created_at DESC);

ALTER TABLE profile.lead_notes, profile.lead_tags, profile.lead_tag_vocabulary,
  profile.lead_queries, profile.lead_export_jobs ENABLE ROW LEVEL SECURITY;

CREATE POLICY lnote_owner ON profile.lead_notes FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY lnote_svc ON profile.lead_notes FOR ALL TO app_role
  USING (current_setting('app.context', true) = 'svc')
  WITH CHECK (current_setting('app.context', true) = 'svc');
-- identical owner + svc policy pairs for lead_tags, lead_tag_vocabulary,
-- lead_queries, lead_export_jobs (names ltag_*, lvocab_*, lquery_*, lexp_*).

GRANT SELECT, INSERT, UPDATE, DELETE ON profile.lead_notes, profile.lead_tags,
  profile.lead_tag_vocabulary, profile.lead_queries, profile.lead_export_jobs TO app_role;
```

## Notes

- Search plan: `gin_trgm_ops` indexes serve `ILIKE '%q%'` and similarity ranking; exact-match precedence is a `CASE` rank on normalized equality before similarity; all predicates pre-scoped (`user_id = app.user_id`); no plan without the scope filter exists in code.
- Count/export parity: one predicate builder produces both the `COUNT(*)` and the export `WHERE`; the job freezes `row_count` at enqueue.
- Append-only enforcement: notes/tags are insert-only in app code (no UPDATE paths); alerts fire on UPDATE/DELETE statements against `lead_notes`/`lead_tags` (same posture as `lead_events`).
- Vocabulary: auto-add on first use until 50 per account (`SELECT count(*) ... FOR UPDATE` guard); deletions absent; foreign vocabularies invisible via RLS.
- Query history: write-on-search (100 % or sampled — fixed at 100 % at this volume); trimmed to the newest 20 per account by a nightly sweep; fire-and-forget (NFR-04).
- Export jobs: worker picks `queued` rows, streams CSV (fixed column order per BR-11) to `profile/exports/leads/{userId}/{jobId}.csv` on the uploads volume, sets `done` + `file_path`; grants are HMAC-derived (`jobId|userId|exp`), never stored; quota counters in Redis db 4 (`lead:exports:{day}` TTL 24 h, `lead:exportopen:{userId}` gauge); 7-day sweep deletes files + rows.
- Purge (feature 02 participant): delete by `user_id` across the five tables + uploads namespace `profile/exports/leads/{userId}/`; counts returned for the certificate.
- Redis db 4 additions: `lead:exports:*`, `lead:exportopen:*`, `lead:reinvite:{leadId}` (3/day cap counter, TTL 24 h).

## Verification drills

1. Fresh apply V6→V7 on PG `18.6`; `EXPLAIN` shows GIN usage for `%q%` searches at the 100k fixture.
2. Scope drill: overlapping cross-creator fixtures (same email/name in two accounts) return zero foreign rows for both the owner and any filter combination (RLS probe).
3. Vocabulary drill: 50-tag cap enforced under concurrent inserts (two writers, one row wins); foreign vocabulary invisible.
4. Export drill: `filters` and `ids` modes; frozen count matches streamed rows byte-for-byte on the stable columns; `chk_export_payload` rejects empty modes.
5. Grant drill: HMAC grant verifies only for the owning `userId`; expiry at 15 min; file private (no direct storage URL exists).
6. Purge drill: five-table delete + files removed; certificate counts exact.
