# Monetize Overview - Workflow and User Journey (feature 32)

Phase 1 BA · The uniform lifecycle at overview depth; feature-specific flows expand it (33-40).

## Buyer: pay on any rail (uniform)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Buyer | opens a checkout surface (plan or product) | eligible methods render in ops-config order, verbatim, with capability hints; no preselected favorite | BR-02/06 |
| 2 | Buyer | picks a rail | explicit `provider` chosen; server re-quotes before any side effect | BR-02/03 |
| 3 | System | create accepted | one `orders` row (int64 orderCode, amount_vnd, immutable provider); handoff `next: url\|qr\|sheet\|token` | BR-03/07/08 |
| 4 | Buyer | pays at the provider, returns | pending page polls canonical status; return params change nothing | BR-03/04 |
| 5 | System | verified confirmation arrives | amount-checked, idempotent grant exactly once; audit appended | BR-03/09 |
| 6 | Buyer | sees paid + receipt | receipt renders `RCPT-<orderCode>`; email queued (never blocking) | BR-13 |

## Ops: rail control without deploys

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Ops | flips eligibility/display/kill switch (reason mandatory) | versioned config updated; audit row; effect ≤ 5 s | BR-06/09/10 |
| 2 | System | a rail is killed | delisted; in-flight pendings routed to settlement with buyer messaging | BR-10 |
| 3 | Ops | restores the rail | delisted state clears the same way; no deploy, no code change | BR-06 |

## Support/finance: trace one order

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Support | looks up `orderCode` | trail: provider refs, status history, ledger rows, receipts, refund annotations, audit | BR-15/09 |
| 2 | Finance | reconciles | provider reference vs ledger vs receipt; mismatches page finance (38 owns execution) | BR-15 |
| 3 | Ops | overrides (rare) | audited with actor + reason; replayable | BR-09 |

## Release: conformance before breadth

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | QA | runs the shared row per rail (sandbox) | all cases green, recorded; any red blocks the rail's ship | BR-11 |
| 2 | QA | rehearses secrets rotation | no-downtime rotation, old credentials dead | BR-12 |
| 3 | Release | verifies gates | container digest pair + platform mixed-load release gate green | BR-11 |

## Notification pipeline (shared side-effect)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Any money feature | a grant/receipt/lifecycle event enqueues a transactional send | queued ≤ 60 s dispatch; grant never waits on email | BR-13 |
| 2 | System | bounce/complaint received | writes back to feature 27's status machine for lead-bound sends | BR-13 |
| 3 | System | outage | backlog visible; grants proceed; no marketing ever rides this queue | BR-13 |
