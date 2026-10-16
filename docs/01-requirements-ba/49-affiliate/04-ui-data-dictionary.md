# Affiliate Tracking, Program, and Payouts - UI Data Dictionary (feature 49) - GATED

Phase 1 BA · GATED: no surface below may be built before the amendment. Surfaces: affiliate dashboard, enrollment application, ops program console, finance payout console. Buyers get no affiliate UI — only checkout-adjacent copy that affiliate cost is margin-funded.

## Affiliate dashboard

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `statsPanel` | Stat row | On load | Clicks, conversions, pending/confirmed counts | Aggregates + own referrals only; near-real time |
| `linksPanel` | Table | On load | Vanity slug + random suffix; copy action | Suffix rotates on request after leaks |
| `codesPanel` | List | On load | Affiliate codes (39's supply) | Read-only here; codes beat cookies at checkout |
| `earningsPanel` | Table | On load | Per-line states `pending/payable/in_payout/paid/failed/reversed` + threshold progress | One ledger is the single truth |
| `statementsList` | Table | On load | Per-cycle statement (lines, rates, reversals, payout ref) | Download + email copy match |
| `payoutDetails` | Form | Yes for payouts | Vault-encrypted; never rendered back in full | Incomplete tax fields → batch blocked with fix-it notice |
| `missingPayoutAction` | Button | Optional | Support flow starts from statement id | Ends in the ledger, never chat memory |

## Enrollment application

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `identityFields` | Form | Yes | Identity + required tax fields collected up front | Finance/legal-owned form list |
| `payoutFields` | Form | Yes | Bank details encrypted at rest | Never logged |
| `channelDescription` | Textarea | Yes | How the affiliate will refer | Manual review input |
| `termsAcceptance` | Checkbox + version | Yes | Versioned terms; acceptance timestamp logged | Material adverse changes trigger re-acceptance |
| `statusChip` | Chip | Yes | `applied/active/suspended/terminated` | Auto-approval path does not exist |

## Ops program console

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `applicantQueue` | Table | On load | Manual review queue with channel + identity | Approve/deny audited |
| `affiliateActions` | Buttons + reason | Yes to act | Approve/suspend/terminate; notice + appeal log on adverse actions | Termination forfeits only fraud-tainted earnings |
| `tiersEditor` | Versioned config form | Yes to act | Rates/tiers per catalog discipline; effective-date semantics | Absence → activation refuses `rates_incomplete` |
| `evidenceLookup` | Search + bundle view | On demand | Click id, code, timestamps, rate snapshot, order ref | Read-only; immutable |
| `exposurePanel` | Stat | On load | Commission exposure vs config cap for the period | Breach pauses new-accrual review |

## Finance payout console

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `batchBuilder` | Table + freeze action | Yes to act | Eligible lines (threshold met, tax fields complete); freeze blocks double-instruct | Monthly cycle |
| `dualControlApproval` | Two-approver flow | Yes to execute | Same discipline as manual bank refunds | Instruction file + statements generated together |
| `reconcileAction` | Upload + match | Yes to finish | Confirmations reconcile per line; failures open exactly one case each | Idempotent re-runs are no-ops |
| `ledgerView` | Table | On load | All states + linked reversal references | Commission expense books off these rows |

## Validation and behavior notes

- Referred checkout totals render identical to direct for the same cart (copy: "Affiliate rewards are paid by us, never added to your price.").
- No rate, threshold, or cap value renders from hardcoded code — all read from versioned config.
- Empty states: no referrals yet → link/code sharing guidance; no statements → first cycle explanation.
