# Sales (Ledger, Analytics, Order Console) - Workflow and User Journey (feature 51) - GATED

Phase 1 BA · W-1..W-6. GATED: all flows are design; nothing executes until the amendment. Rules: `docs/01-requirements-ba/51-sales/02-user-stories-and-business-rules.md`. Contracts: `docs/02-architecture-techlead/51-sales/03-api-contracts-and-payloads.md`.

## W-1 Grant → sales fact

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Provider | verified webhook → grant step (34) | exactly one `sales_lines` row in the grant transaction | BR-02 |
| 2 | System | duplicate/replayed webhook | UNIQUE guard emits nothing; zero additional lines | BR-02 |
| 3 | Finance | reads gross revenue | lines only; fee fields NULL until confirmed | BR-04 |
| 4 | System | settlement data arrives | fee fields backfilled; net-of-known reads become available | BR-04 |

## W-2 Refund → net

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System | refund executed (38) | `sales_adjustments` append (negative, reasoned); original untouched | BR-03 |
| 2 | System | partial refund or lost dispute | proportional / full adjustment with linked reference | BR-03 |
| 3 | Finance | re-runs the net query | lines + adjustments in one query; matches hand computation | BR-03 |
| 4 | Finance | finds a bookkeeping error | `kind=correction` append with reason + actor; no edit path exists | BR-05 |

## W-3 Lookup → case → action

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Support agent | searches any identifier | assembled trail (order, status, events, lines, receipts, effects, attributions) | BR-08 |
| 2 | Support agent | reads the state | buyer-safe sentence + suggested next action; no provider codes quoted | BR-12 |
| 3 | Support agent | triggers a support-tier action | preconditions checked; ineligible refused with reason; audited | BR-09 |
| 4 | Support agent | escalates | ops case opens; buyer sees "under review"; internal notes hidden | BR-10 |

## W-4 Dual-control action

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Ops operator | proposes a money-moving action (above floor) | dry-run preview of exact effects; approval request created | BR-11, BR-13 |
| 2 | Second approver | approves | maker-checker enforced (proposer ≠ approver); action executes with re-validation | BR-11, BR-13 |
| 3 | System | stale view or expired approval | fails closed; nothing executes; reason stated | BR-11, BR-13 |
| 4 | System | bulk action | per-order results (no silent partial batches) | BR-13 |

## W-5 Close → frozen snapshot

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Finance | closes a period | named versioned snapshot of revenue cuts; re-runs byte-identical | BR-15 |
| 2 | System | post-close adjustments arrive | they append to the open period; the frozen snapshot never changes | BR-15 |
| 3 | Auditor | replays revenue | facts + webhook causality reproduce every figure | BR-06 |

## W-6 Provider health → decisions

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System | hourly aggregates refresh | scorecards: success rate, median time-to-paid, webhook lag, sweep-rescue, refund/dispute rates | BR-16 |
| 2 | System | threshold breach | ops paged with the slice attached; alert rules versioned | BR-16 |
| 3 | Operator | flips the checkout kill-switch | funnel shifts visible same-day; decision recorded with data | BR-16 |
| 4 | Support lead | reads anomaly watch | failed-checkout waves + dispute clusters alert before incidents | BR-17 |
