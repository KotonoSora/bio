# Promo Redemption Rules (stacking, flow, and abuse handling)

PLAN ref: P3-3 (buyer-facing redemption contract binding `promo-code/campaign-management.md` eligibility to `subscription/trials-promotions.md` grant mechanics). Generation in `promo-code/code-generation.md`.

## Purpose

Define the exact buyer experience and rule set at redemption time: where codes are entered, what stacks with what, how errors read, and how abuse is contained without punishing legitimate buyers. Two failure modes are designed out here: combinable discounts compounding into unintended free pro (stacking ambiguity), and hostile buyers probing the validate endpoint into an oracle (rejection leakage).

## Users

- Buyer: enters a code at checkout (or on the plan page pre-checkout), sees the discounted total before paying, gets a clear correctable error on problems.
- Trial/credit holder: understands how a coupon interacts with existing benefits instead of guessing.
- Fraud reviewer: sees flagged redemption patterns with context instead of raw logs.

## Functional requirements

1. Entry points: checkout code field (primary) + plan-page "have a code?" quote preview (calls the same validate endpoint, never grants). Both show line-item math (catalog price − coupon = charge) before provider handoff; the quoted total is frozen for the pending window and re-checked at grant.
2. Stacking policy (closed, server-enforced): exactly ONE coupon per order; coupons do not stack with each other (second code replaces the first with explicit buyer confirmation). Coupon + active trial: allowed — coupon discounts the first paid term after trial, trial period itself unchanged. Coupon + referral/goodwill credit-days: allowed — credit-days extend the discounted term. Coupon + already-discounted renewal: the renewal re-prices from the active catalog and the coupon applies only if its campaign window still covers the renewal date (almost never — stated in UX copy: "one-time discount").
3. Rejection taxonomy (buyer-safe, oracle-free): `invalid`, `expired`, `exhausted`, `not_eligible`, `already_redeemed` — all render the same neutral "This code can't be applied" with one correctable hint where safe (e.g. expiry is safe to state; eligibility reasons are not). Response timing normalized across outcomes; per-IP/email throttling shared with checkout budgets.
4. Farming and abuse containment: one-trial-ever and per-user coupon limits keyed on account + signals (shared with trials doc); referral self-dealing (referrer = referee signals) voids the credit, never the paid order; velocity anomalies (many distinct codes from one identity, many identities one device) flag for human review with auto-pause of the offending campaign as the containment action — never auto-ban in v1.
5. Quote integrity: validate responses are quotes, not reservations (except single-use consumption at grant, which the generation doc owns). Campaign pause/expiry between quote and pay fails the grant with the same neutral error and an offer to pay full price in one click — never a surprise charge, never a silent discount.
6. Accessibility and honesty: code field keyboard-reachable, errors announced, discount math in plain language and figures + words for VN buyers; dark patterns forbidden (no fake countdowns, no pre-applied codes without consent).

## Data / API touches

- No new tables: validation reads `coupons` (hash lookup) + `promo_campaigns` (eligibility, counters); redemption writes flow through the existing order/subscription/audit writes. Stacking state (`applied_coupon`) lives transiently on the pending order, never as persistent buyer state. All PG via `worker/db/*`.
- Endpoints: existing `POST /api/billing/coupons/validate` (quote) + `POST /api/billing/checkout` (applies `coupon_code`, enforces stacking + eligibility + budget decrement atomically). Timing normalization and throttling configured per the trials doc's budgets.
- Fraud flags land in the metrics/audit pipeline (signals + counts, no buyer PII beyond owner scope); campaign auto-pause on anomaly is an audited system action.

## Acceptance criteria

- Stacking drill: two coupons on one order → second replaces first only with confirmation; coupon + trial + credits combine exactly per the matrix; renewal with an expired campaign charges full catalog price with honest copy.
- Oracle drill: invalid/expired/exhausted/ineligible codes indistinguishable by timing or error text (except the two safe hints); throttling engages under brute force.
- Quote/grant race drill: campaign paused between quote and pay → grant refused, full-price one-click offered, no partial discount.
- Abuse drill: self-referral ring voids credits while preserving paid orders; velocity anomaly auto-pauses the campaign and opens a review case; no auto-bans.
- Accessibility drill: full keyboard + screen-reader redemption pass; math in figures + words.

## Out of scope

- Campaign budgets and lifecycle (see `promo-code/campaign-management.md`); batch generation and formats (see `promo-code/code-generation.md`).
- Coupon kinds and repricing math (`subscription/trials-promotions.md`); catalog versioning (`subscription/plan-catalog.md`); ledger/lifecycle (`billing/`); P4/P5.

## Open questions

- Q1: Safe-hint boundary — which rejection reasons may state specifics vs stay neutral (fraud-team call)?
- Q2: Plan-page quote preview — include for launch, or checkout-only to shrink the oracle surface?
