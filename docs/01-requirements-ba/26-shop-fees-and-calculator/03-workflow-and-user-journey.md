# Sales Fees and Profit Calculator - Workflow and User Journey (feature 26)

Phase 1 BA · System calls cite owning features; finance actions are dual-control and audited.

## Finance: draft → approve → publish

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Finance/ops A | drafts a rate version (rows per tier × kind) | draft stored; structural invariants validated (free ≥ pro, linkout = 0) | BR-01/02 |
| 2 | Finance/ops B | approves the draft | version `published`; `requested_by ≠ approved_by` enforced | BR-02 |
| 3 | System | publish lands | new checkouts use it; historical orders keep pinned versions forever | BR-02/06 |
| 4 | Ops | enters/updates partner base costs | cost rows audited; calculator quotes reflect them | BR-13 |

## Seller: price a product

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Seller | opens the calculator on a merch product | inputs prefilled: base cost (cost table), shipping estimate, rail estimate, current-tier fee | BR-08 |
| 2 | Seller | sets desired profit (forward) or target price (reverse) | exact math; line decomposition labeled contractual vs estimate | BR-08/09 |
| 3 | Seller | compares tiers | free vs pro profit side by side; break-even units from live plan prices or "pending" | BR-10 |
| 4 | Seller | saves a named scenario | immutable row with `rate_version` | BR-11 |
| 5 | Seller | publishes the price | product price + pinned `rate_version` written; below-cost rejected with shortfall | BR-12 |
| 6 | System | a new rate version publishes later | saved scenarios and live prices unchanged until explicit republish | BR-11/12 |

## Buyer: pay the displayed total

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Buyer | opens a product page | one price shown — the payable total | BR-05 |
| 2 | Checkout (33/34) | creates the order | internal quote snapshots `{gross, fee lines, net, rate_version}` | BR-04/06 |
| 3 | Buyer | completes payment | charged total equals the displayed total; fees stay seller-side | BR-05 |
| 4 | Ledger (38) | settles | same function + pinned version → identical figures; mismatch pages finance | BR-04 (forward) |

## Support: replay

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Support | opens a closed-period order | pinned version + saved inputs reproduce the figures exactly | BR-04/11 |
| 2 | Support | requotes a saved scenario after a rate change | old-vs-new shown explicitly | BR-11 |

## Pending state (pre-ratification)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Seller | opens the schedule | 404 `schedule_not_published` — no copy renders | BR-03 |
| 2 | Checkout | requests a quote | 409 `schedule_not_published`; checkout fee-snapshot enablement is release-gated on a published version | BR-03 |
| 3 | Tests | identity drills run | labeled fixture versions in test databases only | BR-03 |
