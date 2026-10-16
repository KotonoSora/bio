# Entitlement and Plans - Workflow and User Journey (feature 35)

Phase 1 BA · Flows executed by `docs/02-architecture-techlead/35-entitlement-and-plans/`. Rules cited as `BR-nn` from `docs/01-requirements-ba/35-entitlement-and-plans/02-user-stories-and-business-rules.md`.

## Plan purchase to visible plan

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Buyer | Picks pro + term on the plan page | Quote returns the active price for the plan/term pair; rail choice follows 33 | BR-06, BR-07 |
| 2 | System | Checkout created | Order stamps the active term row; pending expiry per 33/32 | BR-06 |
| 3 | Rail | Payment confirmed | 40 verifies; 34's grant step runs | BR-03 |
| 4 | System | Grant transaction | Superseded row closes (audited), new row opens: plan, term, provider, order, period | BR-01, BR-03 |
| 5 | System | Snapshot resolution | `GET /internal/v1/billing/plan/{userId}` returns pro; gates open within the `<= 30 s` bound | BR-05, BR-11 |

## First-touch free floor

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | New user | Hits any gated surface | Snapshot resolves; no subscription history exists | BR-02 |
| 2 | System | Ledger read | Missing current row + no history -> free row materialized idempotently; resolution returns `free` | BR-02 |
| 3 | System | History without current row | Corruption path: loud failure + `plan.missing_row` alert; never a silent free pass | BR-02 |

## Upgrade mid-period

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Free user | Buys pro while no row is current | New row opens; nothing to supersede | BR-01 |
| 2 | Pro user | Buys a different term/plan | Current row closes as `superseded` in the same transaction; new row's period starts at grant | BR-01, BR-03 |
| 3 | System | AI quota read | Quota stamps from the new term row; old period's consumed credits remain recorded | BR-13 |

## Ops publishes a catalog version

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Ops operator | Drafts monthly term (amount, AI quota) | Draft row `v(n+1)` created, audited; no effect on live quotes | BR-09, BR-17 |
| 2 | Ops operator | Drafts yearly term | Amount derived: `12 x monthly - configured discount` to the VND; stored in the row | BR-08 |
| 3 | Finance operator | Activates the pair | Activation refused if a row is already active for the (plan, term); on success old row retires, flips effective `<= 5 s` | BR-09, BR-17 |
| 4 | System | Public read + quotes | New version serves; already-stamped orders keep their row | BR-06, BR-10 |

## Price-change drill (grandfathering)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Buyer | Leaves a checkout pending at `v1` | Order carries the stamped term row | BR-06 |
| 2 | Ops/Finance | Publishes `v2` before the pending pays | Pending still charges `v1` (stamp); new quotes price `v2` | BR-06 |
| 3 | Rail | Late payment confirms | Grant records the stamped row; no re-price, no amount drift | BR-03, BR-06 |
| 4 | Ops operator | Tries to retire `v1` with pending `v1` orders | Refused until the pendings drain (expire/pay) | BR-09 |

## AI tailor happy path

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Pro user | Opens tailor, pastes a job description | Workspace shows remaining credits; JD capped at 10 000 chars | BR-16 |
| 2 | System | Generate | Debit one credit atomically (keyed); snapshot + JD sent to the AI adapter | BR-14 |
| 3 | System | Generation returns | Sanitized suggestions (max 8) rendered; remaining credits decremented | BR-13, BR-16 |
| 4 | System | Generation fails/overruns | `503 ai_unavailable`; the debit is returned exactly once | BR-14 |

## Gate hits (free / over-quota)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Free user | Calls the tailor | `403 upgrade_required` + pricing CTA; no debit attempted | BR-16 |
| 2 | Pro user | Calls with zero credits left | `429 tailor_quota_exhausted` + upgrade CTA and reset date | BR-13, BR-16 |
| 3 | System | Next period starts | Fresh counter row; no rollover from the old period | BR-13 |

## Support trace

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Support agent | Opens 32's order trace | Sees order timeline + ledger rows + audit for that order | BR-20 |
| 2 | Support agent | Asks "why pro?" | Grant row -> order -> rail reference chain answers it; read-only | BR-03, BR-20 |

## Cancel to downgrade (36 handoff)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Pro user | Cancels | Row -> `cancelled`; service continues to `period_end` (resolver keeps pro) | BR-05 |
| 2 | System | `period_end` passes (even if the job lags) | Resolver already resolves free (time-aware); job materializes `expired` + notifies (36) | BR-05, BR-19 |
| 3 | System | Downgrade executes | Lossless: nothing deleted/hidden; limit guard blocks new creates; gates re-check | BR-12, BR-19 |
