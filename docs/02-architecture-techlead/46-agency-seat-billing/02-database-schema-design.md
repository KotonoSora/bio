# Agency Seat Billing - Database Schema Design (feature 46)

Phase 2 TL · `billing_db` migration `V8__agency_seat_billing.sql` (chain: V1 orders … V7 provider rails). BA sources: `docs/01-requirements-ba/46-agency-seat-billing/`.

## Flyway V8 (`V8__agency_seat_billing.sql`)

```sql
-- Plan domain evolution (V2 constraint; same style as 36's status ALTERs)
ALTER TABLE subscriptions DROP CONSTRAINT subscriptions_plan_check;
ALTER TABLE subscriptions ADD CONSTRAINT chk_sub_plan CHECK (plan IN ('free','pro','agency'));
ALTER TABLE subscriptions ADD CONSTRAINT chk_sub_agency CHECK (plan <> 'agency' OR (
  period_end IS NOT NULL AND provider IS NOT NULL));

CREATE TABLE org_subscriptions (
  id TEXT PRIMARY KEY,
  org_id TEXT NOT NULL,
  billing_user_id TEXT NOT NULL,
  subscription_id TEXT NOT NULL REFERENCES subscriptions(id),
  state TEXT NOT NULL CHECK (state IN ('active','past_due','lapsed','cancelled')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uniq_os_current ON org_subscriptions (org_id)
  WHERE state IN ('active','past_due');
CREATE INDEX idx_os_user ON org_subscriptions (billing_user_id, state);

CREATE TABLE seat_history (
  id TEXT PRIMARY KEY,
  org_id TEXT NOT NULL,
  snapshot_day DATE NOT NULL,
  seats INT NOT NULL CHECK (seats >= 0),
  by_role JSONB NOT NULL,
  source TEXT NOT NULL DEFAULT 'roster' CHECK (source IN ('roster','reconciled')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uniq_sh UNIQUE (org_id, snapshot_day)
);
CREATE INDEX idx_sh_org ON seat_history (org_id, snapshot_day DESC);

CREATE TABLE contact_transfers (
  id TEXT PRIMARY KEY,
  org_id TEXT NOT NULL,
  from_user_id TEXT NOT NULL,
  to_user_id TEXT NOT NULL,
  outgoing_at TIMESTAMPTZ,
  incoming_at TIMESTAMPTZ,
  state TEXT NOT NULL DEFAULT 'pending'
    CHECK (state IN ('pending','completed','expired','cancelled')),
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_ct_confirmed CHECK (state <> 'completed'
    OR (outgoing_at IS NOT NULL AND incoming_at IS NOT NULL))
);
CREATE UNIQUE INDEX uniq_ct_pending ON contact_transfers (org_id) WHERE state = 'pending';
```

## RLS and grants

```sql
ALTER TABLE org_subscriptions ENABLE ROW LEVEL SECURITY;
CREATE POLICY os_contact ON org_subscriptions FOR SELECT TO app_role
  USING (billing_user_id = current_setting('app.user_id', true));
CREATE POLICY os_svc ON org_subscriptions FOR ALL TO app_role
  USING (current_setting('app.context', true) IN ('service','ops'))
  WITH CHECK (current_setting('app.context', true) IN ('service','ops'));
-- seat_history: read via owning org_subscriptions.billing_user_id EXISTS (or service/ops);
-- contact_transfers: read for from/to users; writes service/ops
GRANT SELECT, INSERT, UPDATE ON org_subscriptions, seat_history, contact_transfers TO app_role;
-- seat_history has no DELETE grant (evidence); closed-day rows have no UPDATE path (see semantics)
```

## Semantics

- Snapshot (D-3): job pages 45's seats route (org list + counts + by-role) → `INSERT ... ON CONFLICT (org_id, snapshot_day) DO NOTHING`; re-runs and retries are idempotent; a miss is alerted after 2 consecutive failures and backfilled on recovery only if the day is still open (same-day) — past days are never fabricated.
- Charge math (D-4): `cycleCharge(baseVnd, seatPriceVnd, seatDays, cycleDays) = baseVnd + floor((seatDays × seatPriceVnd × 2 + cycleDays) ÷ (2 × cycleDays))` — half-up on VND integers; used by renewal, true-up, and overview projection; fixture parity is a drill.
- Renewal + true-up (D-5): at renewal the service computes `seatDays` over the closing cycle from `seat_history`, the closing cycle's `cycleCharge`, and `trueUpVnd = closingCharge − base − (seatsCharged_prev × seatPrice)`; the renewal order's metadata (33/34's record) carries `{seatsCharged, seatDays, catalogVersion, trueUpVnd}`; 37 renders the lines; negative `trueUpVnd` applies as account credit through 39's credit-source mechanism (`source='credit'`); no amounts are stored outside the existing money tables.
- Transfer (D-6): initiate writes a `pending` row (one per org); outgoing/incoming confirmations stamp columns; both → `completed` + `org_subscriptions.billing_user_id` flips + audit; expiry job flips stale pending rows at 7 days; single-sided rows are inert.
- Entitlement (D-7): read joins `org_subscriptions` + `subscriptions.status` (+ 36's grace semantics): `active` while subscription ∈ active/grace; `{active:false}` on lapse/cancel-end; 45 consumes it at invite + act-as gates; client surfaces never consult it.
- Reconciliation (D-8): daily job compares the day's snapshot vs a fresh seats-route read; mismatch → alert + ops correction that may touch only the current open day (`source='reconciled'`, reason + audit); closed days have no UPDATE path in code.
- Cancel/org-delete: state mirrors 36's lifecycle transitions into `org_subscriptions.state`; final counts recorded; snapshots retained for audit; client-side impact is zero by construction (45 owns the invariant).
- No new Redis keys beyond `ag:rl:*`; zero instrument/token material in any row, payload, or log.

## Drills

| Drill | Asserts |
|---|---|
| Fresh apply | V8 after V7; three tables + indexes + RLS; plan CHECK now admits agency with the paid-evidence constraint |
| Snapshot idempotence | Re-run + retry produce one row per (org, day); misses alert; no fabricated days exist |
| Math parity | Fixture seat-day matrices match hand-computed `cycleCharge`/true-up to the VND; grep: one implementation |
| True-up settlement | Positive delta lands on the next order metadata; negative becomes credit via 39; closed periods untouched |
| Transfer lifecycle | Dual confirm switches contact + audit; single-sided inert; expiry at 7 days; one pending per org |
| Entitlement read | active/grace → true; lapse/cancel-end → false; 45's gates refuse on false |
| Immutability | Closed-day rows have no UPDATE/DELETE path (grants + code grep); corrections touch open day only |
| RLS probes | Non-contact reads zero rows; ops context reads for reconciliation; managers/clients get no route |
