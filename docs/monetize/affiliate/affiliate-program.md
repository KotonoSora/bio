# Affiliate Program (enrollment, tiers, terms)

PLAN ref: none — new scope beyond PLAN P0–P5, flagged explicitly. Closest neighbor is the P4 agency roster (`organizations/agency-skeleton.md`); affiliate is proposed as P4-growth alongside it, and needs a plan update before build. Distinct from referral pro-day credits (`subscription/trials-promotions.md`) and influencer code batches (`promo-code/code-generation.md`), which feed attribution but never pay cash.

## Purpose

Define who can earn cash for referring buyers, on what terms, and at what rates: affiliate enrollment, commission tiers, and the conduct rules that keep the program out of fraud and brand trouble. Referral credits reward users with pro-days; influencer codes attribute batches — neither pays money. This doc is where money-to-strangers begins, so eligibility, tiers, and termination rules are specified up front instead of improvised per dispute.

## Users

- Affiliate (blogger, reviewer, community owner): *As an Affiliate, I want to enroll, share tracked links/codes, earn commission on referred paid orders so that who can earn cash for referring buyers, on what terms, and at what rates as defined here.* enrolls, shares tracked links/codes, earns commission on referred paid orders.
- Referred buyer: *As a Referred buyer, I want to buy at normal catalog prices (affiliate cost borne by margin, never surcharged — stated on checkout-adjacent copy) so that who can earn cash for referring buyers, on what terms, and at what rates as defined here.* buys at normal catalog prices (affiliate cost borne by margin, never surcharged — stated on checkout-adjacent copy).
- Program operator: *As a Program operator, I want to approve affiliates, set tiers, terminate abusers, answer "where is my commission" so that who can earn cash for referring buyers, on what terms, and at what rates as defined here.* approves affiliates, sets tiers, terminates abusers, answers "where is my commission".

## Functional requirements

1. Enrollment (audited): application with identity + payout details + channel description; approval is manual in v1 (auto-approval explicitly deferred — stranger-payout fraud starts at signup). Statuses `applied|active|suspended|terminated`; suspension freezes new attribution while preserving earned-but-unpaid balances for review; termination forfeits only fraud-tainted earnings per the terms, never legitimate history without review.
2. Commission tiers (config-driven, versioned like the plan catalog): default rate per plan term and product category (see Q1 for numbers), volume tiers (e.g. monthly referred revenue crossing thresholds steps the rate up next month — never retroactively within a month, keeping computation auditable). Rate changes apply to attributions after the effective date only; in-flight attribution windows keep the old rate.
3. Attribution basis: commissionable events are paid plan grants and paid product orders (refunded/disputed orders reverse — see tracking doc); trials, 0-VND promo orders, and self-purchases never commission. Commission computed on net collected VND (after provider fees? see Q2 — decided once, applied uniformly).
4. Terms and prohibited conduct (buyer- and affiliate-visible, versioned, acceptance-logged): no cookie stuffing, no brand-bidding on trademarked keywords, no spam, no self-referral, no misrepresentation of pricing; violations trigger the suspension/termination ladder with written notice and an appeal window before forfeiture.
5. Program economics guardrail: total commission exposure per period is capped in config (percentage of referred net revenue); breaching the cap pauses new-affiliate payouts review, never silently underpays earned commissions.
6. Relationship boundary: affiliates are independent referrers, not employees or resellers — no discounting authority, no support obligations, no sub-affiliate tiers in v1 (MLM-shaped structures explicitly excluded).

- 7. Scale/consistency NFR (derived from rules 2 and 5 above): tiered rates stamp the per-order rate snapshot and keep current-month attributions on the prior rate; an exposure-cap breach pauses new-accrual review with earned balances intact — no retroactive rewrites and no new thresholds.

## Data / API touches

- New tables `affiliates{id, user_id?, display_name, status, tier, payout_details_enc, terms_version}` + `commission_tiers{...versioned...}` with standard RLS posture (affiliates see only own rows; program config restricted to ops). Payout details encrypted at rest (same vault discipline as OAuth tokens in P4: never logged). All PG via `worker/db/*`.
- Endpoints: public application intake (Turnstile-guarded), affiliate self-service (profile, links/codes, earnings read-only), audited ops endpoints (approve/suspend/terminate, tier changes). No affiliate-settable rates anywhere.
- Terms versions stored with acceptance timestamps per affiliate; rate changes require no re-acceptance but trigger notification (material adverse changes trigger re-acceptance — defined in terms).

## Acceptance criteria

- Enrollment drill: application → manual approval → active affiliate with attributable links/codes; auto-approval impossible (no code path).
  *GWT: Given Enrollment drill: application; When the transition executes; Then manual approval → active affiliate with attributable links/codes; auto-approval impossible (no code path).*
- Tier drill: volume threshold crossed mid-month steps next month's rate only; current-month attributions keep prior rate (auditable per-order rate stamp).
  *GWT: Given Tier drill context; When volume threshold crossed mid-month steps next month's rate only; Then current-month attributions keep prior rate (auditable per-order rate stamp).*
- Conduct drill: cookie-stuffing signal → suspension (attribution frozen, balance held) → upheld termination forfeits tainted earnings with notice + appeal log; legitimate history untouched without review.
  *GWT: Given Conduct drill: cookie-stuffing signal; When the transition executes; Then suspension (attribution frozen, balance held) → upheld termination forfeits tainted earnings with notice + appeal log; legitimate history untouched without review.*
- Buyer-price drill: referred checkout totals identical to direct totals for the same cart (margin-funded, never surcharged).
  *GWT: Given Buyer-price drill context; When the checked action occurs; Then referred checkout totals identical to direct totals for the same cart (margin-funded, never surcharged).*
- Cap drill: exposure cap breach pauses new accrual review with earned balances intact and visible.
  *GWT: Given Cap drill context; When the checked action occurs; Then exposure cap breach pauses new accrual review with earned balances intact and visible.*
- Auth probe: affiliate A reading affiliate B rows → 403; RLS denies cross-affiliate rows.
  *GWT: Given Auth probe: affiliate A reading affiliate B rows; When the transition executes; Then 403; RLS denies cross-affiliate rows.*

## Out of scope

- Link/code mechanics and attribution windows (see `affiliate/tracking-attribution.md`); commission computation and payouts (see `affiliate/commission-payout.md`).
- Referral pro-day credits (`subscription/trials-promotions.md`); influencer batch supply (`promo-code/`); agency seats and Act-as-user (`organizations/`, P4); P5.

## Open questions

- Q1: Launch rates and tiers — flat opening rate vs tiered from day one (finance call with margin model)?
- Q2: Commission base — net of provider fees or gross collected (decide once, disclose in terms)?
