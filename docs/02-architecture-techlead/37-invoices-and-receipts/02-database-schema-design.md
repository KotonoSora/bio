# Invoices and Receipts - Database Schema Design (feature 37)

Phase 2 TL · `billing_db` migration `V4__proformas.sql` (chain row in `docs/02-architecture-techlead/32-monetize-overview/02-database-schema-design.md`). BA sources: `docs/01-requirements-ba/37-invoices-and-receipts/`. Receipts add no DDL (D-1); this migration is proformas + one `orders` column.

## Flyway V4 (`V4__proformas.sql`)

```sql
CREATE TABLE proformas (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  ref TEXT NOT NULL UNIQUE,
  target_kind TEXT NOT NULL CHECK (target_kind IN ('plan','product')),
  plan_term_version TEXT REFERENCES plan_terms(id),
  plan_code TEXT, term TEXT, product_id TEXT,
  amount_vnd INT NOT NULL CHECK (amount_vnd >= 0),
  expires_at TIMESTAMPTZ NOT NULL,
  status TEXT NOT NULL DEFAULT 'open'
    CHECK (status IN ('open','converted','superseded','expired')),
  converted_order_code BIGINT REFERENCES orders(order_code),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_pf_target CHECK (
    (target_kind = 'plan' AND plan_term_version IS NOT NULL AND plan_code IS NOT NULL
      AND term IS NOT NULL AND product_id IS NULL) OR
    (target_kind = 'product' AND product_id IS NOT NULL AND plan_term_version IS NULL
      AND plan_code IS NULL AND term IS NULL)),
  CONSTRAINT chk_pf_term CHECK (term IS NULL OR term IN ('monthly','yearly')),
  CONSTRAINT chk_pf_conv CHECK (status <> 'converted' OR converted_order_code IS NOT NULL)
);
CREATE UNIQUE INDEX uniq_pf_open_plan ON proformas (user_id, plan_code, term)
  WHERE status = 'open' AND target_kind = 'plan';
CREATE UNIQUE INDEX uniq_pf_open_product ON proformas (user_id, product_id)
  WHERE status = 'open' AND target_kind = 'product';
CREATE INDEX idx_pf_user ON proformas (user_id, created_at DESC);
CREATE INDEX idx_pf_expiry ON proformas (expires_at) WHERE status = 'open';

ALTER TABLE orders ADD COLUMN proforma_id TEXT REFERENCES proformas(id);
CREATE INDEX idx_orders_proforma ON orders (proforma_id) WHERE proforma_id IS NOT NULL;

ALTER TABLE proformas ENABLE ROW LEVEL SECURITY;
CREATE POLICY pf_owner ON proformas FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY pf_ops ON proformas FOR ALL TO app_role
  USING (current_setting('app.context', true) = 'ops')
  WITH CHECK (current_setting('app.context', true) = 'ops');
GRANT SELECT, INSERT, UPDATE ON proformas TO app_role;
```

## Semantics

- `ref` = `PF-` + 8 crypto-random chars from the unambiguous alphabet (`23456789ABCDEFGHJKMNPQRSTUVWXYZ`); unique-checked at insert with one retry; quoted in transfer memos (40's matching reads it as a candidate key; forward seam only).
- Amounts: snapshotted at issue from the active term row or 22's freeze; immutable until superseded/expired; integer VND only.
- Statuses: `open` (unique per scope) -> `converted` (first granting order; `converted_order_code` set) | `superseded` (price drift at create; new doc issuable) | `expired` (job, after `expires_at`). Terminal statuses never reopen; history retained.
- Conversion write (34's grant txn): `UPDATE proformas SET status='converted', converted_order_code=? WHERE id=? AND status='open'` - zero rows on replay or a second grant = no-op, no audit duplicate.
- Supersede write (33's create): same guard shape, `status='superseded'` only from `open`; superseded documents stay readable.
- `orders.proforma_id` is nullable and NOT unique: multiple orders may reference one proforma (rail attempts); exactly one conversion exists.
- No new Redis keys: resend throttles reuse `bill:rl:*`; token verification is stateless; no caching of proformas or receipts.

## Drills

| Drill | Asserts |
|---|---|
| Fresh apply | V4 runs after V3 clean; table + 4 indexes + `orders.proforma_id` visible; RLS enabled |
| Target CHECK | Plan row without term fails; product row with `plan_term_version` fails; both/neither populated fails; zero-amount allowed (0-VND promo quotes, 39) |
| Open-scope uniques | Second open plan proforma for the same (user, plan, term) fails insert; same for product; a superseded one does not block a new open row |
| Conversion race | Two orders reference one open proforma; parallel grants -> exactly one `converted`; loser probe writes zero rows; no duplicate audits |
| Expiry pass | `open` rows past `expires_at` flip on the run; second run = zero writes; terminal rows untouched |
| RLS + immutability probes | Foreign `app.user_id` reads zero rows; ops context reads for support; no DELETE grant exists |
| Single DDL owner | `grep -rn "CREATE TABLE proformas" docs/` matches exactly one file (this one) |
| No-PAN / printer greps | Receipt + proforma templates contain zero card/bank identifiers; zero PDF libraries referenced under `docs/` for this feature |
