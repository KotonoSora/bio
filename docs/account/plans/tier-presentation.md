# Tier Presentation (buyer-facing plan guarantees)

PLAN ref: P3-3 + `monetize/subscription/plan-catalog.md` (terms/versioning) + `monetize/subscription/trials-promotions.md` (trial mechanics) + `monetize/billing/subscription-lifecycle.md` (cancel/downgrade). No new tables or endpoints — this doc binds existing mechanics into public promises and settles three presentation deviations.

## Purpose

State what the pricing page and account screens promise — and make each promise traceable to an enforcing spec: two tiers (not three), monthly/yearly billing with a stated annual saving, a trial, one-click cancel, and mid-term changes with exact effects. Buyers should never discover a smaller truth behind a bigger headline.

## Users

- Buyer: *As a Buyer, I want to compare two tiers, try pro, pay yearly with known savings, and cancel or change mid-term with exact effects so that no headline hides a smaller truth.* Compares tiers, tries pro, pays yearly with known savings, cancels or changes mid-term without fear.
- Marketing/support: *As a Marketing/support, I want to quote only guaranteed claims and answer "will I lose X?" from the lifecycle reference so that buyers get traceable promises.* Quotes only what this doc guarantees; answers "will I lose X?" from the lifecycle reference.
- Reviewer: *As a Reviewer, I want every pricing-page claim to resolve to an enforcement point so that each promise is traceable.* Verifies every pricing-page claim resolves to an enforcement point.

## Functional requirements

1. Two tiers, named plainly (deviation from link.me's three — Plus intentionally not copied, per the parity doc): Free (1 profile/unlimited links/tips/basic analytics + badge) and Pro (unlimited profiles, custom domain, realtime analytics, AI allowances, lower sales fees). The matrix in `monetize/plans/subscription-plans.md` is the feature truth; this page is its presentation — drift between them fails review (same CI copy-drift discipline as the fee schedule).
2. Monthly/yearly billing: both terms from the versioned catalog; yearly = single upfront charge, 12× monthly minus the configured discount. Discount value: up to 44% annual saving (answers catalog Q1 with the link.me-parity ceiling — finance ratifies the exact figure ≤44% before launch copy; the engine reads config, never this doc). Renewal reminders state the upcoming amount from the then-active version (no silent rises).
3. Trial presentation: 7-day full-pro trial recommended (answers trials-doc Q1 with the parity option — one trial per user ever, no instrument on one-shot rails, conversion nudges, lossless expiry). If trials config ships 14-day, this page states the configured length from the same config — the number is never hand-typed in copy.
4. One-click cancel: account → plan → Cancel, one confirmation (stating service runs to `period_end` + exact downgrade effects), done — no retention maze, no support ticket, no provider-side residue (Stripe `cancel_at_period_end` set in the same action; one-shot rails need no provider call). Cancel confirmation always offers export-first (profile JSON, leads CSV) before the term ends.
5. Mid-term changes (narrow, as specced — presented honestly): upgrades stack a fresh term; plan switches re-price at next renewal; no proration math in v1 (the change screen states this with the effective dates, never implying instant credit). Term switches (monthly→yearly) take effect next renewal with the yearly discount applied then.
6. Price-change honesty: catalog versioning + grandfathering (active subscriptions keep their term; renewals price from the active version with stated-amount reminders) surfaced in-UI as "your renewal price" — the buyer always sees their own next charge, not the headline price.

## Data / API touches

- No new tables or endpoints. Reads: public `GET /api/v1/plans` (active terms for pricing display), subscription status + `period_end` + next-amount for account screens, trial eligibility flag. Writes flow through existing plan/cancel/checkout endpoints (idempotent, audited).
- Copy discipline: pricing page, checkout summary, account screens, and reminder emails render amounts/discounts/trial-days from config + catalog rows. Hand-typed percentages or day-counts in copy fail the drift test.

## Acceptance criteria

- Claim-trace drill: every pricing-page sentence (tiers, savings %, trial days, cancel effect, change effects) resolves to an enforcing spec + config value; stale claim after a config change fails CI. *GWT: Given any pricing-page sentence; When traced after a config change; Then it resolves to an enforcing spec plus config value, and stale claims fail CI.*
- Cancel drill: logged-in cancel completes in two clicks (open + confirm) with service-to-term-end honored, reminders stopped, downgrade pre-scheduled losslessly; Stripe ledgers show no further invoices. *GWT: Given a logged-in subscriber; When they open and confirm cancel; Then service runs to term-end, reminders stop, downgrade is pre-scheduled losslessly, and no further invoices appear.*
- Trial drill: eligible user starts trial without payment instrument (one-shot rails), sees the true day-count from config, converts or downgrades losslessly at expiry. *GWT: Given an eligible user; When they start the trial and reach expiry; Then no instrument is required on one-shot rails, the day-count matches config, and conversion or downgrade is lossless.*
- Change drill: mid-term upgrade stacks, term-switch re-prices at renewal with stated dates; no screen implies proration. *GWT: Given a mid-term upgrade or term-switch; When the change completes; Then upgrades stack and term-switches re-price at renewal with stated dates and no proration implied.*
- Discount drill: yearly checkout math equals 12× monthly minus the configured discount to the VND; finance-ratified value ≤44%. *GWT: Given monthly pricing and the configured discount; When yearly checkout runs; Then the charge equals 12× monthly minus the configured discount to the VND with value ≤44%.*

## Out of scope

- Gate enforcement and matrix (see `monetize/plans/subscription-plans.md`); catalog versioning (see `monetize/subscription/plan-catalog.md`); trial/coupon mechanics (see `monetize/subscription/trials-promotions.md`); lifecycle/dunning (see `monetize/billing/subscription-lifecycle.md`, `monetize/subscription/recurring-billing.md`).
- Password/account/domains/concierge (sibling docs in this major); seat billing (`docs/agency/billing/seat-billing.md`).

## Open questions

- Q1: Yearly discount exact value (≤44% ceiling proposed) — finance ratification before launch?
- Q2: Trial length final (7-day parity vs 14-day generosity) — decide with first conversion data, or lock 7-day at launch?
