# Agency Seat Billing - Architecture and Tech Stack (feature 46)

Phase 2 TL · Service: `billing-service` (port 8088, `billing_db`, Redis db 10). Implements `docs/01-requirements-ba/46-agency-seat-billing/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`. Money posture: rides 32–40's machinery end to end — no new checkout, webhook, ledger, or money table.

## Service boundary

| Owns | Consumes (contract -> owner) |
|---|---|
| `seat_history` + `org_subscriptions` + `contact_transfers` (V8) + the plan-domain ALTER; snapshot job; seat charge math; agency subscription orchestration; billing-contact transfer; reconciliation; agency entitlement read | Seat counts -> 45's `GET /internal/v1/agency/seats`; checkout + grants -> 33/34/35; renewal + dunning -> 36; invoices/receipts -> 37; refunds -> 38; credit application -> 39's credit-source mechanism; catalog values -> 35's `billing.catalog`; notification enqueue -> 32's contract |

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | Module `com.app.billing.agency`: `SeatSnapshotJob`, `SeatChargeMath`, `AgencySubscriptionService`, `ContactTransferService`, `ReconcileJob`, `AgencyReads` | One money-math owner; reads and jobs share it |
| D-2 | `V8__agency_seat_billing.sql`: `seat_history`, `org_subscriptions`, `contact_transfers` + indexes + RLS + the plan-domain ALTER (`subscriptions.plan` gains `agency`; agency requires `period_end` + `provider`) | Explicit versioned evolution, same style as 36's status ALTERs; mapping tables are not money tables (no amounts, no ledger) |
| D-3 | Snapshot job: daily, pages 45's seats route, upserts per `(org_id, day)` with `DO NOTHING` (idempotent; re-runs safe); misses retried and alerted after 2 misses; snapshots are never fabricated | BR-01/NFR-01; the bill cites only recorded truth |
| D-4 | `SeatChargeMath.cycleCharge(baseVnd, seatPriceVnd, seatDays, cycleDays) = baseVnd + floor((seatDays × seatPriceVnd × 2 + cycleDays) ÷ (2 × cycleDays))` (half-up, VND integers); one implementation used by renewal, true-up, and overview projection | BR-02/NFR-03; grep gate: a second proration implementation is a CI failure |
| D-5 | Renewal settlement: the renewal order (33/34) metadata carries `{seatsCharged, seatDays, catalogVersion, trueUpVnd}`; 37's invoice lines render base + seat line + true-up (positive) or account credit (negative) from it; no new money tables exist | BR-02/BR-03; invoices stay self-contained evidence |
| D-6 | Contact transfer: `contact_transfers` row with dual confirmations (outgoing + incoming), 7-day expiry, one pending per org; effective only on both; `org_subscriptions.billing_user_id` flips with audit | BR-06/Q-04; single-sided attempts are inert |
| D-7 | Entitlement read for 45: `GET /internal/v1/billing/agency/entitlement?orgId=` returns `{active, state}`; 45 consumes it at staff invites and Act-as-user starts; lapse freezes staff powers, never client surfaces | BR-07; one entitlement truth (35's snapshot mechanics) |
| D-8 | Cancel/no-hostage: cancellation rides 36's lifecycle (cycle-end effect); entitlement flips inactive; 45 severs tooling; client profiles/pages are untouched by construction; cancel-flow copy states it | BR-07/BR-12; the invariant is enforced by 45's ownership model, not by billing |
| D-9 | Events: `agency.subscription_changed.v1` `{orgId, subscriptionId, state}` post-commit; consumers: 45 (entitlement cache refresh), notification | Additive; at-least-once, idempotent consumers |
| D-10 | Config: `billing.catalog` gains `agencyBaseVnd` + `agencySeatVnd` (values catalog-owned — never invented here); absent values refuse subscribe with `catalog_incomplete`; the catalog version used is stamped on every line | Guardrail: no invented finance values; price integrity |

## Cross-edits applied with this feature

| Target | Edit |
|---|---|
| `docs/02-architecture-techlead/32-monetize-overview/03-api-contracts-and-payloads.md` | registry rows for `/api/v1/agency/billing/*` + the internal entitlement read; V8 chain row in 32 TL02 |
| 45 | TL 03 cites the exact entitlement route at invite + act-as gates; seat-route DTO drops billing-contact (billing owns contact post-transfer) |
| `docs/02-architecture-techlead/00-platform-baseline/01-architecture-and-service-topology.md` | billing-service feature list gains 46 |
| `MIGRATION.md` | rows 45–47 + batch line at the batch tail |
