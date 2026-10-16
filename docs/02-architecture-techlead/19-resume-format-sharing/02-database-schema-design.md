# Resume Format Sharing - Database Schema Design (feature 19)

Phase 2 TL · `resume_db` migration `V2__format_sharing.sql` (V1 feature 17). BA sources: `docs/01-requirements-ba/19-resume-format-sharing/` (BR-01, BR-03, BR-04, BR-10). Baseline conventions apply (TEXT ids, RLS, no money columns, no cross-service FKs).

## DDL

```sql
CREATE TABLE resume.formats (                       -- one row per format VERSION (immutable when published)
  id                      TEXT PRIMARY KEY,         -- version-row id; clones pin this id
  slug                    TEXT NOT NULL,            -- groups versions for gallery URLs
  user_id                 TEXT NOT NULL,            -- author (RLS)
  name                    VARCHAR(120) NOT NULL DEFAULT 'Untitled format',
  version                 INT NOT NULL DEFAULT 1,
  layout_json             JSONB NOT NULL DEFAULT '{}',  -- closed layout-only schema; validated + re-scanned
  thumbnail_key           TEXT,                     -- auto placeholder render only (random file name)
  status                  TEXT NOT NULL DEFAULT 'draft',
  author_username_cached  TEXT NOT NULL DEFAULT '', -- frozen attribution string
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_format_version CHECK (version >= 1),
  CONSTRAINT chk_format_status CHECK (status IN ('draft','published','unpublished','removed')),
  CONSTRAINT uq_format_slug_version UNIQUE (slug, version)
);
CREATE INDEX idx_formats_user   ON resume.formats (user_id);
CREATE INDEX idx_formats_status ON resume.formats (status, updated_at DESC);
CREATE INDEX idx_formats_slug   ON resume.formats (slug);

CREATE TABLE resume.format_clones (                 -- copy-not-pointer: layout_json lives in the reuser's own set
  format_id  TEXT NOT NULL REFERENCES resume.formats(id) ON DELETE RESTRICT,  -- clones block hard-delete
  user_id    TEXT NOT NULL,                         -- reuser
  cloned_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (format_id, user_id)                  -- idempotent clone
);
CREATE INDEX idx_clones_user ON resume.format_clones (user_id);

ALTER TABLE resume.formats ENABLE ROW LEVEL SECURITY;
ALTER TABLE resume.format_clones ENABLE ROW LEVEL SECURITY;
CREATE POLICY f_owner ON resume.formats FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY f_public ON resume.formats FOR SELECT TO app_role
  USING (status = 'published');
CREATE POLICY c_owner ON resume.format_clones FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));

GRANT SELECT, INSERT, UPDATE, DELETE ON resume.formats TO app_role;
GRANT SELECT, INSERT, DELETE ON resume.format_clones TO app_role;
```

## Notes

- `layout_json` closed schema: `{sections [{key, visible, order, variant}], tokens{font, scale, accent, density}, a4{margins, pageBreakHints}}`; allowlist keys only; `<= 64 KB`; personal-data keys/styles/scripts/URLs rejected in service and re-scanned weekly.
- No PII columns exist (no email, no real name): the privacy invariant is columnar, not promissory.
- Clone copies the source `layout_json` into the reuser's own format row (created at clone time); `format_clones` records the source relationship for counts, `based-on` display, and RESTRICT protection. Author-side edits/unpublish/takedown touch only author rows.
- Thumbnails: `thumbnail_key` random; auto placeholder renders only (Q-06); purge participant deletes files.
- Takedown = status flip (`removed`) + audit; hard-delete is impossible while clones exist (`RESTRICT`).

## Verification drills

1. Fresh apply: `V2` clean on 18.6; CHECKs, unique, three indexes, three policies, grants present.
2. Immutability drill: editing after publish inserts `vN+1`; vN bytes unchanged; no UPDATE path touches `status='published'` rows' content.
3. Clone drill: clone byte-compares `layout_json`; author unpublish/edit leaves the copy intact; `RESTRICT` fires on hard-delete attempt with clones.
4. Takedown drill: `removed` hides gallery-wide (public policy excludes it); clones still render; audit row with reason.
5. Scrub drill (schema level): no personal-data column exists to receive leaks.
6. RLS drill: foreign `app.user_id` sees zero non-published rows; anonymous sees published only; clone rows owner-visible.
7. No-duplication drill: this file is the sole source of the two `CREATE TABLE` statements.
