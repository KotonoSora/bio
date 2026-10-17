# Resume Documents - Database Schema Design (feature 17)

Phase 2 TL · `resume_db` migration `V1__resume_core.sql` (first migration of this service; schema `resume`). BA sources: `docs/01-requirements-ba/17-resume-documents/` (BR-01, BR-03, BR-05, BR-07). Baseline conventions apply (TEXT ids, RLS, no money columns, no cross-service FKs).

## DDL

```sql
CREATE SCHEMA IF NOT EXISTS resume;

CREATE TABLE resume.documents (
  id                TEXT PRIMARY KEY,              -- referenced by block payloads as documentId
  profile_id        TEXT NOT NULL,                 -- logical ref to profile-service; service-checked, no hard FK
  user_id           TEXT NOT NULL,                 -- owner (RLS + baseline convention)
  title             VARCHAR(120) NOT NULL DEFAULT 'My resume',
  template_id       TEXT NOT NULL DEFAULT 'classic',
  is_public         BOOLEAN NOT NULL DEFAULT FALSE,
  published_version INT,                           -- NULL until first publish
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uniq_public_doc ON resume.documents (profile_id) WHERE is_public;
CREATE INDEX idx_docs_user          ON resume.documents (user_id);
CREATE INDEX idx_docs_profile       ON resume.documents (profile_id);

CREATE TABLE resume.snapshots (                    -- insert-only; DELETE only via the archive job
  document_id   TEXT NOT NULL REFERENCES resume.documents(id) ON DELETE CASCADE,
  version       INT NOT NULL,
  snapshot_json JSONB NOT NULL,                    -- closed schema, validated in service
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (document_id, version)
);
CREATE INDEX idx_snap_doc ON resume.snapshots (document_id, version DESC);

ALTER TABLE resume.documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE resume.snapshots ENABLE ROW LEVEL SECURITY;
CREATE POLICY d_owner ON resume.documents FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY d_public ON resume.documents FOR SELECT TO app_role
  USING (is_public = TRUE AND published_version IS NOT NULL);
CREATE POLICY s_owner ON resume.snapshots FOR ALL TO app_role
  USING (EXISTS (SELECT 1 FROM resume.documents d WHERE d.id = document_id
                 AND d.user_id = current_setting('app.user_id', true)))
  WITH CHECK (EXISTS (SELECT 1 FROM resume.documents d WHERE d.id = document_id
                 AND d.user_id = current_setting('app.user_id', true)));
CREATE POLICY s_public ON resume.snapshots FOR SELECT TO app_role
  USING (EXISTS (SELECT 1 FROM resume.documents d WHERE d.id = document_id
                 AND d.is_public = TRUE AND d.published_version IS NOT NULL));

GRANT SELECT, INSERT, UPDATE, DELETE ON resume.documents TO app_role;
GRANT SELECT, INSERT, DELETE ON resume.snapshots TO app_role;   -- no UPDATE = structural immutability
```

## Notes

- `snapshot_json` schema (closed, in service): `{schema_version, basics, experience[], education[], skills[], links[], template_id, captured_at, source_profile_rev}`. Unknown keys rejected; every string sanitized before insert (stored-clean principle).
- Archive file convention: `resume/archives/{document_id}.ndjson` (one JSON line per version), private uploads volume, `>= 90 days` retention; retrieval is server-side only.
- Public reads are two paths: the internal `published` read scopes by `username + is_public + published_version` through the profile join in service code (RLS `d_public`/`s_public` back it); the owner path scopes by `user_id`.
- The archive job's DELETE is the only DELETE on `snapshots` besides cascade; guarded in service code (only rows that were verified-written to the archive).

## Verification drills

1. Fresh apply: `V1` clean on 18.6; two tables, partial unique, three indexes, four policies, grants with no UPDATE on `snapshots`.
2. Immutability drill: `UPDATE resume.snapshots` as `app_role` fails at the grant; a service save inserts N+1; version N bytes unchanged after profile edits.
3. Single-public drill: second `is_public=true` on the same profile violates `uniq_public_doc`; the publish transaction clears the old one instead of failing.
4. Archive drill: 31-day-old non-published version is appended to the NDJSON file (write verified) then deleted; published and recent versions stay; retrieval returns `archived: true`.
5. RLS drill: foreign `app.user_id` sees zero rows; anonymous sees only public documents + their snapshots; parity with service-level scoping.
6. Delete drill: document with linked visible block -> confirm required; confirmed path hides/unlinks (profile-service) then deletes; crash between leaves hidden block + document (retry-safe).
7. No-duplication drill: `CREATE TABLE resume.documents|resume.snapshots` appears only in this file.
