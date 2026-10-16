# Sales (Ledger, Analytics, Order Console) - Document Context and Scope (feature 51) - GATED

Phase 1 BA · Feature 51 of 53 — gated backlog 4/6. GATED: design-complete only — no table, route, job, or UI string ships until a spec amendment approves it (BR-01, AC-01). Sources: `sales/sales-ledger.md`, `sales/sales-analytics.md`, `sales/order-management.md`. Implemented (future) by `docs/02-architecture-techlead/51-sales/`.

## Context

Three coupled scopes: the single append-only record of what was sold and what happened to the money afterward (sales facts projected once per financial event, never edited); how the business reads its own sales motion (checkout funnels, revenue cuts, provider health — derived from those facts, never from per-provider dashboards that disagree by definition); and the unified ops console where support and ops find any order, see its complete money trail, and take every legal action with one audit trail instead of eight provider consoles. The future home is `billing-service` (extensions `V9__sales_ledger.sql` + `V10__sales_analytics.sql`) — the ledger must be projected in the same transaction as the grant, and decomposition-exactness demands one database; the console orchestrates across 49/50 payout reads via scoped internal reads. Nothing here is built while gated.

## Objectives

| ID | Objective |
|---|---|
| OBJ-01 | One immutable revenue fact per grant (same-transaction projection; duplicate grants emit nothing) |
| OBJ-02 | Netting by appended adjustments only — originals byte-identical forever; net revenue in one query |
| OBJ-03 | Universal lookup: any order found from any identifier with one assembled trail; nothing requires knowing the provider first |
| OBJ-04 | Every action role-gated, precondition-checked, audited; money-moving actions dual-controlled above the floor; stale views fail closed |
| OBJ-05 | Business analytics that decompose exactly to ledger lines; finance close is a frozen, re-runnable snapshot |
| OBJ-06 | Provider health with config thresholds that page ops and feed kill-switch/provider-ordering decisions |

## Scope

In scope (at amendment, SC-01..SC-06): `sales_lines` + `sales_adjustments`; support cases + dual-control approvals; the unified console (lookup, action catalog, plain-language states, dry-runs); funnel + revenue aggregates; provider scorecards + refund/dispute watch + alerting.

Out of scope: order lifecycle and transitions (33/34 own them — the console orchestrates, never reimplements); buyer receipts (37); affiliate/creator payout ledgers (49/50 — separate liabilities, derived independently); creator page analytics (12/31); accounting/revenue-recognition reports; automated dunning (36).

## Personas and permissions

| Role | CRUD Permissions | Description |
|---|---|---|
| Finance/operator | R all sales facts + analytics; C corrections + close snapshots | Reads net revenue by day/provider/plan/product/campaign — one query, no spreadsheets |
| Support agent | R console (assembled trails); C support-tier actions + cases | Finds orders, explains states in plain language, triggers buyer-safe actions |
| Ops/finance operator | C ops-tier actions with dual control where defined | Executes cancel/link/refund/subscription-fix/campaign-pause |
| Auditor | R append-only facts + webhook causality | Replays platform revenue from facts plus the events that caused them |
| Buyer (indirect) | R own case status only | Gets faster, consistent answers; never reads the ledger |

## Open-question resolutions

| # | Question (source) | Resolution |
|---|---|---|
| Q-01 | Dual-control threshold (order Q1) | Config-owned (`sales.dualControlFloorVnd`); launch value finance-set; mechanism fixed here: maker-checker, proposer ≠ approver, pending approvals expire after 24 h and cannot execute |
| Q-02 | Trail latency (order Q2) | Assembled trail p95 ≤ 500 ms; heavy exports/aggregations stay in analytics endpoints |
| Q-03 | Scorecard thresholds (analytics Q1) | Config-owned (`sales.scorecards`); launch floors/ceilings set from rail history at the amendment; alert rules versioned in config |
| Q-04 | Dashboard freshness (analytics Q2) | Hourly aggregates (≤ 1 h lag) confirmed; near-real-time deferred |
| Q-05 | Provider-fee capture (ledger Q1) | Recorded, not decided here: fee fields NULL until confirmed, then backfilled by settlement data where available; finance reads gross and net-of-known separately — never a blended fiction |
| Q-06 | Correction approval (ledger Q2) | Single finance role below the dual-control floor; at or above it, maker-checker applies (same floor as Q-01) |

## Constraints carried

Append-only everywhere (INSERT + SELECT only on facts; no UPDATE/DELETE path in code or migrations — grep + migration-review gate); no buyer PII in analytics beyond what ops roles already see; affiliate/creator rollups stay on their own scoped endpoints (no cross-earner leakage through shared dashboards); integer VND as charged; canonical statuses owned by 34 (console displays, never redefines); the console adds no financial state of its own.
