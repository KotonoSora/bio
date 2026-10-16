# Course Builder - Database Schema Design (feature 24)

Phase 2 TL · `shop_db` migration `V3__shop_courses.sql` (extends V2 from feature 23). BA sources: `docs/01-requirements-ba/24-shop-courses/` (BR-01..BR-17).

## DDL

```sql
CREATE TABLE shop.course_sections (
  id         TEXT PRIMARY KEY,
  product_id TEXT NOT NULL,
  user_id    TEXT NOT NULL,
  position   INTEGER NOT NULL CHECK (position >= 0),
  title      VARCHAR(120) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_section_pos UNIQUE (product_id, position)
);

CREATE TABLE shop.course_lessons (
  id               TEXT PRIMARY KEY,
  section_id       TEXT NOT NULL,
  product_id       TEXT NOT NULL,
  user_id          TEXT NOT NULL,
  position         INTEGER NOT NULL CHECK (position >= 0),
  title            VARCHAR(120) NOT NULL,
  body             TEXT NOT NULL DEFAULT '',
  video_key        TEXT,
  video_duration_s INTEGER CHECK (video_duration_s >= 0),
  video_ready      BOOLEAN NOT NULL DEFAULT false,
  is_preview       BOOLEAN NOT NULL DEFAULT false,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_lesson_pos UNIQUE (section_id, position)
);

CREATE TABLE shop.course_materials (
  material_id   TEXT PRIMARY KEY,
  lesson_id     TEXT NOT NULL,
  product_id    TEXT NOT NULL,
  user_id       TEXT NOT NULL,
  file_path     TEXT NOT NULL,
  name          VARCHAR(120) NOT NULL,
  size_bytes    BIGINT NOT NULL CHECK (size_bytes > 0),
  mime_verified TEXT NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE shop.enrollments (
  id            TEXT PRIMARY KEY,
  product_id    TEXT NOT NULL,
  user_id       TEXT NOT NULL,               -- seller
  buyer_user_id TEXT NOT NULL,
  source        TEXT NOT NULL,
  status        TEXT NOT NULL DEFAULT 'active',
  cert_code     TEXT UNIQUE,                 -- set once at 100%
  completed_at  TIMESTAMPTZ,
  courtesy_until TIMESTAMPTZ,                -- set on refund
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_enrollment UNIQUE (product_id, buyer_user_id),
  CONSTRAINT chk_enr_source CHECK (source IN ('free','paid')),
  CONSTRAINT chk_enr_status CHECK (status IN ('active','revoked'))
);

CREATE TABLE shop.lesson_progress (
  product_id    TEXT NOT NULL,
  buyer_user_id TEXT NOT NULL,
  user_id       TEXT NOT NULL,               -- seller
  lesson_id     TEXT NOT NULL,
  position_s    INTEGER NOT NULL DEFAULT 0 CHECK (position_s >= 0),
  completed     BOOLEAN NOT NULL DEFAULT false,
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (product_id, buyer_user_id, lesson_id)   -- survives re-enrollment
);

CREATE INDEX idx_sections_product ON shop.course_sections (product_id, position);
CREATE INDEX idx_lessons_section  ON shop.course_lessons (section_id, position);
CREATE INDEX idx_lessons_product  ON shop.course_lessons (product_id, is_preview);
CREATE INDEX idx_materials_lesson ON shop.course_materials (lesson_id);
CREATE INDEX idx_enroll_buyer     ON shop.enrollments (buyer_user_id, status);
CREATE INDEX idx_enroll_product   ON shop.enrollments (product_id, status);
CREATE INDEX idx_progress_buyer   ON shop.lesson_progress (buyer_user_id, product_id);

ALTER TABLE shop.course_sections  ENABLE ROW LEVEL SECURITY;
ALTER TABLE shop.course_lessons   ENABLE ROW LEVEL SECURITY;
ALTER TABLE shop.course_materials ENABLE ROW LEVEL SECURITY;
ALTER TABLE shop.enrollments      ENABLE ROW LEVEL SECURITY;
ALTER TABLE shop.lesson_progress  ENABLE ROW LEVEL SECURITY;

-- owner policies (user_id) on all five; representative form:
CREATE POLICY sections_owner ON shop.course_sections FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
-- lessons_owner, materials_owner, enroll_owner, progress_owner: same shape.
CREATE POLICY lessons_svc   ON shop.course_lessons FOR SELECT TO app_role
  USING (current_setting('app.context', true) = 'svc');   -- syllabus preview read
CREATE POLICY materials_svc ON shop.course_materials FOR SELECT TO app_role
  USING (current_setting('app.context', true) = 'svc');   -- ticket mint file read
CREATE POLICY enroll_buyer  ON shop.enrollments FOR SELECT TO app_role
  USING (buyer_user_id = current_setting('app.user_id', true));
CREATE POLICY enroll_svc    ON shop.enrollments FOR ALL TO app_role
  USING (current_setting('app.context', true) = 'svc')
  WITH CHECK (current_setting('app.context', true) = 'svc');
CREATE POLICY progress_buyer ON shop.lesson_progress FOR ALL TO app_role
  USING (buyer_user_id = current_setting('app.user_id', true))
  WITH CHECK (buyer_user_id = current_setting('app.user_id', true));
CREATE POLICY progress_svc  ON shop.lesson_progress FOR ALL TO app_role
  USING (current_setting('app.context', true) = 'svc')
  WITH CHECK (current_setting('app.context', true) = 'svc');

GRANT SELECT, INSERT, UPDATE, DELETE ON shop.course_sections, shop.course_lessons,
  shop.course_materials, shop.enrollments, shop.lesson_progress TO app_role;
```

## Notes

- Limits (50/200/10/0-10) are application-enforced at save; position uniqueness is DB-enforced (dense rebuild like feature 22 reorder).
- `cert_code` mints once via `UPDATE ... WHERE cert_code IS NULL AND completed_at IS NULL`; the unique index makes duplicate mints impossible.
- Progress PK `(product_id, buyer_user_id, lesson_id)` is the re-enrollment-safe idempotency key (D-8); the flusher upserts with `GREATEST(position_s, :incoming)` and sticky `completed`.
- Redis overrides: `course:progress:{productId}:{buyerUserId}` hash → merged reads; deleted on purge and after flush confirms.
- Ticket mint never writes PostgreSQL; material mint increments nothing (no counters here — anomaly flag set on the enrollment row is v2; v1 flags log + support).
- Video files live under `shop/courses/videos/`; materials under `shop/courses/materials/`; both random names, purge-scoped.

## Verification drills

1. Fresh apply V2→V3 on PG `18.6`; `\d` shows constraints + policies.
2. Density drill: duplicate section position rejected; reorder rolls back on mismatch.
3. Progress drill: upsert twice with lower position → `GREATEST` keeps the higher; `completed` never un-sets.
4. Mint drill: concurrent completion flushes → one `cert_code`; second update is a no-op.
5. RLS drill: foreign buyer reads zero progress rows; svc reads lessons for the syllabus; buyer context cannot read materials.
6. Refund drill: revoke preserves progress (`courtesy_until` set); the sweep deletes only expired rows.
