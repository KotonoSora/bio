# Sales Fees and Profit Calculator - Context and Scope (feature 26)

Phase 1 BA · Feature 26 of 53 — shop 5/5. Legacy sources: `shop/pricing/sales-fees.md` and `shop/merch/profit-calculator.md` (folded here; deleted with this batch). Realized by `docs/02-architecture-techlead/26-shop-fees-and-calculator/`; extends shop-service (features 22-25).

## Purpose

Give the one honest answer to "are there fees when I sell?": a public per-tier fee schedule, a pre-payment breakdown on every sale, one engine whose figures are identical at quote, charge, and ledger time — and a calculator that answers "what should I charge?" from base cost, shipping estimate, fees, and desired profit with exact integer-VND math.

## Objectives

| # | Objective |
|---|---|
| O-1 | Schedule structure per tier × kind (platform fee, rail passthrough at cost, payout terms) published from versioned config — never hand-typed copy. |
| O-2 | Quote = charged = ledger to the VND via one pure function + pinned `rate_version`; any mismatch pages finance instead of settling. |
| O-3 | Display rules: the buyer sees one payable total (no last-step additions); the seller sees gross → fee lines → net. |
| O-4 | Rate locking: checkout pins its rate version; upgrades and rate publishes affect only new checkouts; history is immutable. |
| O-5 | Calculator: forward (price from profit) and reverse (profit from price) modes, tier comparison with break-even, immutable named scenarios, explicit publish-price. |
| O-6 | Enforceable honesty: free products carry zero fees; tips follow the standard schedule (stated plainly); link-outs carry no platform fee; estimates are labeled, never dressed as contractual. |

## Personas / RBAC

| Role | CRUD Permissions | Description |
|---|---|---|
| Seller | R schedule; R/W own scenarios; W publish-price on own products | reads fees, prices merch, compares tiers |
| Buyer | R totals only | pays the displayed total; fee mechanics stay seller-side |
| Finance/ops (dual-control) | C rate-version drafts; U approve (different operator) | publishes rates after sign-off; audited |
| Support | R (replay from saved inputs + pinned versions) | explains any historical figure |
| Checkout service (33/34) | R quotes (internal seam) | snapshots fee lines onto orders |

## In scope

Rate versioning + dual-control publish + public schedule read; the pure quote function (seller endpoint + internal checkout seam); order fee-snapshot semantics (money movement itself is 32-40); display rules; calculator (quote math, cost table, scenarios, publish-price, tier comparison); audit; purge of scenarios.

## Out of scope

Plan prices and subscription mechanics (32-40; 36 owns projections); checkout/refund/receipts/ledger/disputes (32-40); the ledger-side reconciliation execution (38; the identity and its fixture drills are fixed here); partner SLAs and shipping (23, provider domain); marketing pricing-page rendering (48-53 gated; the public schedule endpoint ships here); promo/affiliate adjustments (their own minors).

## Open questions (resolved)

| Q | Resolution |
|---|---|
| Q1 finance-pending values | The legacy placeholder bands are **not carried as values** (the legacy flagged them unratified). This feature ships mechanism only: rates publish via dual-control config; until a ratified version exists, quotes refuse `schedule_not_published` — the engine never fabricates a rate, and this tree contains zero literal fee percentages (grep gate). Tests use labeled fixture versions in test databases only. |
| Q2 buyer-visible service charge | None in v1 — seller absorbs fees permanently for now; a buyer-side service charge would be a separate line item by amendment, never a silent bump. |
| Q3 shipping estimates | Zone-averaged estimates in v1, labeled as estimates; exact zones v2; reconciliation happens at checkout (32-40). |
| Q4 rounding rule | Config enum on the published version: `nearest_1000` (launch rule per the legacy example) or `exact` (half-up to VND); integer ops only. |
| Q5 tier source | Subscription projection (36, forward); until it lands only `free` is assignable and no pro row is fabricated — quoting uses whatever published rows exist. |
| Q6 structural invariants | Free-tier platform fee ≥ pro-tier per kind; `linkout` = 0 (DB-enforced); tips carry the standard schedule with no carve-outs — structure only, no values. |
