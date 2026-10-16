# Refunds - Document Context and Scope (feature 38)

Phase 1 BA · Feature 38 of 53 — monetize 7/9: one refund lifecycle every rail shares — who may ask, who must approve, how money moves back, and what happens to entitlements and evidence afterward. Source: `monetize/refunds/refund-processing.md`. Credit-note shape: `monetize/invoices/credit-notes.md` (rendering owned by feature 37). Implemented by `docs/02-architecture-techlead/38-refunds/`.

## Context

Each rail reverses money differently (API, manual transfer, cancel-only). This feature is the router and the rulebook: explicit dispatch on the order's stored `provider`, no default, no cross-rail fallback. Full-only in v1 is already resolved tree-wide (feature 34's Q2: re-purchase is the adjustment path; a `partially_refunded` status would invent finance scope). Execution is ops-evidenced; buyers request and track. The refunded order is terminal — re-grants are refused by construction, and entitlement revocation reuses feature 36's lossless downgrade machinery.

## Objectives

| ID | Objective |
|---|---|
| OBJ-01 | One refund lifecycle (request → approve → execute → reversed) audited identically on all eight rails |
| OBJ-02 | Explicit provider routing from the order's stored `provider`; unknown codes rejected; no fallback exists (grep gate) |
| OBJ-03 | Full refunds only: amount to the dong equals the order amount; one refund row per order |
| OBJ-04 | Atomic finality: the `paid → refunded` transition, refund row, credit note, revocation, and event marker commit together |
| OBJ-05 | Manual bank reversals carry evidence (reversal transfer reference) and two-person approval before any ledger transition |
| OBJ-06 | Entitlement revocation is lossless: plan rows downgrade and close; product claims invalidate via the refund event; nothing is deleted |
| OBJ-07 | Buyers can request and track; sellers never self-execute (ops role only); system-class reasons (booking/tip policies) auto-approve |
| OBJ-08 | Evidence stays append-only: credit notes link to originals; receipts render the chain; re-download always matches the ledger |

## Scope

In scope (SC-01..SC-08): refund request + tracking, ops approve/reject/execute, the eight-rail strategy dispatch, manual evidence + two-person checks, the refunds + credit-notes schema (V5), plan revocation via feature 36 paths, the `order.refunded.v1` emission, ops queue reads, retry/backoff + alerting.

Out of scope: partial refunds (deferred; recorded in Q-01); buyer self-service execution (deferred; Q-02); rail member PSR mechanics and dispute contest detail (40's member conformance consumes this contract); receipt/credit-note rendering (37 — its AC-13 activates when this ships); coupon unwinding (39 recorded seam); seller-payout side of reversals (26/23 stay consumer-side); P4/P5.

## Personas and permissions

| Role | CRUD Permissions | Description |
|---|---|---|
| Buyer | R: own refund requests + status; C: open a request | Track request → approved → reversed without support ping-pong |
| Support/ops (restricted role) | R: queue; C: approve, reject, execute | Executes reversals with evidence on every rail |
| Finance | R: refund + credit-note rows | Reconciles PSP references against one refunds ledger |
| System (booking/tip policies, 25) | C: system-class requests | Auto-approve policy-driven refunds; API rails auto-execute, manual rails queue for ops |

## Open-question resolutions

| # | Question (source) | Resolution |
|---|---|---|
| Q-01 | Partial refunds in v1 (Q1) | Full-only: one refund row per order (`uniq_rf_order`); adjustment cases go re-purchase; partials + stacking policy deferred (recorded; credit-note `seq` column keeps the door open) |
| Q-02 | Buyer self-service execution (Q2) | Request-only in v1; execution is ops-role; windowed auto-approve deferred (recorded). System-class reasons auto-approve where policy demands (Q-04) |
| Q-03 | Bank-rail evidence + SLA (Q3) | Evidence = reversal transfer reference recorded before any transition + two-person (approver ≠ executor); manual execution SLA ≤ 3 business days after approval; API rails first attempt immediate, retries bounded (Q-05) |
| Q-04 | System-class refunds | Internal route (service JWT): allowlisted reasons `cancel_ge_24h|creator_cancel|hold_expired` (25's booking policies) enter `approved` directly; API rails auto-execute, manual rails join the ops queue |
| Q-05 | Retry posture | ≤ 5 attempts over ≤ 24 h with backoff, then `failed` + alert; ops re-execute is idempotent; PSP-level idempotency keys where the rail supports them |
| Q-06 | Entitlement revocation on plan refunds | Revoke the current pro row when the refunded order is its origin or a recorded extension (36's `plan.granted`/`plan.renewed` audits match); close `expired` + open free floor (36's path) + audit `plan.refunded`; edge-case compensation via 36's ops period-fix; product claims invalidate via the event |
| Q-07 | Tips | `tip` kind orders refuse refunds with `tip_final` except provider-initiated disputes (40's dispute path) |
| Q-08 | Disputes | Disputes are not refunds: `disputed` orders follow 40's dispute path; a resolved dispute either restores `paid` or lands `chargeback_reversed` (terminal), never through this router |

## Constraints carried

Routing reads the stored `provider` only (32's no-default gate); every refund row carries `user_id` + RLS; VND integers only; no PAN/card/bank material in rows, logs, receipts, or notes; refund rows and credit notes are append-only (no DELETE grant); the event shape is frozen by 32's catalog (`{orderId, refundId, refundedAt}`).
