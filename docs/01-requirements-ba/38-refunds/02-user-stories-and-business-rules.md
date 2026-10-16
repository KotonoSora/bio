# Refunds - User Stories and Business Rules (feature 38)

Phase 1 BA · Enforced by `docs/02-architecture-techlead/38-refunds/`. Scope: `docs/01-requirements-ba/38-refunds/01-document-context-and-scope.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Buyer | request a refund for a paid order and track every state | I stop chasing status through support | High |
| US-02 | Support/ops | approve, reject, and execute reversals with evidence on any rail | no path silently defaults to another rail's behavior | High |
| US-03 | Finance | reconcile PSP references against one refunds ledger and credit notes | reversals stay audited and add up | High |
| US-04 | System (booking policies) | auto-refund policy-entitled cases without human latency | a creator cancel or expired hold never strands money | High |
| US-05 | Reviewer | prove idempotency: retries and races resolve to one outcome | the ledger can be trusted under stress | High |
| US-06 | Refunded subscriber | see the revocation honestly: what stops, what stays, how to re-buy | losing access is predictable, never silent | Medium |

## Business rules

- **BR-01 Eligibility.** Only `paid` orders may be refunded; `pending|authorizing|failed|cancelled|expired` refuse with `refund_ineligible` naming the terminal reason; `disputed`/`chargeback_reversed` route to the dispute path, never this router. `tip` kind orders refuse with `tip_final` (Q-07).
- **BR-02 Full-only.** `amount_vnd` equals the order's `amount_vnd` to the dong; one refund row per order ever (`uniq_rf_order`); no partial sums exist (Q-01; credit-note `seq` stays 1).
- **BR-03 Request flow.** Owner + CSRF + Turnstile + `Idempotency-Key`; body carries a reason; the row lands `requested` with `initiated_by = buyer`. A second request while one is open returns the existing row (200); after a terminal row exists, a new request returns `refund_conflict` (ops handles exceptions, never silent second rows).
- **BR-04 Approval.** Ops role only; `requested → approved` or `requested → rejected` (terminal, with reason); both audited with actor. Approval never touches the order.
- **BR-05 System-class entry.** Feature 25 (and future policy owners) call the internal route with an allowlisted reason (`cancel_ge_24h|creator_cancel|hold_expired`) + source reference + idempotency key; rows enter `approved` directly with `initiated_by = system` and `approved_by = system`.
- **BR-06 Explicit dispatch.** Execution reads the order's stored `provider` and calls that member's strategy: `vnpay`/`zalopay`/`stripe` = refund APIs; `googlepay`/`applepay` = underlying PSP path; `payos`/`sepay`/`napas` = manual bank reversal. Unknown codes reject; no default and no cross-rail fallback exists (grep gate).
- **BR-07 Manual evidence + two-person.** `payos|sepay|napas`: `evidence_ref` (reversal transfer reference) is recorded before the transition, and executor ≠ approver (for system-approved rows the approver is `system`). Missing evidence → 422; same person → 403.
- **BR-08 Execution order.** `approved → executing` commits first; the PSP/manual step runs outside any transaction; the final transaction is atomic: order `paid → refunded` through feature 34's guard, refund row `executing → reversed` + `psp_ref` first-write, credit note insert, entitlement revocation, `refund.emitted` marker — all or nothing.
- **BR-09 Amount check.** The final transaction re-asserts `refund.amount_vnd == order.amount_vnd`; mismatch = no transition + alert.
- **BR-10 Retry/backoff.** First attempt immediate; failures land `failed` + alert and retry with backoff (≤ 5 attempts, ≤ 24 h), then escalate; ops re-execute is status-guarded and idempotent. Per-rail PSP idempotency keys are used where the rail supports them (stripe = refund id; query-before-retry for rails without keys).
- **BR-11 Revocation — plans.** When the refunded order is the current row's origin or a recorded extension (audited `plan.renewed` orderCode match), the row closes `expired` + the free floor opens + `plan.refunded` audit — feature 36's lossless path; otherwise no entitlement change (older superseded grants). Compensation edge cases go through 36's ops period-fix (Q-06).
- **BR-12 Revocation — products.** `order.refunded.v1` publishes post-commit (frozen shape); 23/24/25 invalidate claims idempotently (pre-print cancel, enrollment + certificate revoke with `courtesy_until`, bookings/tip states). Consumers' behavior is theirs; this feature guarantees at-least-once + marker + reconciliation.
- **BR-13 Credit notes.** On reversal, one note issues inside the final transaction: `note_ref = CN-<orderCode>-<seq>`, linked to the original receipt; append-only; rendered by 37; email enqueued post-commit (`billing.credit_note`, key `cn:{refundId}`).
- **BR-14 Re-grant refused.** A `refunded` order is terminal; confirmations for it are refused by 34's guard; repurchase means a new order (and potentially a renewal extension on the ledger side).
- **BR-15 Events.** `order.refunded.v1` shape frozen (32's catalog); emitter marker `refund.emitted` + reconciliation re-emits marker-less reversed refunds once.
- **BR-16 Audit.** Every request, approval, rejection, execution, retirement, and failure appends actor-attributed audit rows (24 months); two-person pairs are recorded by identity.
- **BR-17 Scoping.** Buyer routes are owner-scoped (RLS + server-set `user_id`); ops routes require the restricted role per request; the internal route is service JWT + allowlist only.
- **BR-18 Data hygiene.** No PAN/card/bank material in any row, log, event, receipt, or note; reason strings are plain text; emails masked in logs.
- **BR-19 Rate limits.** Request 3/day/order + 10/day/IP; reads 60/h; ops actions 30/h/operator; all 429 with `Retry-After`.
- **BR-20 Degrade.** PSP down → `failed` + backoff; event bus down → revocation/notification delayed, money truth unaffected, reconciliation heals; billing down → all routes 503; manual rails never depend on any PSP being up.
- **BR-21 No-default gate.** Refund code contains no hardcoded provider default or fallback ordering; the CI grep gate is normative (32's D-4 posture extended to this module).
- **BR-22 Coupons.** Refund amount = amount actually paid; coupon unwinding is deferred to 39's design (recorded seam); no promotion state is mutated by a refund in v1.
