# Retention Behavior (adoption, lapse signals, win-back)

PLAN ref: none — new scope beyond PLAN P0–P5, flagged (see `user-behavior/buyer-journey-tracking.md` for placement). Lifecycle execution in `billing/subscription-lifecycle.md`; trial mechanics in `subscription/trials-promotions.md`.

## Purpose

Understand what happens AFTER the grant — who adopts, who quietly lapses, who cancels why, and which win-back moves actually work — so retention effort targets the savable instead of blanket-discounting the loyal. Acquisition analysis without retention analysis optimizes for buyers who leave; this doc closes the loop from first value moment to renewal or return.

## Users

- Operator/founder: sees adoption-to-renewal chains, lapse reasons, and win-back effectiveness per cohort.
- Lifecycle marketer: tunes reminder/win-back copy and timing from response data, not taste.
- Support lead: learns the top cancel reasons and fixes the underlying causes (product, confusion, price) in priority order.

## Functional requirements

1. Adoption tracking (the first-value moment): per-plan "activated" definitions (e.g. pro subscriber publishes first change / connects first integration — concrete per-plan checklists, see Q1) recorded as journey events; time-to-activated distributions by cohort; non-adopters flagged for onboarding attention before renewal risk materializes. Adoption is a product-usage fact, never a billing state — it informs messaging only.
2. Lapse-signal scoring (transparent, bounded): renewal-risk signals (adoption never reached, usage decay across the term, support-contact spike, payment-method expiry ahead on recurring rails) combined into an explainable score band (healthy|watch|at-risk) with the contributing reasons visible per account. Scores drive reminder intensity and support readiness — never gates, prices, or auto-actions against the buyer (normative ban restated here deliberately).
3. Cancel-flow analysis: structured exit-reason capture (closed list + free text, optional, skippable in one click — never a dark-pattern retention maze), reason distributions by cohort/plan/tenure, and save-offer experiments (discount, pause, plan-switch) run only through the experimentation registry with honest copy. Cancel completes in the same session regardless of answers given.
4. Win-back measurement: lapsed-buyer outreach (reminder sequence end-state, targeted offers within promo-campaign discipline) tracked as won-back / ignored / unsubscribed; effectiveness cut by cohort, offer kind, and time-since-lapse. Win-back offers never exceed the promo stacking rules and never target buyers who declined marketing consent (consent classes from the receipts doc apply).
5. Trial-to-paid behavior: trial engagement curves (activated vs dormant trials), conversion by engagement band, and dormant-trial rescue messaging — feeding trial-length/scope experiments with evidence instead of defaults.
6. Reporting rhythm: shared monthly cadence with conversion behavior (one retention section: adoption, lapse, cancel reasons, win-back ROI), plus cohort graduation reports as acquisition cohorts mature past first renewal.

## Data / API touches

- Reads journey trails, subscription rows, support cases (reason codes), and outreach engagement; writes risk bands + adopted flags to buyer-scoped analytics tables (never to gating/entitlement/fraud systems — enforced by schema separation + review gate). Computations batch-scheduled with the analytics cadence. All PG via `worker/db/*`; RLS restricts to operator/analyst roles.
- Exit-reason taxonomy versioned like segments (renames are new versions with overlap). Outreach engagement tracked through the receipts-channel tooling (no new mail infrastructure).
- Risk-band logic published internally (explainability requirement — no black-box scores anywhere near buyers).

## Acceptance criteria

- Adoption drill: seeded cohorts show correct time-to-activated distributions; non-adopters flagged pre-renewal with reason-visible bands.
- Lapse drill: decaying-usage accounts score at-risk with correct contributing reasons; scores provably unread by gating/pricing/fraud code paths (dependency + review gate).
- Cancel drill: exit reasons captured without blocking cancel (one-click skip measured); save offers run only as registered experiments with honest copy.
- Win-back drill: outreach response cut by cohort/offer/recency; consent-declined buyers provably excluded from every send.
- Trial drill: engagement-banded conversion reported; dormant-rescue messaging feeds the trial experiment backlog.

## Out of scope

- Lifecycle execution, grace, and downgrade (`billing/subscription-lifecycle.md`); trial/coupon mechanics (`subscription/`); journey event model (`user-behavior/buyer-journey-tracking.md`); conversion analysis (`user-behavior/conversion-behavior.md`).
- Reminder/send mechanics (receipts channel); support case handling (`sales/order-management.md`); P4/P5.

## Open questions

- Q1: Per-plan activation definitions — concrete checklists per plan tier (product call)?
- Q2: Risk-band visibility — internal-only forever, or buyer-visible "account health" someday (explicitly deferred either way)?
