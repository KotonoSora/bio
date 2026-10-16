# Sales Fees and Profit Calculator - User Stories and Business Rules (feature 26)

Phase 1 BA · Realized by `docs/02-architecture-techlead/26-shop-fees-and-calculator/` (BR-01..BR-17). No literal fee values appear in this feature — structure and mechanism only.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Seller | read the public schedule and see exact fees before each sale | fee surprises never occur | Must |
| US-02 | Buyer | pay the displayed total with no last-step additions | checkout is honest | Must |
| US-03 | Finance/ops | publish ratified rates with dual control and immutability | close stays reproducible | Must |
| US-04 | Creator | compute merch list prices from costs, fees, and target profit (and the reverse) | pricing carries exact math | Must |
| US-05 | Creator | compare tiers and see break-even units | pro pays for itself or not, visibly | Should |
| US-06 | Support | replay any figure from saved inputs + pinned versions | price questions resolve exactly | Should |

## Business rules

- BR-01 Schedule structure: per tier (`free|pro`) × per kind (the seven product kinds): platform fee in integer basis points (`pct_bp`), rail passthrough at cost (labeled, never marked up), payout terms (delay days, minimum VND). Structural invariants: free ≥ pro per kind; `linkout` = 0 (DB-enforced); tips carry the standard schedule, no carve-outs.
- BR-02 Values are finance-owned: publishing requires a draft requested by one finance/ops operator and approved by a **different** operator (dual control; DB CHECK `requested_by <> approved_by`); versions are append-only (`draft → published → retired`), audited, and prospective only.
- BR-03 Pending state: with no published version, quote endpoints refuse 409 `schedule_not_published`, schedule reads 404, and no surface renders fee copy. No default or placeholder rate exists in any environment; test fixtures live only in test databases.
- BR-04 Quote identity: one pure function computes fee lines for quote, checkout snapshot, and ledger; integer basis-point math only (zero floats); rounding per the pinned version's rule; the three-way reconciliation (quote vs charged vs ledger) is exact to the VND, and any mismatch pages finance instead of settling (executed by 38).
- BR-05 Display: buyer-facing = one payable total, identical to the last step (no drip pricing, no silent bumps); seller-facing = gross → fee lines → net per order; schedule page, dashboard summary, and checkout lines render the same config version (CI drift test).
- BR-06 Rate locking: checkout pins `rate_version` at creation (order-side snapshot); a mid-checkout tier upgrade does not re-price the open checkout; subscription lapse reverts only new checkouts to free-tier rates with a seller-visible notice; in-flight checkouts are unaffected.
- BR-07 Free and small money: price-0 orders carry zero fee lines (no minimum-fee floor); tips follow the standard schedule and the schedule says so plainly; link-outs never enter the fee engine (no order row exists).
- BR-08 Calculator forward mode: inputs = partner base cost, variant delta, zone-averaged shipping estimate, rail passthrough estimate, current-tier fee, desired profit per unit → outputs = list price (rounded per rule), profit, margin (basis points), and labeled line decomposition (contractual: base cost, platform fee; estimates: shipping, rail).
- BR-09 Calculator reverse mode: target list price → profit/margin; a price below total cost is flagged `below_cost_blocked` with the shortfall stated and cannot be published from the scenario.
- BR-10 Tier comparison: side-by-side profit at free vs pro for identical inputs, with break-even units computed from live plan prices (forward 36) — never from stale copy; unpriced plans show break-even as pending, never guessed.
- BR-11 Scenarios: named, owner-scoped, immutable rows; edits append new versions; every scenario carries its `rate_version`; a requote after a rate change shows old-vs-new explicitly.
- BR-12 Publish-price: an explicit seller action writes the price + pinned `rate_version` (+ scenario ref) to the product; a rate change never silently alters a saved scenario or a live price.
- BR-13 Merch quotes require a partner cost entry for the template+variant (`cost_unknown` otherwise); costs enter via audited ops/finance routes; values are never guessed.
- BR-14 Ownership: scenarios and publish-price scope to the owning `user_id` + RLS; IDOR → 403; rate tables are platform config (read-only to app paths; writes only via the dual-control routes).
- BR-15 Audit: rate drafts/approvals/publishes, cost updates, publish-price actions, and scenario writes are append-only (ids only).
- BR-16 Purge: seller account deletion removes their scenarios; rate and cost tables are platform config and survive (no personal data).
- BR-17 No-invention gate: this feature's directory contains zero literal fee percentages or currency amounts for rates; CI grep fails any addition; the schedule renders exclusively from published config rows.
