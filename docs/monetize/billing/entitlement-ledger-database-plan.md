# Entitlement Ledger Database Plan (subscriptions — table written once)

PLAN ref: P3-3 (single plan-truth row per user; free-from-signup; grant mapping; AI-credit period keying) + S-1/S-6. BA source: `docs/monetize/billing/entitlement-ledger.md`. Consumed by the ledger backend plan (grant writes + plan reads; splits next to this file) and every rail grant handler (all eight rails write here post-transition — same row shape, no per-rail variants). Period movement lives in the lifecycle plans (same table, never redefined); receipts read it (never write it). No migration — the SQL below is complete, applied once.

## Purpose

Define the one entitlement record all provider paths write and all feature gates read: current plan, period bounds, status, and the lifecycle columns the expiry job needs. Exactly one `CREATE TABLE`; no ALTERs, no versions. Gates never branch on row absence (free-from-signup is structural), never read `orders`/cookies/client claims for plan truth.

## Users

- Backend developer: runs this script once; grant handlers close/open rows through the ledger writer only.
- Gated-feature code (domains, realtime, premium templates, AI): reads effective plan from the single current row.
- Support/ops: resolves "I paid but I'm still free" from full per-user history.

## Functional requirements

1. Conventions (repo-wide): `TEXT` ids, `TIMESTAMPTZ`, `user_id TEXT NOT NULL` + index + RLS plus app scoping, migration role owns DDL, `app_role` grants, schema `billing` (money domain — `orders` joins locally in-schema when the checkout-session database plan lands).
2. Table `billing.subscriptions` — one row per user per plan era. `id` (ulid PK); `user_id`; `plan` (`free|pro` — CHECK alphabet; new plans are spec amendments, not values); `status` (`active|grace|cancelled|expired` — CHECK); `period_start/period_end TIMESTAMPTZ` (grant-time anchored); `provider` (settling rail, traceability); `order_id` (FK → `billing.orders(orderCode)` — cross-table in-schema join, defined once the session DDL lands; until then the column exists with the FK named-not-enforced? No — exact-once honesty: the FK clause ships in this script against the orders table created in the same baseline run (order the scripts: orders first). Cross-schema avoided by co-locating money tables in `billing`.); `cancelled_at?`; `reminded_7d/reminded_1d BOOL DEFAULT FALSE` (reminder state owned by the lifecycle slice — columns live HERE so the expiry job never alters the table to do its job); `created_at`.
3. Exactly-one-current rule (structural, not conventional): partial unique `uniq_current_plan(user_id) WHERE status IN ('active','grace')` — two concurrent grants cannot both go current (second fails closed into retry-against-history, never split-brain). History rows (`cancelled|expired|superseded`… note: supersede modeled as `cancelled` with reason? BA says "closes any current row (status → superseded)". Hmm — BA FR4 says status → superseded, but FR1 alphabet lacks it. Decide here: superseded rows carry `status='cancelled'` + `cancelled_at` + audit reason `superseded-by-<orderCode>` (no fifth status — alphabet stays four, history stays queryable; the reason lives in audit where reasons belong). Flagged as decided-here; BA Q-neutral.
4. Free-from-signup (structural): signup hook inserts `free/active` row with null `period_end` (free never expires — NULL means unbounded, never magic-date). Gates read the current row or fail closed loudly (row absence is a signup-hook bug, surfaced as 500 + alert, never silent free-pass — fail-closed beats fail-open on entitlements).
5. AI-credit keying: `ai_generations` rows (storage owned by the plans slice — subscription-plans database plan, lifecycle queue; NOT here) key off `user_id` + the current row's period window; mid-period plan changes never retroactively alter consumed credits (computation rule owned there, period source owned here).
6. KV fast-path (optional, mirrors AI pattern): effective plan cached per user short TTL for hot gates; PG row reconciled truth on every dispute (cache never cited in billing arguments — support reads PG).
7. Indexes: `idx_subs_user(user_id, status)` (current-row lookup + history); partial unique (rule 3); `idx_subs_period(period_end)` (expiry-job scan — bounded range scan, never full-table).

## Data / API touches

- Full table, applied once (same baseline run, after `orders` — FK target first):
```sql
CREATE SCHEMA IF NOT EXISTS billing;
CREATE TABLE billing.subscriptions (
  id TEXT PRIMARY KEY, user_id TEXT NOT NULL, plan TEXT NOT NULL,
  status TEXT NOT NULL, period_start TIMESTAMPTZ NOT NULL, period_end TIMESTAMPTZ,
  provider TEXT, order_id BIGINT REFERENCES billing.orders(orderCode),
  cancelled_at TIMESTAMPTZ, reminded_7d BOOLEAN NOT NULL DEFAULT FALSE, reminded_1d BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_sub_plan CHECK (plan IN ('free','pro')),
  CONSTRAINT chk_sub_status CHECK (status IN ('active','grace','cancelled','expired'))
);
CREATE UNIQUE INDEX uniq_current_plan ON billing.subscriptions(user_id) WHERE status IN ('active','grace');
CREATE INDEX idx_subs_user ON billing.subscriptions(user_id, status);
CREATE INDEX idx_subs_period ON billing.subscriptions(period_end) WHERE status IN ('active','grace');
ALTER TABLE billing.subscriptions ENABLE ROW LEVEL SECURITY;
CREATE POLICY s_owner ON billing.subscriptions FOR ALL TO app_role USING (user_id = current_setting('app.user_id', true)) WITH CHECK (user_id = current_setting('app.user_id', true));
GRANT SELECT, INSERT, UPDATE, DELETE ON billing.subscriptions TO app_role;
```
- Consumed by: ledger backend plan (only writer besides the lifecycle job); every gate (read-only current-row reads); receipts (plan terms display).

## Acceptance criteria

- Fresh-apply drill: script runs clean after `orders`; `\d` shows FK, CHECKs, partial unique, indexes, policy.
- Single-current drill: concurrent double-grant → one current row (second fails closed), full history, `GET /api/v1/plan` correct within SLA.
- Product-order drill: zero `subscriptions` writes; plan reads unchanged.
- Free drill: new signup holds `free/active` (hook test); no gate path handles missing rows except the loud 500 + alert.
- Supersede drill: second paid grant cancels-with-reason first (audit shows `superseded-by-*`), history complete.
- RLS drill: foreign `app.user_id` reads zero rows.
- No-duplication drill: `grep -rn "CREATE TABLE billing.subscriptions" docs/` returns exactly this file.

## Out of scope

- Grant/write/read flows (backend plan); plan screens (frontend plan); periods/renewal/grace/downgrade execution (lifecycle plans); receipts/history (invoices-receipts plans); rail grant mechanics (rail slices); plan matrix/AI caps (plans slices); agency seats; P4/P5.

## Open questions

- Q1: Monthly-only vs monthly + yearly terms (BA Q1 — changes `period_end` math + refund exposure)?
- Q2: Multi-seat/org-owned rows later, or per-user + org links forever (BA Q2)?
