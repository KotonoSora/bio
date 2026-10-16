# Affiliate Tracking, Program, and Payouts - Workflow and User Journey (feature 49) - GATED

Phase 1 BA · W-1..W-6. GATED: all flows are design; nothing executes until the amendment. Rules: `docs/01-requirements-ba/49-affiliate/02-user-stories-and-business-rules.md`. Contracts: `docs/02-architecture-techlead/49-affiliate/03-api-contracts-and-payloads.md`.

## W-1 Referral click → order attribution

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Buyer | opens `/r/<slug>-<random>` | fast redirect to the target; signed cookie set; click row stored server-side (ip/ua hashes) | BR-02 |
| 2 | Buyer | shops normally | no surcharge, no friction; declined consent simply leaves traffic unattributed | BR-07 |
| 3 | System | checkout runs | signals resolve: code beats cookie; most recent click wins among cookies; direct = unattributed | BR-03 |
| 4 | System | order created | winning `affiliate_id` + evidence ref stamped at creation; fixed pre-payment | BR-03 |
| 5 | Affiliate | opens stats | clicks, conversions, pending/confirmed in near-real time; aggregates only | BR-07, BR-10 |

## W-2 Enrollment → activation

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Applicant | submits the application (identity, payout details, channel) | Turnstile-guarded intake; status `applied`; audited | BR-08 |
| 2 | Operator | reviews and approves | status `active`; links/codes attributable; terms version logged with acceptance | BR-08, BR-11 |
| 3 | System | rates catalog absent | activation refuses `rates_incomplete`; nothing invented | Q-03 |
| 4 | Operator | suspends or terminates | suspension freezes new attribution, balances held; termination forfeits only fraud-tainted earnings with notice + appeal log | BR-08, BR-11 |

## W-3 Commission lifecycle

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System | referred order paid (grant past refund window) | line `pending` → `confirmed`; rate snapshot from attribution time | BR-05, BR-10 |
| 2 | System | refund or dispute loss | line `reversed` (proportional on partials) with the linked reference | BR-05 |
| 3 | System | trial/0-VND/cancelled/self-purchase | never leaves `pending`; self-referral flags the affiliate | BR-05, BR-06 |
| 4 | Affiliate | reads earnings | per-line states + threshold progress | BR-13 |

## W-4 Payout cycle

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Finance | runs the monthly batch | eligible lines freeze; instruction file + statements generated; dual-control approval | BR-13, BR-14 |
| 2 | System | executes | bank transfers out; reconcile confirmations; lines `paid`; statements + email match | BR-13, BR-16 |
| 3 | System | a line fails | stays `in_payout` with exactly one ops case; batch continues | BR-13 |
| 4 | System | confirm re-run | idempotent no-op; double-instruct impossible while frozen | BR-13 |
| 5 | Finance | reads the ledger | commission expense books off the same rows | BR-14 |

## W-5 Reversal / dispute

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Dispute reviewer | opens a case | evidence bundle replays the attribution (click id, code, timestamps, rate snapshot, order ref) | OBJ-01 |
| 2 | System | refund executed | `pending`/`payable` lines reverse with the linked refund reference | BR-05 |
| 3 | System | line already `paid` | notified debit against future payables; full audit chain; never a silent pull | BR-16 |

## W-6 Suspension → termination

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System | stuffing signal detected | new attribution frozen; balance held; affiliate flagged | BR-06, BR-08 |
| 2 | Operator | reviews with notice + appeal window | upheld → termination forfeits tainted earnings only; legitimate history untouched without review | BR-08, BR-11 |
| 3 | System | exposure-cap breach | new-accrual review pauses; earned balances visible and intact | BR-12 |
