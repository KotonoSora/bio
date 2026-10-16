# Shop Catalog - Database Schema Design (feature 22)

Phase 2 TL · `shop_db` migration `V1__shop_catalog.sql` (first migration of this service; schema `shop`). BA sources: `docs/01-requirements-ba/22-shop-catalog/` (BR-01..BR-15). Baseline conventions apply (TEXT ids, RLS, no money math, no cross-service FKs).

## DDL

```sql
CREATE SCHEMA IF NOT EXISTS shop;

CREATE TABLE shop.products (
  id             TEXT PRIMARY KEY,
  user_id        TEXT NOT NULL,
  profile_id     TEXT NOT NULL,
  kind           TEXT NOT NULL,
  title          VARCHAR(120) NOT NULL,
  description    TEXT NOT NULL DEFAULT '',
  price_vnd      INTEGER NOT NULL DEFAULT 0 CHECK (price_vnd >= 0),
  cover_key      TEXT,
  payload_json   JSONB NOT NULL DEFAULT '{}'::jsonb,
  status         TEXT NOT NULL DEFAULT 'draft',
  sort_position  INTEGER NOT NULL DEFAULT 0,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_product_kind   CHECK (kind IN ('merch','digital','paywall','course','tip','service','linkout')),
  CONSTRAINT chk_product_status CHECK (status IN ('draft','live','archived')),
  CONSTRAINT chk_price_kind     CHECK (price_vnd > 0 OR kind IN ('course','tip','linkout'))
);

CREATE TABLE shop.product_price_history (      -- append-only; no UPDATE/DELETE grants
  id          BIGSERIAL PRIMARY KEY,
  product_id  TEXT NOT NULL,
  user_id     TEXT NOT NULL,
  price_vnd   INTEGER NOT NULL CHECK (price_vnd >= 0),
  changed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_products_user         ON shop.products (user_id, status, sort_position);
CREATE INDEX idx_products_profile_live ON shop.products (profile_id, sort_position) WHERE status = 'live';
CREATE INDEX idx_price_history_product ON shop.product_price_history (product_id, changed_at DESC);

ALTER TABLE shop.products ENABLE ROW LEVEL SECURITY;
ALTER TABLE shop.product_price_history ENABLE ROW LEVEL SECURITY;

CREATE POLICY products_owner ON shop.products FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY products_public ON shop.products FOR SELECT TO app_role
  USING (status = 'live' AND current_setting('app.context', true) = 'svc');
CREATE POLICY price_history_owner ON shop.product_price_history FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));

GRANT SELECT, INSERT, UPDATE, DELETE ON shop.products TO app_role;
GRANT SELECT, INSERT ON shop.product_price_history TO app_role;   -- append-only
```

## Notes

- The kind ↔ price invariant lives in the constraint (BR-03); kind immutability after first publish (BR-04) is an app-level compare-and-set (`UPDATE ... WHERE status = 'draft' OR kind = :kind`).
- `payload_json` is closed per kind by the validator registry: `linkout {externalUrl}` shipped with feature 22; `merch {designId, designVersion, template, variants[]}` and `digital|paywall {}` (files via `digital_files` versions, feature 23) ship with feature 23; `course {completionRule}` (curriculum in `course_sections`/`course_lessons`, feature 24) ships with 24; `tip {presets[], floor, ceiling, wallEnabled}` and `service {durationMin, bufferBeforeMin, bufferAfterMin, meetingLink}` (booking machinery in feature 25) ship with 25 (same registry; unknown keys reject, never silent-drop).
- Status transitions are compare-and-set `UPDATE ... WHERE status = :expected`; a race loses with 409, never overwrites.
- Shop-page read: one indexed query (`idx_products_profile_live`); enumerated columns only — no `SELECT *`.
- Redis db 9 is reserved for this service; no keys ship with feature 22 (catalog reads are cacheless; the SSR cache lives in public-site db 5).
- `sort_position` reorder mirrors feature 08's set-equality + dense-rebuild transaction; one audit row per reorder (feature 08 code pattern reused).
- Price history rows are appended inside the same transaction as the product update.

## Verification drills

1. Fresh apply on empty PG `18.6`; `\d` shows constraints, both partial indexes, policies.
2. Constraint drill: free merch insert rejected; free course accepted; negative price rejected; unknown kind rejected.
3. RLS drill: foreign `app.user_id` reads zero rows; `app.context='svc'` sees live rows only, never drafts.
4. Append-only drill: UPDATE/DELETE on price history denied for `app_role`.
5. CAS drill: concurrent status flips — exactly one wins; the loser gets 409 `transition_conflict`.
6. Sort drill: reorder concurrency — no duplicate positions; mismatch rolls back with zero writes.
