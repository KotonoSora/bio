# Resume Snapshot Database Plan (documents + versions — tables written once)

PLAN ref: P2-1 (document model + `resume`-block linkage) + growth rule (versions >30d to blob; PG keeps current + recent) + S-1/S-6. BA source: `docs/resume-builder/documents/resume-snapshot.md`. Consumed by the snapshot backend plan (the only writer; splits next to this file). Profile/blocks rows live in the link-to-bio database plans (link targets, never redefined here). No migration chains — the SQL below is complete, applied once.

## Purpose

Give every developer the exact resume tables in one place: documents vs frozen versions, the single-public-snapshot rule, the 30-day archive boundary, and block linkage without dangling-public rows. Exactly one `CREATE TABLE` per table; no ALTERs, no versions. Cover letters are a `template_id` variant — never a third table.

## Users

- Backend developer: runs this script once, maps two entities 1:1, writes versions only through explicit-save flows.
- QA: probes immutability (reprint stability), archive retrieval, and RLS directly.
- Future slices (templates render, sharing links read, portability exports): read these tables through the contracts below, never with new writes except the backend plan's endpoints.

## Functional requirements

1. Conventions (repo-wide): `TEXT` ids (ulid, app-generated), `TIMESTAMPTZ` ISO8601, `user_id TEXT NOT NULL` + index + RLS plus app-level scoping, migration role owns DDL, `app_role` table grants, schema `resume` (per-module schemas per `docs/platform/stack/postgres-baseline.md`).
2. Table `resume.documents` — one row per resume document (identity, not content). `id` (stable UUID referenced by `resume`-block payloads); `profile_id` (logical reference to `bio.profiles(id)` — cross-module, service-checked, deliberately not a hard FK so modules stay extractable); `user_id`; `title VARCHAR(120)`; `template_id` (registry id in code, same rule as link-to-bio themes); `is_public` (selected for public `/:username/cv`); `published_version INT NULL` (which frozen version the public page renders — NULL means nothing public yet); timestamps. Partial unique `uniq_public_doc(profile_id) WHERE is_public` enforces one public resume per profile in v1 (a multi-snapshot picker is BA Q1 future, not a missing constraint).
3. Table `resume.snapshots` — frozen versions, one row per explicit save. Composite PK `(document_id, version)` (no surrogate — the version IS the address); `document_id` FK → `documents(id)` `ON DELETE CASCADE`; `snapshot_json JSONB NOT NULL` (schema `{basics, experience[], education[], skills[], links[], template_id, captured_at, source_profile_rev}` — validated in service, stored opaquely here); `created_at`. Versions are insert-only: no UPDATE path exists (a "re-save" inserts version N+1; immutability is structural, so reprint-after-profile-edit is byte-identical by construction).
4. Archive boundary (growth rule): snapshots with `created_at < now() - 30d` AND NOT the document's `published_version` move to blob storage (one NDJSON object per document, key `resume-archives/{document_id}.ndjson`, 90d+ retention) and are then deleted from PG. PG therefore holds published + recent only; retrieval of older versions reads the archive (read path specified in the backend plan). A scheduled job performs move-then-delete (never delete-then-move — crash order matters).
5. Block linkage: `profile_blocks` rows of `type=resume` carry `payload_json{document_id, show_contact, section_order?}` (payload shape owned by the link-blocks plans; referenced, never redefined). Deleting a document with a linked visible block requires explicit confirm; the link is hidden (payload cleared / block hidden via service), never left dangling-public — enforced as a service transaction (document delete + block unlink atomically), not a DB cascade (blocks live in another module's table).
6. Indexes: `idx_docs_user(user_id)` (dashboard lists); `idx_docs_profile(profile_id)` (per-profile documents); `idx_snap_doc(document_id, version DESC)` (version history reads; PK already covers it — this index is the DESC-order optimization, stated so nobody "adds it later" as a surprise). Snapshot bodies are TOAST-stored JSONB — no expression indexes in v1.
7. RLS: `d_owner`/`s_owner` (all operations, owner-only); `d_public` (anonymous `SELECT` where `is_public` — the SSR path); `s_public` (anonymous `SELECT` where the parent document `is_public`, via `EXISTS` subquery). Owner dashboard reads scope `user_id`; public reads scope `username + is_public` through the profile join in service code.

## Data / API touches

- Full tables, applied once (same baseline run, after profile/blocks scripts; schema `resume` created here):
```sql
CREATE SCHEMA IF NOT EXISTS resume;
CREATE TABLE resume.documents (
  id TEXT PRIMARY KEY, profile_id TEXT NOT NULL, user_id TEXT NOT NULL,
  title VARCHAR(120) NOT NULL DEFAULT 'My resume',
  template_id TEXT NOT NULL DEFAULT 'classic',
  is_public BOOLEAN NOT NULL DEFAULT FALSE, published_version INT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uniq_public_doc ON resume.documents(profile_id) WHERE is_public;
CREATE INDEX idx_docs_user ON resume.documents(user_id);
CREATE INDEX idx_docs_profile ON resume.documents(profile_id);
CREATE TABLE resume.snapshots (
  document_id TEXT NOT NULL REFERENCES resume.documents(id) ON DELETE CASCADE,
  version INT NOT NULL, snapshot_json JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (document_id, version)
);
ALTER TABLE resume.documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE resume.snapshots ENABLE ROW LEVEL SECURITY;
CREATE POLICY d_owner ON resume.documents FOR ALL TO app_role USING (user_id = current_setting('app.user_id', true)) WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY d_public ON resume.documents FOR SELECT TO app_role USING (is_public = TRUE);
CREATE POLICY s_owner ON resume.snapshots FOR ALL TO app_role USING (EXISTS (SELECT 1 FROM resume.documents d WHERE d.id = document_id AND d.user_id = current_setting('app.user_id', true))) WITH CHECK (true);
CREATE POLICY s_public ON resume.snapshots FOR SELECT TO app_role USING (EXISTS (SELECT 1 FROM resume.documents d WHERE d.id = document_id AND d.is_public = TRUE));
GRANT SELECT, INSERT, UPDATE, DELETE ON resume.documents, resume.snapshots TO app_role;
```
- Consumed by: snapshot backend plan (only writer); templates render, sharing reads, portability exports (readers, specified in their plans).

## Acceptance criteria

- Fresh-apply drill: script runs clean; `\d` shows both tables, partial unique, indexes, four policies.
- Immutability drill: profile edited after save → old version row unchanged (no UPDATE path exists); reprint returns identical bytes.
- Single-public drill: second `is_public=true` on the same profile violates `uniq_public_doc`.
- Archive drill: 31-day-old non-published version moves to blob then deletes; published version stays; retrieval reads the archive object.
- Delete drill: document with linked visible block deletes + unlinks atomically (no dangling-public block).
- RLS drill: foreign `app.user_id` sees zero rows; anonymous sees only public documents + their snapshots.
- No-duplication drill: `grep -rn "CREATE TABLE resume.documents\|CREATE TABLE resume.snapshots" docs/` returns exactly this file.

## Out of scope

- Save/duplicate/delete flows (backend plan); editor screens (frontend plan); template visuals and print CSS (templates plans); five-link sharing contract (sharing plans); backup/adapters (portability plans); block payload shape (link-blocks plans); avatar pipeline; AI JD-tailor (P3); publishing/agency (P4); replicas/ISR (P5).

## Open questions

- Q1: One public resume per profile, or a picker (BA Q1 — current: single, enforced)?
- Q2: Auto-snapshot on publish in addition to the manual button (BA Q2)?
