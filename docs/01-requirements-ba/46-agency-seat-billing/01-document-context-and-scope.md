# Agency Seat Billing - Document Context and Scope (feature 46)

Phase 1 BA · Feature 46 of 53 — agency 2/3. Charges agencies for exactly the team they field: base platform fee + N × seat price (VND integers), clients free, every charge tracing to a dated seat snapshot. Sources: `agency/billing/seat-billing.md`, `monetize/organizations/agency-skeleton.md`. Implemented by `docs/02-architecture-techlead/46-agency-seat-billing/`. Service: billing-service (port 8088, `billing_db`, Redis db 10) — money moves only through 32–40's rails; this feature adds no checkout, webhook, or ledger of its own.

## Context

Roster truth lives in 45 (account-service): billable seats = active owner + managers + members; clients free; invited unbilled until acceptance; suspended unbilled from the suspension date. This feature consumes 45's internal seat counts (daily job), keeps the legacy "no new money tables" posture — V8 adds only `seat_history` (the billable record) and `org_subscriptions` (the org↔subscription mapping + billing contact); amounts, orders, invoices, and ledger rows stay in the existing billing minors. The plan domain evolves explicitly: `subscriptions.plan` gains `agency` via a versioned ALTER (the same evolution style 36 used for statuses). The agency subscription is the owner's (or billing contact's) own subscription row — one current subscription per user still holds, and the agency plan is a superset of pro for that user.

## Objectives

| ID | Objective |
|---|---|
| OBJ-01 | Closed seat model: active owner + managers + members billable; clients free (unlimited links); invited unbilled; suspended unbilled from the date |
| OBJ-02 | Day-weighted charging with dated snapshot evidence; invoice lines cite snapshots; closed-period invoices immutable |
| OBJ-03 | Owner-pays posture: one billing contact per org (default owner), dual-confirmation transfer, managers counts-only |
| OBJ-04 | Enforcement: uncapped pay-per-seat; lapse grace freezes staff powers (invites + Act-as-user) while client pages stay live |
| OBJ-05 | Invoicing through 37's receipt pipeline; every line reaches its snapshots in one hop from the overview screen |
| OBJ-06 | No-hostage: cancel, lapse, or org delete never deletes or privatizes client profiles |

## Scope

In scope (SC-01..SC-06): `seat_history` + `org_subscriptions` + plan-domain ALTER (V8); daily snapshot job from 45's seat counts; overview/transfer/cancel/invoice endpoints; renewal line contribution + day-weighted true-up; reconciliation job + ops surface; entitlement consumption for staff gates (45 cross-edit); cancel-flow no-hostage copy.

Out of scope: roster, roles, invites, linking, act-as mechanics (45); reporting (47); plan matrix values (35's catalog — absent values refuse, never invented); checkout, webhook verify, grants, dunning, receipts internals (32–38); creator-individual subscriptions (orthogonal — a staffer's personal plan never substitutes for a seat).

## Personas and permissions

| Role | CRUD Permissions | Description |
|---|---|---|
| Owner / billing contact | R overview + snapshots + invoices; U subscribe/cancel/transfer | Sees seat count, unit price, projected total; pays; reads invoices |
| Manager/member | R counts-only (who holds seats, never amounts) | Consumes a seat; sees plan status, never billing instruments |
| Client | none | Free; profiles never hostage to agency billing |
| Finance/support | R reconciliation + evidence (scoped) | Resolves "why was I charged X?" from seat-history alone |

## Open-question resolutions

| # | Question (source) | Resolution |
|---|---|---|
| Q-01 | Seat accounting method (seat-billing Q1a) | Day-weighted: daily snapshots; cycle charge = base + `round(Σ seat-days × seatPrice ÷ cycleDays)` in VND integers (round half-up); prepaid at renewal; the day-weighted true-up settles on the next invoice (positive as a line, negative as account credit) |
| Q-02 | Removed-seat credit (seat-billing Q1b) | Account credit applied to the next renewal — self-evident, no surprise; cash refund on request through 38's machinery; no refund fees invented (finance-owned) |
| Q-03 | Seat caps per tier (seat-billing Q2) | Uncapped pay-per-seat in v1 — no ladder invented; a reserved cap flag may gate invites with the add-seat path (off by default) |
| Q-04 | Billing contact (seat-billing FR-3) | Defaults to the owner; transferable to any active staff member with dual confirmation (outgoing + incoming) and audit; single-sided attempts leave billing untouched |
| Q-05 | Yearly terms | Only when the catalog defines a yearly term — no discount invented; monthly is the v1 default |
| Q-06 | Skeleton seat intent (skeleton Q1) | Per-seat + base fee, resolved here; the skeleton's tables are superseded by V8's shapes (no behavior shipped in P3, verified by the E4 gate audit) |

## Constraints carried

VND integers only (no fractional amounts anywhere); provider explicit, no default (eight equal rails via 33's front door); idempotent grants; values from the versioned catalog (`agencyBaseVnd`, `agencySeatVnd`; absent → `catalog_incomplete` refusal); closed-period evidence immutable; zero token material in logs or payloads.
