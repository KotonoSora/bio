# Refunds - Database Schema Design (feature 38)

Phase 2 TL · `billing_db` migration `V5__refunds.sql` (chain row in `docs/02-architecture-techlead/32-monetize-overview/02-database-schema-design.md`). BA sources: `docs/01-requirements-ba/38-refunds/`. No `orders`/`subscriptions` DDL changes (D-2).

## Flyway V5 (`V5__refunds.sql`)

```sql
CREATE TABLE refunds (
  id TEXT PRIMARY KEY,
  order_code BIGINT NOT NULL REFERENCES orders(order_code),
  user_id TEXT NOT NULL,
  provider TEXT NOT NULL,
  amount_vnd INT NOT NULL CHECK (amount_vnd > 0),
  status TEXT NOT NULL DEFAULT 'requested'
    CHECK (status IN ('requested','approved','executing','reversed','rejected','failed')),
  initiated_by TEXT NOT NULL CHECK (initiated_by IN ('buyer','system','ops')),
  reason_code TEXT NOT NULL CHECK (reason_code IN
    ('buyer_request','courtesy','cancel_ge_24h','creator_cancel','hold_expired')),
  reason_note TEXT,
  evidence_ref TEXT,
  approved_by TEXT, executed_by TEXT,
  psp_ref TEXT, failure_reason TEXT,
  idempotency_key TEXT NOT NULL UNIQUE,
  requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  approved_at TIMESTAMPTZ, executed_at TIMESTAMPTZ,
  CONSTRAINT chk_rf_manual CHECK (
    provider NOT IN ('payos','sepay','napas') OR status NOT IN ('executing','reversed')
    OR (evidence_ref IS NOT NULL AND approved_by IS NOT NULL
        AND executed_by IS NOT NULL AND approved_by <> executed_by)),
  CONSTRAINT chk_rf_reversed CHECK (status <> 'reversed' OR (executed_at IS NOT NULL AND psp_ref IS NOT NULL))
);

CREATE UNIQUE INDEX uniq_rf_order ON refunds (order_code);
CREATE INDEX idx_rf_user ON refunds (user_id, requested_at DESC);
CREATE INDEX idx_rf_queue ON refunds (status, requested_at) WHERE status IN ('approved','failed');

CREATE TABLE credit_notes (
  id TEXT PRIMARY KEY,
  refund_id TEXT NOT NULL UNIQUE REFERENCES refunds(id),
  order_code BIGINT NOT NULL REFERENCES orders(order_code),
  user_id TEXT NOT NULL,
  note_ref TEXT NOT NULL UNIQUE,
  seq INT NOT NULL DEFAULT 1 CHECK (seq >= 1),
  amount_vnd INT NOT NULL CHECK (amount_vnd > 0),
  reason_code TEXT NOT NULL,
  issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uniq_cn_ref UNIQUE (order_code, seq)
);
CREATE INDEX idx_cn_order ON credit_notes (order_code);

ALTER TABLE refunds ENABLE ROW LEVEL SECURITY;
CREATE POLICY rf_owner ON refunds FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY rf_ops ON refunds FOR ALL TO app_role
  USING (current_setting('app.context', true) = 'ops')
  WITH CHECK (current_setting('app.context', true) = 'ops');
ALTER TABLE credit_notes ENABLE ROW LEVEL SECURITY;
CREATE POLICY cn_owner ON credit_notes FOR ALL TO app_role
  USING (user_id = current_setting('app.user_id', true))
  WITH CHECK (user_id = current_setting('app.user_id', true));
CREATE POLICY cn_ops ON credit_notes FOR ALL TO app_role
  USING (current_setting('app.context', true) = 'ops')
  WITH CHECK (current_setting('app.context', true) = 'ops');

GRANT SELECT, INSERT, UPDATE ON refunds TO app_role;
GRANT SELECT, INSERT ON credit_notes TO app_role;
```

## Semantics

- Full-only: `amount_vnd` must equal the order's amount — asserted in code before `executing` and again in the final transaction (drill-enforced; `uniq_rf_order` caps to one row per order; `reason_code` vocabulary extends only by add-value migration when 25/39/40 need it).
- Manual-rail invariant is a DB property: `payos|sepay|napas` can never reach `executing`/`reversed` without evidence and two distinct approver/executor identities (`chk_rf_manual`); `system` counts as an identity for auto-approved rows.
- `psp_ref` for manual rails = the reversal transfer reference (same value as `evidence_ref` at reversal; kept separately for finance joins).
- Terminal safety: `reversed|rejected` never reopen; `failed → executing` retries only via guarded updates; concurrent writers lose by rowcount-0.
- Credit notes: one per refund (`UNIQUE refund_id`), `note_ref = CN-<orderCode>-<seq>` with the per-order sequence unique constraint; append-only (no UPDATE/DELETE grant); `seq` stays 1 under full-only but the shape survives future partials.
- Revocation reads (no DDL): the matcher queries `audit_log` for the row's origin (`plan.granted` target) and extensions (`plan.renewed` detail `orderCode`); matching flips nothing new — it calls 36's close-to-free path with `plan.refunded` audit in the same final transaction.
- No new Redis keys; the retry executor reads `idx_rf_queue`.

## Drills

| Drill | Asserts |
|---|---|
| Fresh apply | V5 runs after V4 clean; both tables + 5 indexes + RLS visible; no `orders`/`subscriptions` DDL touched |
| One-per-order | Second insert for the same `order_code` fails; after a `rejected` row, no new row can exist |
| Manual invariant | `sepay` row to `executing` without evidence fails; with evidence but approver = executor fails; `reversed` without `psp_ref`/`executed_at` fails |
| Terminal guards | `reversed → *` and `rejected → *` updates affect zero rows; `failed → executing` succeeds only from `failed` |
| Retry queue | `EXPLAIN` picks `idx_rf_queue` for the executor scan; completed rows leave the partial index |
| Credit-note chain | Duplicate `refund_id` fails; duplicate `(order_code, seq)` fails; no UPDATE grant exists |
| RLS probes | Foreign `app.user_id` reads zero rows; ops context sees the queue; owner cannot read another's refund even by id |
| Single DDL owner | `grep -rn "CREATE TABLE refunds\|CREATE TABLE credit_notes" docs/` matches exactly this file |
| No-default gate | grep over refund code: zero provider defaults/fallback orderings (32's gate extended) |
| Revocation matcher | Origin refund closes the row; extension refund matches via `plan.renewed` `orderCode`; unrelated/older order leaves entitlements untouched |
