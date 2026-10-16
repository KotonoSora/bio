# Checkout Session - Database Schema Design (feature 33)

Phase 2 TL · `billing_db` migration `V1__billing_orders.sql` — the first DDL of the money kernel (chain forecast in `docs/02-architecture-techlead/32-monetize-overview/02-database-schema-design.md`: V1 here; V2 entitlement_ledger 35; V3 lifecycle 36; V4 proformas 37; V5 refunds 38; V6 promos 39; V7 rails 40). BA sources: `docs/01-requirements-ba/33-checkout-session/` (BR-03..BR-16).

## Flyway V1 (`V1__billing_orders.sql`)

```sql
CREATE TABLE orders (
  order_code       BIGINT PRIMARY KEY,              -- non-sequential unpredictable int64
  user_id          TEXT NOT NULL,
  provider         TEXT NOT NULL,                   -- registry member; immutable
  kind             TEXT NOT NULL,                   -- plan | merch | course | service | tip | ...
  plan             TEXT,
  product_id       TEXT,                            -- logical ref (shop_db)
  amount_vnd       INT  NOT NULL CHECK (amount_vnd > 0),
  status           TEXT NOT NULL DEFAULT 'pending'
                   CHECK (status IN ('pending','authorizing','paid','failed','cancelled',
                                     'expired','refunded','disputed','chargeback_reversed')),
  idempotency_key  TEXT UNIQUE,
  fingerprint_hash TEXT,
  buyer_email      TEXT,                            -- guests: mandatory (check below); RLS-protected
  ship_to          JSONB,                           -- merch only; minimized
  extras           JSONB,                           -- bounded scalar map (bookingId, ...)
  fee_snapshot     JSONB NOT NULL,                  -- {grossVnd, feeLines[], netVnd, rateVersion}
  psp_ref          TEXT,                            -- set by rail verification (40)
  expires_at       TIMESTAMPTZ NOT NULL,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_ord_target CHECK ((plan IS NULL) <> (product_id IS NULL)),
  CONSTRAINT chk_ord_guest_email CHECK (user_id NOT LIKE 'guest:%' OR buyer_email IS NOT NULL)
);
CREATE INDEX idx_orders_user ON orders (user_id, created_at DESC);
CREATE INDEX idx_orders_expiry ON orders (expires_at) WHERE status IN ('pending','authorizing');

CREATE TABLE audit_log (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  actor TEXT NOT NULL, action TEXT NOT NULL, target TEXT NOT NULL,
  detail JSONB, at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ops_config (
  key TEXT NOT NULL, version BIGINT NOT NULL, value JSONB NOT NULL,
  actor TEXT NOT NULL, reason TEXT NOT NULL, at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (key, version)
);

ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
CREATE POLICY ord_owner ON orders FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY ord_ops ON orders FOR ALL TO app_role
  USING (current_setting('app.context', true) = 'ops')
  WITH CHECK (current_setting('app.context', true) = 'ops');

GRANT SELECT, INSERT, UPDATE ON orders TO app_role;   -- no DELETE: financial records
GRANT SELECT, INSERT ON audit_log TO app_role;        -- append-only
GRANT SELECT, INSERT ON ops_config TO app_role;       -- append-versioned

INSERT INTO ops_config (key, version, value, actor, reason) VALUES
  ('billing.eligibility', 1, '{}', 'migration', 'seed: no restrictive cells'),
  ('billing.display_order', 1, '[]', 'migration', 'seed: API order verbatim'),
  ('billing.kill_switches', 1, '{}', 'migration', 'seed: none killed');
```

## Semantics

- Seed semantics: empty eligibility = no restrictive cells; empty display order = registry order; empty kill switches = none killed. Every later change appends a version (never updates); runtime reads the latest version (cached ≤ 5 s per 32 D-3).
- `order_code` comes from the non-sequential generator (32 D-9); collision-checked on insert. `provider` is written once and never updated (immutability covered by grep + tests).
- Plan intents stamp `plan_term_version` (V2, feature 35) at create: the priced term row; immutable once written; read paths compose plan titles from it (retired rows stay readable by reference).
- Guest invariant: `guest:` ids imply `buyer_email NOT NULL` (check constraint); guest rows can never match a session `app.user_id` — RLS makes them invisible to sessions by construction.
- `ship_to`/`extras` are accepted at create from validated inputs; they must never appear in logs, list projections, or non-`merch` flows (drill S-9).
- `audit_log`/`ops_config` are service-scoped tables (no owner rows): access is via internal/ops routes with the ops context; append-only grants make history tamper-evident.
- Deletion: PII scrub via UPDATE (buyer_email → null, ship_to → null, extras pruned) while rows are kept as financial records; counts feed feature 02's roster row (landed this change).

## Drills

| Drill | Asserts |
|---|---|
| Fresh apply | V1 runs clean after boot; indexes, CHECKs, policies visible in `\d` |
| RLS | Foreign `app.user_id` reads zero rows; ops context reads for audit; guest rows invisible to sessions |
| Unique race | concurrent same-key inserts → exactly one row, second replay returns it |
| Expiry index | pending+expiring query uses the partial index; sweep conditional update affects only lapsed rows |
| Guests | no null `user_id`; `guest:` rows always carry email; non-guest rows never carry a `guest:` id |
| Append-only | `app_role` UPDATE/DELETE on audit_log refused at the DB (no grant) |
| Single DDL owner | `grep -rn "CREATE TABLE orders" docs/` matches exactly one file (this one) |
