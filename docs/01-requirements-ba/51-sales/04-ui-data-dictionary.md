# Sales (Ledger, Analytics, Order Console) - UI Data Dictionary (feature 51) - GATED

Phase 1 BA · GATED: no surface below may be built before the amendment. Surfaces: ops order console, case view, dual-control approvals, finance analytics dashboards, provider scorecards, buyer-facing case status mirror.

## Ops order console

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `searchBox` | Input | Yes | Five identifier kinds: orderCode, buyer email/account, provider refs, receipt/credit-note ids | Unknown → clean no-result; no cross-order leakage |
| `trailView` | Assembled panels | On load | Order row, canonical status, provider events, sales lines + adjustments, receipts, entitlement effects, attributions | p95 ≤ 500 ms; reads from owning features, never reimplemented |
| `stateExplanation` | Note | Always | Buyer-safe sentence + suggested next action | Provider-native codes never quoted to buyers |
| `supportActions` | Buttons | Per role | Resend receipt, re-drive verify, escalate | Preconditions shown; ineligible refused with reason |
| `opsActions` | Buttons + dry-run | Per role | Cancel unpaid, link exception, refund, subscription fix, campaign pause | Dry-run preview shows exact effects; previewed = executed |
| `staleViewGuard` | (behavior) | Always | Actions re-validate at execution | Stale views fail closed with the reason stated |

## Case view + buyer mirror

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `caseThread` | Timeline | On load | Order/exceptions/disputes/payout claims in one thread | Internal notes never exposed |
| `caseStatus` | Chip | Yes | `open/investigating/waiting_buyer/resolved` | Transitions audited |
| `buyerMirror` | Status page | For the buyer | Shows "under review" + next step | Never internal notes or provider codes |
| `escalateAction` | Button | Support tier | Routes to ops case | Audited with actor + reason |

## Dual-control approvals

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `approvalRequest` | Record | Yes for above-floor actions | Proposer, action, payload, expiry (24 h) | Maker-checker: proposer ≠ approver |
| `approveAction` / `rejectAction` | Buttons | Second approver only | Expired approvals cannot execute | Full audit chain |
| `floorNote` | Note | Always | States the config-owned amount floor | Never hardcoded in UI copy |

## Finance analytics + scorecards

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `funnelPanel` | Funnel chart | On load | Started → handed off → paid; slices by provider/plan/campaign/device/day | Hourly aggregates; idempotent rebuilds |
| `revenueCuts` | Tables | On load | Day/week/month; provider; plan vs product; campaign; affiliate vs direct; new vs renewal | Every figure drill-downs to order ids |
| `closeSnapshot` | Versioned cut | At close | Named snapshot; re-runs byte-identical | Post-close adjustments stay in the open period |
| `scorecardTable` | Table | On load | Success rate, median time-to-paid, webhook lag, sweep-rescue, refund/dispute rates | Thresholds config-owned; breaches page ops |
| `alertFeed` | List | On breach | Slice attached to every alert | Alert rules versioned |
| `csvExport` | Button | Yes to act | One-click, audited | Finance tooling input |

## Validation and behavior notes

- No KPI renders that cannot decompose to order ids; drill-down links exist on every figure.
- Gross and net-of-known-fees render as separate columns — never a blended number.
- Bulk actions render per-order results; partial batches are visible, never silent.
- Empty states: no cases → queue guidance; no data for a slice → honest zero with the period stated.
