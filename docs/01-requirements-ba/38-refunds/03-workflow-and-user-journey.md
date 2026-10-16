# Refunds - Workflow and User Journey (feature 38)

Phase 1 BA · End-to-end reversal paths. Rules: `docs/01-requirements-ba/38-refunds/02-user-stories-and-business-rules.md`.

## W-1: Buyer request to reversed (API rail)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Buyer | Opens a refund request on a paid order (reason + Turnstile) | Row `requested`, idempotency key stored; tracker shows the timeline | BR-01, BR-03 |
| 2 | Ops | Approves in the queue | `requested → approved` + audit (actor); approval touches no money | BR-04 |
| 3 | Ops | Executes | `approved → executing` commits; the rail's refund API is called with the refund id as idempotency key where supported | BR-06, BR-08, BR-10 |
| 4 | System | PSP verifies the reversal | Final transaction: order `→ refunded` (guard), row `→ reversed` + `psp_ref`, credit note, plan revocation (if a plan order), event marker | BR-08, BR-09, BR-11, BR-13 |
| 5 | System (post-commit) | — | `order.refunded.v1` publishes; credit-note email enqueues; both idempotent | BR-12, BR-13, BR-15 |
| 6 | Buyer | Opens the receipt | Credit-note section renders linked to the original charge (37's surface) | BR-13 |

## W-2: Manual bank reversal (payos / sepay / napas)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Buyer | Requests a refund (same as W-1) | Row `requested` | BR-03 |
| 2 | Approver (ops A) | Approves with reason | `→ approved`; audit records approver identity | BR-04 |
| 3 | Executor (ops B) | Performs the bank transfer, then executes with `evidence_ref` | Evidence + executor recorded; `→ executing` only after evidence exists | BR-07 |
| 4 | System | Final transition | Same atomic txn as W-1; `psp_ref` = the reversal transfer reference | BR-08 |
| 5 | Finance | Reconciles | Refund row + credit note match the bank statement line; no defaults, no guessing | BR-13, BR-16 |

## W-3: System-class refund (booking policy)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Shop (25) | Policy fires (`hold_expired`, `cancel_ge_24h`, `creator_cancel`) | Internal call with reason + source ref + idempotency key | BR-05 |
| 2 | System | Row enters `approved` (`initiated_by = system`) | API rail: auto-executor picks it up immediately; manual rail: joins the ops queue | BR-05, BR-06 |
| 3 | System/ops | Executes per rail posture | Same atomic finality as W-1 | BR-08, BR-11 |
| 4 | Buyer | Receives the credit-note email | Wording cites the policy reason, not internal codes | BR-13 |

## W-4: Plan refund revocation

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System | Refund of a plan order completes | Match: the order is the current row's origin or an audited extension | BR-11 |
| 2 | System | Revocation txn | Row closes `expired` + free floor opens + `plan.refunded` audit; resolver drops to free on next read | BR-11; 36's path |
| 3 | System | Owning surfaces recompute | Domains/themes/templates gated surfaces fall back losslessly (36/37's read-time model) | BR-11 |
| 4 | Buyer | Re-buys | Fresh order + grant; no shadow restoration of the refunded period exists | BR-14 |
| 5 | Ops | Edge case (refund of one renewal in a chain) | Compensate via 36's period-fix with reason + audit | Q-06 |

## W-5: Failure and retry

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System | PSP call fails mid-execution | `executing → failed` + alert + backoff retry (≤ 5 attempts, ≤ 24 h) | BR-10 |
| 2 | Ops | Retries manually after fixing | Re-execute is status-guarded; retries never double-reverse (PSP idempotency or query-first) | BR-10 |
| 3 | System | Retry budget exhausted | Row stays `failed`; queue escalates; buyer tracker shows honest "processing" copy | BR-10 |

## W-6: Dispute boundary

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Rail | Provider opens a dispute | Order moves `paid → disputed` via 40's handler; refund router sees `disputed` and refuses | BR-01, Q-08 |
| 2 | Rail | Dispute resolves | `disputed → paid` (service continues) or `→ chargeback_reversed` (terminal); neither path mints a credit note here | Q-08 |
