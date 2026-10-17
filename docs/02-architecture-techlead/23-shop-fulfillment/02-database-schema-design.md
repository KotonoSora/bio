# Shop Fulfillment - Database Schema Design (feature 23)

Phase 2 TL · `shop_db` migration `V2__shop_fulfillment.sql` (extends V1 from feature 22). BA sources: `docs/01-requirements-ba/23-shop-fulfillment/` (BR-01..BR-16).

## DDL

```sql
CREATE TABLE shop.merch_designs (
  design_id  TEXT NOT NULL,
  version    INTEGER NOT NULL CHECK (version >= 1),
  user_id    TEXT NOT NULL,
  profile_id TEXT NOT NULL,
  file_key   TEXT NOT NULL,
  source     TEXT NOT NULL DEFAULT 'upload',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (design_id, version),
  CONSTRAINT chk_design_source CHECK (source IN ('upload'))
);

CREATE TABLE shop.print_jobs (
  id               TEXT PRIMARY KEY,
  order_id         TEXT NOT NULL,
  order_line       INTEGER NOT NULL CHECK (order_line >= 1),
  user_id          TEXT NOT NULL,            -- seller
  buyer_user_id    TEXT,
  design_id        TEXT NOT NULL,
  design_version   INTEGER NOT NULL,
  template         TEXT NOT NULL,
  variant          TEXT NOT NULL,
  quantity         INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 20),
  ship_to_json     JSONB,                    -- minimized fulfillment fields; buyer-scrubbed
  status           TEXT NOT NULL DEFAULT 'pending_print',
  partner_ref      TEXT,
  partner_sla_date DATE,                     -- partner-provided; always partner-labeled
  cost_base_vnd    INTEGER CHECK (cost_base_vnd >= 0),
  reprint_of       TEXT,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_job_line UNIQUE (order_id, order_line),
  CONSTRAINT chk_job_status CHECK (status IN ('pending_print','printing','shipped','delivered','failed','cancelled')),
  CONSTRAINT chk_job_template CHECK (template IN ('tee','hoodie','mug','tote'))
);

CREATE TABLE shop.digital_files (
  product_id     TEXT NOT NULL,
  version        INTEGER NOT NULL CHECK (version >= 1),
  file_id        TEXT NOT NULL UNIQUE,
  user_id        TEXT NOT NULL,
  role           TEXT NOT NULL DEFAULT 'file',
  file_path      TEXT NOT NULL,
  size_bytes     BIGINT NOT NULL CHECK (size_bytes > 0),
  mime_verified  TEXT NOT NULL,
  upgrade_policy TEXT NOT NULL DEFAULT 'free',
  status         TEXT NOT NULL DEFAULT 'draft',
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (product_id, version),
  CONSTRAINT chk_file_role CHECK (role IN ('file','body')),
  CONSTRAINT chk_file_upgrade CHECK (upgrade_policy IN ('free','paid')),
  CONSTRAINT chk_file_status CHECK (status IN ('draft','current','superseded'))
);

CREATE TABLE shop.entitlements (
  id             TEXT PRIMARY KEY,
  order_id       TEXT NOT NULL UNIQUE,        -- idempotent grant key
  product_id     TEXT NOT NULL,
  file_version   INTEGER NOT NULL,
  user_id        TEXT NOT NULL,               -- seller
  buyer_user_id  TEXT NOT NULL,
  downloads_used INTEGER NOT NULL DEFAULT 0 CHECK (downloads_used >= 0),
  max_downloads  INTEGER NOT NULL DEFAULT 5 CHECK (max_downloads BETWEEN 1 AND 100),
  expires_at     TIMESTAMPTZ NOT NULL,
  status         TEXT NOT NULL DEFAULT 'active',
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_ent_status CHECK (status IN ('active','flagged','revoked'))
);

CREATE UNIQUE INDEX uq_file_current ON shop.digital_files (product_id) WHERE status = 'current';
CREATE INDEX idx_designs_user ON shop.merch_designs (user_id, created_at DESC);
CREATE INDEX idx_jobs_user    ON shop.print_jobs (user_id, status, created_at DESC);
CREATE INDEX idx_jobs_buyer   ON shop.print_jobs (buyer_user_id);
CREATE INDEX idx_files_user   ON shop.digital_files (user_id, product_id);
CREATE INDEX idx_ent_buyer    ON shop.entitlements (buyer_user_id, status);
CREATE INDEX idx_ent_product  ON shop.entitlements (product_id, created_at DESC);

ALTER TABLE shop.merch_designs ENABLE ROW LEVEL SECURITY;
ALTER TABLE shop.print_jobs    ENABLE ROW LEVEL SECURITY;
ALTER TABLE shop.digital_files ENABLE ROW LEVEL SECURITY;
ALTER TABLE shop.entitlements  ENABLE ROW LEVEL SECURITY;

CREATE POLICY designs_owner ON shop.merch_designs FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY designs_svc ON shop.merch_designs FOR SELECT TO app_role
  USING (current_setting('app.context', true) = 'svc');

CREATE POLICY jobs_owner ON shop.print_jobs FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY jobs_svc ON shop.print_jobs FOR ALL TO app_role
  USING (current_setting('app.context', true) = 'svc')
  WITH CHECK (current_setting('app.context', true) = 'svc');

CREATE POLICY files_owner ON shop.digital_files FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY files_svc ON shop.digital_files FOR SELECT TO app_role
  USING (current_setting('app.context', true) = 'svc');

CREATE POLICY ent_seller ON shop.entitlements FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY ent_buyer ON shop.entitlements FOR SELECT TO app_role
  USING (buyer_user_id = current_setting('app.user_id', true));
CREATE POLICY ent_buyer_update ON shop.entitlements FOR UPDATE TO app_role
  USING (buyer_user_id = current_setting('app.user_id', true))
  WITH CHECK (buyer_user_id = current_setting('app.user_id', true));
CREATE POLICY ent_svc ON shop.entitlements FOR ALL TO app_role
  USING (current_setting('app.context', true) = 'svc')
  WITH CHECK (current_setting('app.context', true) = 'svc');

GRANT SELECT, INSERT, UPDATE, DELETE ON shop.merch_designs, shop.print_jobs,
  shop.digital_files, shop.entitlements TO app_role;
```

## Notes

- Exactly-once dispatch = `uq_job_line`; a replay hits the unique key and changes nothing.
- Status transitions are compare-and-set `UPDATE ... WHERE status = :expected`; terminal states (`delivered|failed|cancelled`) have no outgoing path.
- `chk_design_source` carries only `upload`; `ai` joins exactly when feature 16 ships (never stubbed).
- `ship_to_json` is write-once; buyer purge nulls it + `buyer_user_id` (D-8); owner reads and every DTO enumerate columns — `ship_to_json` is never selected outside the adapter path.
- Bodies (`role='body'`) are sanitized markdown payloads; the mint path refuses `role='body'` (bodies render only through the fragment endpoint).
- One `current` row per product via the partial unique index; publish flips within one transaction + audit.
- Redis db 9: `merch:callbacks` stream (group `partner-cb`), `merch:cb:{eventId}` dedupe (24 h), `digital:mint:{entitlementId}` single-flight (30 s).

## Verification drills

1. Fresh apply V1→V2 on PG `18.6`; `\d` shows constraints, partial index, policies.
2. Replay drill: insert the same `(order_id, order_line)` twice → one row.
3. CAS drill: concurrent `printing → shipped` and `printing → failed`; exactly one wins; terminal rows reject updates.
4. RLS drill: seller A sees only own designs/jobs; buyer B reads only own entitlements; svc context reads file rows while buyer context cannot.
5. Scrub drill: buyer purge nulls `ship_to_json` + `buyer_user_id` and deletes their entitlements; seller rows remain.
6. Partial-index drill: a second `current` version for one product is rejected.
