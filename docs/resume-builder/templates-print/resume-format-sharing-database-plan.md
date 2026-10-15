# Resume Format Sharing Database Plan (formats + clones — tables written once)

PLAN ref: P2-1 extension (community formats as layout-only configs) + moderation/audit trail + S-1/S-6. BA source: `docs/resume-builder/templates-print/resume-format-sharing.md`. Consumed by the format-sharing backend plan (the only writer; splits next to this file). Snapshot rows live in `docs/resume-builder/documents/resume-snapshot-database-plan.md`. No migration chains — the SQL below is complete, applied once.

## Purpose

Give every developer the exact community-format tables in one place: versioned immutable layouts, clone records, moderation states, and the privacy invariant made structural (no personal-data column exists to leak). Exactly one `CREATE TABLE` per table; no ALTERs, no versions.

## Users

- Backend developer: runs this script once, maps two entities 1:1, publishes only through versioned inserts.
- QA: probes scrub rejection, clone isolation, and takedown effects.
- Moderator/ops: answers "whose format is this" from attribution columns alone.

## Functional requirements

1. Conventions (repo-wide): `TEXT` ids, `TIMESTAMPTZ`, `user_id TEXT NOT NULL` + index + RLS plus app scoping, migration role owns DDL, `app_role` grants, schema `resume`.
2. Table `resume.formats` — one row per format VERSION (immutable versions, like snapshots). `id` (ulid, stable across versions? No — version rows need identity: `format_group_id` groups versions, `id` is the version row PK… simpler exact-once design: `id` PK per version row; `slug` groups versions for gallery URLs (`slug + vN` addressable); `user_id` (author); `name`; `version INT`; `layout_json JSONB` (closed layout-only schema — sections order/visibility, layout variants, tokens, A4 rules; personal-data keys rejected in service AND re-scanned by the verification job); `thumbnail_key` (placeholder-render only); `status` (draft|published|unpublished|removed); `author_username_cached` (frozen attribution string — survives renames/deletion per BA rules, updated opportunistically never rewritten historically); timestamps. UNIQUE `(slug, version)`; publishing vN+1 inserts a new row (published rows never UPDATE — gallery stability for existing viewers).
3. Table `resume.format_clones` — one row per clone action. `format_id` FK → `formats(id)` `ON DELETE RESTRICT` (a format row with clones cannot be hard-deleted — takedown sets `removed`, history preserved); `user_id` (reuser); `cloned_at`. Clone copies `layout_json` into the reuser's own template set (their copy is their data afterward — author edits/unpublish never propagate, proven by the absence of any live reference: clones store the JSON, not a pointer). Counts derive from `COUNT(*)` queries (no cached counter column to drift; gallery cards accept query-time counts with short cache).
4. Statuses and gallery visibility: public gallery reads `status=published` only (RLS-adjacent predicate in query + app check); `unpublished` hides from gallery but keeps existing clones working (their copies are independent); `removed` (moderator takedown) hides gallery-wide within purge SLA while clones keep working (same independence); `draft` visible to author only. Takedown is a status flip + audit, never a delete (evidence preserved).
5. Attribution columns: `author_username_cached` (display string, frozen at publish; rename follows 301 via profile lookup at render, deletion renders the frozen string non-linked — all presentation logic, no extra columns needed). No author PII columns exist (no email, no real name — the privacy invariant is columnar, not promissory).
6. Indexes: `idx_formats_user(user_id)` (my formats); `idx_formats_status(status, updated_at DESC)` (gallery lists); `idx_clones_format(format_id)` (clone counts, takedown impact review); `idx_clones_user(user_id)` (my clones).
7. RLS: `f_owner` (all operations, owner-only); `f_public` (anonymous `SELECT` where `status=published` — gallery + detail, rate-limited at the API); `c_owner` (clone rows owner-visible to the reuser); clone creation allowed for any authenticated user (insert with own `user_id` — the check is the session, asserted in service). Moderator writes go through the audited ops path with `ROLE_OPS`.

## Data / API touches

- Full tables, applied once (same baseline run, after snapshot tables):
```sql
CREATE TABLE resume.formats (
  id TEXT PRIMARY KEY, slug TEXT NOT NULL, user_id TEXT NOT NULL,
  name VARCHAR(120) NOT NULL DEFAULT 'Untitled format', version INT NOT NULL DEFAULT 1,
  layout_json JSONB NOT NULL DEFAULT '{}', thumbnail_key TEXT,
  status TEXT NOT NULL DEFAULT 'draft', author_username_cached TEXT NOT NULL DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_format_version CHECK (version >= 1),
  CONSTRAINT chk_format_status CHECK (status IN ('draft','published','unpublished','removed')),
  CONSTRAINT uq_format_slug_version UNIQUE (slug, version)
);
CREATE INDEX idx_formats_user ON resume.formats(user_id);
CREATE INDEX idx_formats_status ON resume.formats(status, updated_at DESC);
CREATE TABLE resume.format_clones (
  format_id TEXT NOT NULL REFERENCES resume.formats(id) ON DELETE RESTRICT,
  user_id TEXT NOT NULL, cloned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (format_id, user_id)
);
CREATE INDEX idx_clones_user ON resume.format_clones(user_id);
ALTER TABLE resume.formats ENABLE ROW LEVEL SECURITY;
ALTER TABLE resume.format_clones ENABLE ROW LEVEL SECURITY;
CREATE POLICY f_owner ON resume.formats FOR ALL TO app_role USING (user_id = current_setting('app.user_id', true)) WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY f_public ON resume.formats FOR SELECT TO app_role USING (status = 'published');
CREATE POLICY c_owner ON resume.format_clones FOR ALL TO app_role USING (user_id = current_setting('app.user_id', true)) WITH CHECK (user_id = current_setting('app.user_id', true));
GRANT SELECT, INSERT, UPDATE, DELETE ON resume.formats, resume.format_clones TO app_role;
```
- Consumed by: format-sharing backend plan (only writer); gallery reads (public predicate); clone-count queries.

## Acceptance criteria

- Fresh-apply drill: script runs clean; `\d` shows both tables, CHECKs, unique, indexes, three policies.
- Immutability drill: editing a published format inserts vN+1; vN row bytes unchanged (existing viewers unaffected).
- Clone drill: clone copies JSON (byte-compare at clone time); author unpublish/edit leaves the clone intact; format with clones cannot be hard-deleted (RESTRICT fires).
- Takedown drill: `removed` hides gallery-wide; clones keep working; audit row exists.
- Scrub drill (schema level): no personal-data column exists to receive leaks (`\d` inspection).
- RLS drill: foreign `app.user_id` sees zero non-published rows; anonymous sees published only.
- No-duplication drill: `grep -rn "CREATE TABLE resume.formats\|CREATE TABLE resume.format_clones" docs/` returns exactly this file.

## Out of scope

- Publish/scrub/clone/moderation flows (backend plan); builder/gallery screens (frontend plan); snapshot versioning (snapshot database plan); built-in template registry (templates plans); share links (sharing plans); backup/adapters (portability plans); AI tailoring (P3); publishing/agency (P4); replicas/ISR (P5).

## Open questions

- Q1: Pre-publish review queue vs publish-then-report with fast takedown (BA Q1)?
- Q2: Clone-count caching — query-time counts hold to what gallery traffic before a cached counter is needed?
