# Sales (Ledger, Analytics, Order Console) - Database Schema Design (feature 51) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/51-sales/`. GATED: present state is **zero** — no sales table exists in any database, and none may be added before the amendment (BR-01, NFR-02). This file designs the future `billing-service` migrations `V9__sales_ledger.sql` + `V10__sales_analytics.sql`; the amendment record starts them. Conventions bind: `TEXT` ids except `order_code` (int64, non-sequential, 33's PK); `user_id TEXT NOT NULL` + index + RLS on facts; VND integers only; 5 s statement timeout; no cross-service FK.

## Present state: zero

| Probe | Assertion |
|---|---|
| Schema diff | No `sales_lines`, `sales_adjustments`, `support_cases`, `approval_requests`, `sales_funnel_daily`, `sales_revenue_daily`, or `sales_close_snapshots` table in any database |
| Migration chain | `billing_db` stops at V8 (46); no `V9__sales*`/`V10__sales*` file exists |
| Source grep | `SalesProjection`, `OrderConsole`, `DualControl`, `SalesAnalytics` absent from all source and bundles |
| Routes | No ops console or sales-analytics route exists in the gateway |

## Future Flyway V9 (`V9__sales_ledger.sql`, at amendment)

```sql
CREATE TABLE sales_lines (
  id BIGSERIAL PRIMARY KEY,
  order_code BIGINT NOT NULL UNIQUE,       -- 33's orders PK; no cross-service FK
  user_id TEXT NOT NULL,
  kind TEXT NOT NULL CHECK (kind IN ('plan','product')),
  plan_term_version TEXT,
  amount_vnd BIGINT NOT NULL CHECK (amount_vnd >= 0),
  provider TEXT NOT NULL,
  provider_txn_ref TEXT NOT NULL,
  fee_vnd BIGINT,                          -- NULL until confirmed (Q-05)
  fee_confirmed_at TIMESTAMPTZ,
  granted_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_sl_user ON sales_lines (user_id, granted_at DESC);
CREATE INDEX idx_sl_provider_day ON sales_lines (provider, granted_at);

CREATE TABLE sales_adjustments (
  id BIGSERIAL PRIMARY KEY,
  sales_line_id BIGINT NOT NULL REFERENCES sales_lines(id),
  user_id TEXT NOT NULL,
  kind TEXT NOT NULL CHECK (kind IN ('refund','partial_refund','dispute_loss','correction')),
  amount_vnd BIGINT NOT NULL CHECK (amount_vnd <= 0),
  reason TEXT NOT NULL,
  actor TEXT NOT NULL,
  linked_reference TEXT,
  at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_sa_line ON sales_adjustments (sales_line_id, at DESC);
CREATE INDEX idx_sa_user ON sales_adjustments (user_id, at DESC);

CREATE TABLE support_cases (
  id TEXT PRIMARY KEY,
  order_code BIGINT,
  reporter TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('open','investigating','waiting_buyer','resolved')),
  notes JSONB NOT NULL DEFAULT '[]',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE approval_requests (
  id TEXT PRIMARY KEY,
  action TEXT NOT NULL,
  payload JSONB NOT NULL,
  proposer TEXT NOT NULL,
  approver TEXT,
  status TEXT NOT NULL CHECK (status IN ('pending','approved','rejected','expired','executed')),
  expires_at TIMESTAMPTZ NOT NULL,          -- 24 h
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

## Future Flyway V10 (`V10__sales_analytics.sql`, at amendment)

```sql
CREATE TABLE sales_funnel_daily (
  day DATE NOT NULL, provider TEXT NOT NULL, plan_or_product TEXT NOT NULL,
  campaign TEXT NOT NULL DEFAULT '', device_class TEXT NOT NULL DEFAULT '',
  started BIGINT NOT NULL DEFAULT 0, handed_off BIGINT NOT NULL DEFAULT 0, paid BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (day, provider, plan_or_product, campaign, device_class)
);

CREATE TABLE sales_revenue_daily (
  day DATE NOT NULL, provider TEXT NOT NULL, kind TEXT NOT NULL,
  attribution TEXT NOT NULL CHECK (attribution IN ('direct','campaign','affiliate')),
  gross_vnd BIGINT NOT NULL DEFAULT 0, adjustments_vnd BIGINT NOT NULL DEFAULT 0, net_vnd BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (day, provider, kind, attribution)
);

CREATE TABLE sales_close_snapshots (
  id TEXT PRIMARY KEY, period TEXT NOT NULL UNIQUE, payload JSONB NOT NULL,
  created_by TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

## RLS and grants (at amendment)

```sql
-- sales_lines / sales_adjustments: owner (user_id) read; ops/finance read-all; service INSERT only
-- support_cases / approval_requests: ops roles only; service ALL
-- aggregates + snapshots: finance/operator roles read; service upsert; snapshots INSERT-only
GRANT SELECT, INSERT ON sales_lines, sales_adjustments TO app_role;   -- append-only; no UPDATE/DELETE
GRANT SELECT, INSERT, UPDATE ON support_cases, approval_requests TO app_role;
GRANT SELECT, INSERT, UPDATE ON sales_funnel_daily, sales_revenue_daily TO app_role;
GRANT SELECT, INSERT ON sales_close_snapshots TO app_role;
```

## Semantics

- Projection (BR-02): every grant handler (33/34/40 flow) inserts its sales line in the same transaction; `order_code UNIQUE` makes a second line impossible; replay emits nothing.
- Netting (BR-03): 38's refund execution and 40's dispute losses append negative adjustments in their own transactions with `linked_reference`; originals never touched; net = lines + adjustments in one query.
- Corrections (BR-05): `kind=correction` appends with reason + actor; no UPDATE/DELETE path exists in code or migrations (grep + migration-review gate).
- Fees (BR-04/Q-05): `fee_vnd` NULL until confirmed, backfilled by settlement data; gross and net-of-known read separately.
- Console (BR-08..BR-13): search across order_code, buyer email/account, provider refs, receipt ids; trail assembled from owning tables; actions delegate to 33/34/36/38/40 endpoints with re-validation at execution; `approval_requests` gates above-floor actions (proposer ≠ approver; 24 h expiry).
- Analytics (BR-14..BR-16): funnel + revenue aggregates upserted idempotently from the fact trail + events on an hourly cadence; every figure decomposes to order ids; close freezes `sales_close_snapshots` (re-runs byte-identical); scorecards + anomaly rules read aggregates only.
- Retention: facts persist as long as orders; raw provider payloads age out elsewhere; snapshots retained with the finance period records.

## Drills (at amendment)

| Drill | Asserts |
|---|---|
| Fresh apply | V9 + V10 create seven tables + indexes + RLS; append-only grants on facts and snapshots |
| Projection | Three-rail grants produce exactly one line each; duplicate webhook replay emits zero lines |
| Netting | Refund/partial/dispute fixtures net exactly; originals byte-identical after all adjustments |
| Immutability | Grep + migration review: zero UPDATE/DELETE on facts; corrections append only |
| Close | Snapshot re-runs byte-identical; post-close adjustments stay in the open period |
| Absence (live) | Until amendment: zero sales tables in every schema diff (AC-01) |
