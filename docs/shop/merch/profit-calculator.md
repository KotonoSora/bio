# Merch Profit Calculator (price from costs + tier fees)

PLAN ref: P2-2 (merch listing) + P3-3 (tiering). Rate values are illustrative structure — actual percentages await finance sign-off (see Q1); the calculator ships with config-driven rates, never hardcoded ones.

## Purpose

Answer "what should I charge?" with exact math: from base print cost, shipping estimate, platform fee for the seller's tier, and desired profit, compute the list price — or work backwards from a target price to the profit it yields. Every number shown decomposes to its inputs so a seller trusts the price and support can explain any figure.

## Users

- Creator: prices merch confidently, compares tiers (does pro pay for itself at my volume?), saves scenarios.
- Support: replays any quoted figure from saved inputs.
- Finance: updates rate config; calculator reflects new rates without a deploy.

## Functional requirements

1. Inputs (all VND integers): template base cost (from partner cost table), variant delta, shipping estimate (zone-averaged v1, exact zones v2), platform fee rate for the seller's current tier (from `pricing/sales-fees.md` config), payment-rail passthrough estimate, desired profit per unit (forward mode) or target list price (reverse mode).
2. Outputs: list price (rounded per configured rounding rule, e.g. nearest 1,000 VND), profit per unit, margin %, fee breakdown lines (platform fee, rail estimate, print, ship). Reverse mode outputs profit/margin from a target price and flags below-cost prices as unpublishable.
3. Tier comparison: side-by-side profit at free vs pro fees for the same inputs, with a break-even line (units/month where pro's lower fees cover its subscription) — computed from live plan prices, never stale copy.
4. Scenario saving: named scenarios per product (`calculator_scenarios{product_id, inputs_json, outputs_json, rate_version}`) so a requote after a rate change shows old-vs-new explicitly. Publishing a price from a scenario pins the rate version to the product row.
5. Config-driven rates: all fee/cost/rounding inputs resolve through versioned config (`rate_version` stamped on every quote); a rate change never silently alters a saved scenario or a live price — republishing under new rates is an explicit seller action.
6. Honesty rules: estimates labeled (rail fees, shipping) vs contractual (base cost, platform fee); calculator never promises partner SLAs or delivery dates.

## Data / API touches

- New table `calculator_scenarios` (owner-scoped RLS; immutable rows — edits create new versions). Rate config lives in versioned platform config (same mechanism as plan prices), readable by the quote endpoint, writable by finance role only.
- Endpoints: `POST /api/v1/merch/quote` (pure function, authenticated, no writes), scenario CRUD, `POST /api/v1/merch/publish-price` (writes pinned price + rate version to the product row). Quote latency budget: synchronous, sub-second, no queue.
- Consumer: POD product editor embeds the calculator; fee engine in `pricing/sales-fees.md` is the rate source of truth (this doc never duplicates rate tables).

## Acceptance criteria

- Math drill: fixture inputs reproduce hand-computed price/profit/margin exactly (integer-VND rounding verified at boundaries).
- Rate-change drill: config bump alters new quotes only; saved scenarios and live prices unchanged until explicit republish (pinned `rate_version` verified).
- Below-cost drill: reverse-mode price under total cost blocks publishing with the shortfall stated.
- Tier drill: break-even units match hand computation from live plan prices; plan-price change moves the break-even line.
- IDOR drill: user A scenario reads/writes against user B products return 403.

## Out of scope

- Fee policy itself (see `pricing/sales-fees.md`); POD dispatch and partner SLAs (see `merch/pod-fulfillment.md`); catalog/listing (see `products/product-catalog.md`).
- Checkout/order money movement (`monetize/payment/`); demand forecasting and sales analytics (`monetize/sales/`).

## Open questions

- Q1: Launch rate values (platform fee % per tier, rounding rule) — finance decision needed before publish-price enables?
- Q2: Zone-exact shipping in v1 (address-based quotes) or zone-averaged estimates with reconciliation at checkout?
