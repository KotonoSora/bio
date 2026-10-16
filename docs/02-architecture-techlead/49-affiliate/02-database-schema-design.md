# Affiliate Tracking, Program, and Payouts - Database Schema Design (feature 49) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/49-affiliate/`. GATED: present state is **zero** — no affiliate table exists in any database, and none may be added before the amendment (BR-01, NFR-02). This file designs the future `affiliate_db` migration `V1__affiliate.sql`; the amendment record starts the chain.

## Present state: zero

| Probe | Assertion |
|---|---|
| Schema diff | No `affiliate_clicks`, `order_attributions`, `affiliates`, `commission_tiers`, `commissions`, or `payout_batches` table in any database |
| Migration chain | `affiliate_db` does not exist; no `V*__affiliate*.sql` file exists |
| Source grep | `AttributionResolver`, `CommissionLedger`, `PayoutBatchService` absent from all source and bundles |
| Routing | No `/r/` redirect route exists in the gateway or any service |

## Future Flyway V1 (`V1__affiliate.sql`, at amendment)

```sql
CREATE TABLE affiliates (
  id TEXT PRIMARY KEY,
  user_id TEXT,                      -- null until the affiliate links an account
  display_name TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('applied','active','suspended','terminated')),
  tier TEXT NOT NULL DEFAULT 'default',
  payout_details_enc BYTEA,          -- vault-encrypted; never logged
  tax_fields_complete BOOLEAN NOT NULL DEFAULT false,
  terms_version TEXT NOT NULL,
  terms_accepted_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_aff_status ON affiliates (status);

CREATE TABLE commission_tiers (
  id TEXT PRIMARY KEY,
  tier TEXT NOT NULL,
  rate_bp INT NOT NULL,              -- basis points; finance-owned value
  volume_threshold_vnd BIGINT,       -- null = base tier
  basis_version TEXT NOT NULL,       -- net-of-fees vs gross (Q-04, decided once)
  effective_from TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ct_lookup ON commission_tiers (tier, effective_from DESC);

CREATE TABLE affiliate_clicks (
  id TEXT PRIMARY KEY,
  affiliate_id TEXT NOT NULL REFERENCES affiliates(id),
  landing TEXT NOT NULL,
  clicked_at TIMESTAMPTZ NOT NULL,
  ip_hash TEXT NOT NULL,
  ua_hash TEXT NOT NULL,
  rejected BOOLEAN NOT NULL DEFAULT false,   -- stuffing signals at ingest
  reject_reason TEXT
);
CREATE INDEX idx_ac_affiliate ON affiliate_clicks (affiliate_id, clicked_at DESC);

CREATE TABLE order_attributions (
  order_id BIGINT PRIMARY KEY,       -- billing's order_code; no FK across services
  affiliate_id TEXT NOT NULL REFERENCES affiliates(id),
  source TEXT NOT NULL CHECK (source IN ('link','code')),
  click_id TEXT REFERENCES affiliate_clicks(id),
  clicked_at TIMESTAMPTZ,
  window_days INT NOT NULL,
  rate_snapshot JSONB NOT NULL,      -- {rateBp, tier, basisVersion, windowDays}
  evidence_ref TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('pending','confirmed','reversed')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_oa_affiliate ON order_attributions (affiliate_id, created_at DESC);

CREATE TABLE commissions (
  id TEXT PRIMARY KEY,
  attribution_id TEXT NOT NULL UNIQUE REFERENCES order_attributions(order_id),
  affiliate_id TEXT NOT NULL REFERENCES affiliates(id),
  amount_vnd BIGINT NOT NULL CHECK (amount_vnd >= 0),
  rate_snapshot JSONB NOT NULL,
  rounding_note TEXT,                -- logged per line when rounding occurred
  status TEXT NOT NULL CHECK (status IN ('pending','payable','in_payout','paid','failed','reversed')),
  payout_batch_id TEXT,
  linked_reference TEXT,             -- refund/dispute reference on reversal
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_com_affiliate ON commissions (affiliate_id, status);

CREATE TABLE payout_batches (
  id TEXT PRIMARY KEY,
  period TEXT NOT NULL,              -- e.g. 2026-11
  status TEXT NOT NULL CHECK (status IN ('frozen','instructed','executed','reconciled')),
  executed_at TIMESTAMPTZ,
  rail_refs JSONB NOT NULL DEFAULT '[]',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

## RLS and grants (at amendment)

```sql
-- affiliates: own-row read + own profile update; ops/finance ALL (audited); no cross-affiliate reads
-- affiliate_clicks / order_attributions / commissions: own-affiliate read; service/ops ALL
-- commission_tiers: ops-only writes; service read
-- payout_batches: finance-ops role only
GRANT SELECT, INSERT ON affiliate_clicks, order_attributions TO app_role;   -- append-only evidence
GRANT SELECT, INSERT, UPDATE ON affiliates, commissions, payout_batches TO app_role;
GRANT SELECT, INSERT ON commission_tiers TO app_role;                        -- versioned appends only
```

## Semantics

- Redirect (D-3): `/r/{code}` resolves the affiliate from cache, sets the signed cookie, writes a click row; rejected clicks never become attribution evidence; deep-link targets preserved.
- Resolver (D-2): checkout passes signals; code beats cookie; most recent qualifying click wins; direct → unattributed; the winning id + `evidence_ref` stamp `orders.extras` at creation — no later rewrite path exists.
- Window (BR-04): `clicked_at` + `window_days` (config `affiliate.windowDays`, default 30) checked to the second at order creation; outside → no attribution row.
- Ledger (D-5): `order.granted.v1` → attribution `pending` → `confirmed` past the refund window; refund/dispute outcomes → `reversed` with `linked_reference`; trial/0-VND/cancelled/self never leave `pending`; commission = confirmed net × `rate_snapshot.rateBp` on the snapshot basis version, integer VND, rounding favor-affiliate logged.
- Payout (D-6): monthly freeze selects `payable` lines meeting the config threshold with complete tax fields; `in_payout` during execution; reconcile → `paid`; failures stay `in_payout` + one ops case; re-confirm idempotent; post-payout reversals become debits against future payables (explicit ops action, notified).
- Status ladder (BR-08): suspension freezes new attribution (resolver returns no credit) while balances stay; termination forfeits only rows flagged fraud-tainted with notice + appeal audit.
- Exposure cap (BR-12): period accrual vs config cap; breach pauses new-accrual review; ledger untouched.
- Retention (Q-02): raw click rows follow the analytics raw-event TTL precedent, then aggregate-only; attribution + commission rows follow order retention.

## Drills (at amendment)

| Drill | Asserts |
|---|---|
| Fresh apply | V1 creates six tables + indexes + RLS; append-only grants on clicks/attributions |
| Resolver parity | Fixture signal matrices (code+cookie, two cookies, direct, expired window) match hand-computed outcomes exactly |
| Ledger transitions | Event fixtures drive pending → confirmed → reversed (proportional) with references; trial/0-VND/self stay pending |
| Payout idempotency | Re-confirm no-op; failed line opens one case; debits never silently pull |
| Absence (live) | Until amendment: zero affiliate tables in every schema diff (AC-01) |
