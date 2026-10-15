# Promo Redemption Rules (stacking, flow, and abuse handling)

PLAN ref: P3-3 (buyer-facing redemption contract binding `promo-code/campaign-management.md` eligibility to `subscription/trials-promotions.md` grant mechanics). Generation in `promo-code/code-generation.md`.

## Purpose

Define the exact buyer experience and rule set at redemption time: where codes are entered, what stacks with what, how errors read, and how abuse is contained without punishing legitimate buyers. Two failure modes are designed out here: combinable discounts compounding into unintended free pro (stacking ambiguity), and hostile buyers probing the validate endpoint into an oracle (rejection leakage).

## Users

| Role | Permissions | Notes |
|---|---|---|
| Buyer | Enter a code at checkout (or on the plan page pre-checkout), see the discounted total before paying | Gets a clear correctable error on problems |
| Trial/credit holder | Understand how a coupon interacts with existing benefits | Avoids guessing on stacking |
| Fraud reviewer | See flagged redemption patterns with context | Uses signals + counts instead of raw logs |

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Buyer | Enter a code at checkout, see the discounted total before paying, and get a clear correctable error on problems | Combinable discounts never compound into unintended free pro | Must |
| US-02 | Trial/credit holder | Understand how a coupon interacts with existing benefits | I avoid guessing on stacking | Must |
| US-03 | Fraud reviewer | See flagged redemption patterns with context | I work from signals + counts instead of raw logs | Must |

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

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Stacking drill: two coupons on one order; second replaces first only with confirmation; coupon + trial + credits combine exactly per the matrix; renewal with an expired campaign charges full catalog price with honest copy | Two coupons on one order and coupon + trial + credits | Stacking and renewal run | The second replaces the first only with confirmation, combinations follow the matrix exactly, and renewal with an expired campaign charges full catalog price with honest copy |
| AC-02 | Oracle drill: invalid/expired/exhausted/ineligible codes indistinguishable by timing or error text (except the two safe hints); throttling engages under brute force | Invalid/expired/exhausted/ineligible codes and brute force | Validation runs | Codes stay indistinguishable by timing or error text except the two safe hints and throttling engages under brute force |
| AC-03 | Quote/grant race drill: campaign paused between quote and pay; grant refused, full-price one-click offered, no partial discount | A campaign paused between quote and pay | Grant runs | Grant is refused, full-price one-click is offered, and no partial discount applies |
| AC-04 | Abuse drill: self-referral ring voids credits while preserving paid orders; velocity anomaly auto-pauses the campaign and opens a review case; no auto-bans | A self-referral ring and a velocity anomaly | Containment runs | Credits void while paid orders preserve, the campaign auto-pauses with a review case opened, and no auto-bans occur |
| AC-05 | Accessibility drill: full keyboard + screen-reader redemption pass; math in figures + words | Keyboard + screen-reader redemption | The redemption flow runs | The full pass succeeds with math in figures + words |

## Out of scope

- Campaign budgets and lifecycle (see `promo-code/campaign-management.md`); batch generation and formats (see `promo-code/code-generation.md`).
- Coupon kinds and repricing math (`subscription/trials-promotions.md`); catalog versioning (`subscription/plan-catalog.md`); ledger/lifecycle (`billing/`); P4/P5.

## Open questions

- Q1: Safe-hint boundary — which rejection reasons may state specifics vs stay neutral (fraud-team call)?
- Q2: Plan-page quote preview — include for launch, or checkout-only to shrink the oracle surface?
