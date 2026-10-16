# Promo Codes - Workflow and User Journey (feature 39)

Phase 1 BA · End-to-end discount and credit paths. Rules: `docs/01-requirements-ba/39-promo-codes/02-user-stories-and-business-rules.md`.

## W-1: Validate → discounted checkout (percent/fixed)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Buyer | Enters a code at checkout | `POST /api/v1/billing/promo/validate`; hash lookup, eligibility, window, budget checked | BR-06, BR-08, BR-09, BR-14 |
| 2 | System | Valid | Quote returns line-item math: catalog − discount = charge | BR-01, BR-15 |
| 3 | System | Invalid | One neutral `coupon_invalid` (expiry may state itself); timing normalized | BR-14 |
| 4 | Buyer | Proceeds | Create re-validates + decrements budget counters atomically with order minting; handoff charges the discounted total | BR-05, BR-15 |
| 5 | Buyer | Payment verifies | Grant re-checks eligibility, consumes the code (uses / single-use), stamps attribution + `source='coupon'` | BR-11, BR-18 |
| 6 | Buyer | Abandons | Order expiry restores the budget decrement exactly once | BR-05 |

## W-2: 0-VND promo order

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System | Discount reaches the full amount | Final amount 0; no provider handoff exists (kind `none`) | BR-02 |
| 2 | System | Create mints the order | `provider='promo'` (server-minted); budget decrement as usual | BR-02, BR-05 |
| 3 | System | Immediate grant | Canonical amount-checked path: ledger + receipt + `order.granted.v1`; no PSP reference exists | BR-02, BR-18 |
| 4 | Buyer | Opens the receipt | Renders like any paid order, provider labeled as promotion | BR-02 |

## W-3: Term-extend + credits

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Buyer | Redeems a `term_extend` code and pays | Grant inserts a credits ledger row (days, source `coupon`) when valid; attribution stamped | BR-01, BR-18 |
| 2 | System (job) | Daily run finds unconsumed days + an `active`/`grace` row | `period_end += days`; ledger row consumed with subscription id; `plan.credits_applied` audited | BR-16 |
| 3 | Ops | Issues goodwill days | Audited issuance within the configured cap; same application path | BR-17, BR-23 |
| 4 | Buyer | Checks the billing page | Unconsumed days shown as days, never as cash | BR-16 |

## W-4: Campaign lifecycle + burn control

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Ops | Creates a campaign (cap + window + target) | Draft row; unbounded creation impossible | BR-03 |
| 2 | Ops | Launches | `draft → scheduled|live` (audited); jobs move `scheduled → live` at `starts_at` | BR-04 |
| 3 | System | Burn crosses 80% | Alert fires; ops can pause or let it ride | BR-05 |
| 4 | System | Burn hits 100% | Auto-pause (audited); new redemptions reject cleanly; in-flight quotes stand | BR-05, BR-07 |
| 5 | Ops | Pauses mid-campaign | New validations/creates block ≤ 5 s; pending quotes keep theirs | BR-07, BR-08 |
| 6 | System (job) | `ends_at` passes | `→ ended` (audited); terminal | BR-04 |

## W-5: Batch generation → export → leak response

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Ops | Generates 10k influencer codes (prefix + channel) | Atomic batch; plaintext list in the create response exactly once | BR-09, BR-10 |
| 2 | Ops | Acknowledges the export | `export_acked_at` stamped + audited; hashes only thereafter | BR-10 |
| 3 | Ops | Leak detected | Pause campaign; bulk-void unredeemed codes (audited count); redeemed stand | BR-12, BR-19 |
| 4 | Finance | Reads burndown | Committed vs redeemed vs remaining per campaign | BR-03, BR-05 |

## W-6: Rejection / brute force

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Attacker | Probes validate with guesses | Same neutral outcome and timing as legitimate failures; throttles engage | BR-14, BR-21 |
| 2 | System | Velocity anomaly (many codes/identity or identities/device) | Metric + auto-pause of the offending campaign; no auto-bans | BR-20 |
| 3 | Buyer | Typo | One neutral message + correctable hint only where safe (expiry) | BR-14 |
