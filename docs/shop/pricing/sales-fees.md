# Sales Fees (are there fees when I sell? — schedule per tier)

PLAN ref: P3-3 (free/pro tiers) + P2-2/P2-3 (sellable kinds). This doc answers the link.me-parity fee question for our VN-adapted matrix: two tiers (free/pro), PayOS-first rails. Illustrative rates below are structural placeholders — finance sign-off required (see Q1) before any checkout copy quotes them.

## Purpose

Give the one honest answer to "are there fees when I sell?": a public per-tier fee schedule, a pre-payment breakdown on every checkout, and an engine that computes identical figures at quote, charge, and ledger time. Fee surprises are refund requests; this doc makes surprises structurally impossible.

## Users

- Seller: reads the schedule, sees exact fees before each sale, compares tiers at a glance.
- Buyer: pays the displayed total — fees are seller-side and never added opaquely at the last step (any buyer-visible service charge is a separate line item, not a silent bump).
- Finance/support: explains any historical figure from pinned rate versions.

## Functional requirements

1. Schedule structure (per tier × per kind): platform fee % (structure: free higher, pro lower; tips same schedule, no carve-outs) + payment-rail passthrough (provider cost passed at cost, labeled) + payout terms (settlement delay, minimum payout). Link-out sales carry no platform fee (money moves off-platform; clicks only). Proposed bands (PLACEHOLDER, finance to ratify): free 8–10% / pro 3–5% by kind; rail passthrough at provider cost (PayOS-first, see provider majors).
2. Quote-charge-ledger identity: `POST /api/v1/fees/quote` (pure, synchronous) → checkout snapshot → ledger lines use the identical function + pinned `rate_version`. Three-way reconciliation (quote vs charged vs ledger) is exact to the VND; any mismatch pages finance instead of settling.
3. Buyer-facing display: seller absorbs fees by default (buyer sees one total); price display on shop/product surfaces is fee-inclusive; seller dashboard shows gross, fee lines, and net per order. No drip-pricing: the first price shown is the payable total.
4. Tier-change semantics: fee rate locks at checkout creation (mid-checkout plan upgrade does not re-price an open checkout; next checkout uses the new rate). Subscription lapse reverts new checkouts to free-tier rates with seller-visible notice — in-flight checkouts unaffected.
5. Free-product and tip handling: price-0 enrollments carry zero fees (no minimum-fee floor that makes free cost money); tips follow the standard schedule (goodwill is not fee-exempt — stated plainly in the schedule).
6. Schedule publication: marketing/pricing page copy, in-dashboard seller summary, and checkout fee lines all render from the same versioned config — no hand-typed percentages in copy that can drift from the engine.

## Data / API touches

- Rate config: versioned platform config `fee_schedule{rate_version, tier, kind, pct_bp, rail_passthrough_rule, payout_terms}` (finance-role writes, audited; basis points integers, never floats). Orders snapshot `{gross_vnd, fee_lines[], net_vnd, rate_version}` at creation; sales ledger consumes the snapshot (see `monetize/sales/sales-ledger.md`).
- Endpoints: `GET /api/v1/fees/schedule` (public, current version), `POST /api/v1/fees/quote` (pure quote), checkout integration (snapshot), ops rate-publish (versioned, dual-control per the ops console pattern). All PG via `worker/db/*`.
- Change management: new rate versions apply prospectively only; historical orders keep their pinned version forever (finance close reproducibility).

## Acceptance criteria

- Identity drill: fixture sales reconcile quote = charged = ledger to the VND across tiers × kinds × rate versions.
- Display drill: shopper-facing totals equal payable amounts (no last-step additions); seller view decomposes gross → fees → net exactly.
- Version drill: mid-checkout tier change leaves the open checkout priced; rate publish affects only new checkouts; closed-period figures immutable.
- Copy drill: pricing page, dashboard summary, and checkout lines render identical percentages from the same config version (drift test in CI).
- Free/tip drill: zero-price orders carry zero fees; tip orders carry the standard schedule (verified in ledger lines).

## Out of scope

- Plan prices and subscription mechanics (`monetize/plans/`, `monetize/subscription/`, `monetize/billing/`); checkout orchestration and provider rail costs (provider majors + `monetize/payment/`).
- Profit-target math (see `merch/profit-calculator.md`, which consumes this schedule); sales analytics and ledger presentation (`monetize/sales/`); promo/affiliate adjustments (their minors layer on top of these lines).

## Open questions

- Q1: Ratified launch rates (platform % per tier × kind, payout delays/minimums) — finance decision blocking checkout fee copy?
- Q2: Buyer-visible service charge ever (e.g. ticket-style fee on paid calls/courses), or seller-absorbs permanently?
