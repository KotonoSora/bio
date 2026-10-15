# Promo Campaign Management (lifecycle, budgets, targeting)

PLAN ref: P3-3 (operationalizes coupon kinds defined in `subscription/trials-promotions.md`). Redemption mechanics stay there; this doc owns the campaign container around them.

## Purpose

Define how promo campaigns are created, bounded, targeted, and retired: a campaign is the budgeted, time-windowed parent of a batch of codes, and without this container, codes become untracked liabilities (unbounded discounts, unkillable leaks, no attribution). Marketing intent becomes enforceable server-side guardrails here — every code in the wild traces to exactly one campaign with a spend ceiling and an off switch.

## Users

| Role | Permissions | Notes |
|---|---|---|
| Marketing/ops | Launch a campaign (Back-to-school 20%, influencer batch, win-back), watch burn-down, pause leaks | Every code traces to exactly one campaign with a spend ceiling and an off switch |
| Finance | See committed vs redeemed exposure per campaign before approving | Budgeted, time-windowed parent of a batch of codes |
| Buyer | Only ever see an eligible code accepted or a clean rejection | Campaign internals never leak |

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Marketing/ops | Launch a campaign, watch burn-down, and pause leaks | Marketing intent becomes enforceable server-side guardrails | Must |
| US-02 | Finance | See committed vs redeemed exposure per campaign before approving | Codes never become untracked liabilities | Must |
| US-03 | Buyer | Only ever see an eligible code accepted or a clean rejection | Campaign internals never leak | Must |

## Functional requirements

1. Campaign entity `promo_campaigns{id, name, kind_scope (percent|fixed_vnd|term_extend per trials doc), value, budget_cap_vnd, max_redemptions, per_user_limit, target (new|winback|all|invite_only), starts_at, ends_at, status (draft|scheduled|live|paused|ended)}`. Launch requires budget cap + window + targeting set — unbounded campaigns cannot exist.
2. Lifecycle: draft → scheduled → live → (paused ↔ live) → ended. `paused` halts new redemptions instantly (in-flight `pending` checkouts already repriced keep their quote until expiry); `ended` is terminal. Every transition is an audited ops action with actor + reason.
3. Budget enforcement (server-side, pre-grant): each redemption decrements both `max_redemptions` and the monetary exposure accumulator atomically with the order repricing — racing redemptions past the cap is impossible by construction. At 80% burn, ops alert fires; at 100%, the campaign auto-pauses with redemptions rejected (clean "expired" UX, never a half-discounted charge).
4. Targeting: `new` (no prior paid grant), `winback` (expired/cancelled subscription, no active row), `all`, `invite_only` (code bound to specific user ids or a single-use token list). Eligibility evaluated server-side at validate + re-checked at grant (status changes between quote and pay must not slip through).
5. Attribution: every redeemed code stamps `campaign_id` on the order and subscription `source_ref`, feeding the trials doc's conversion metrics (trial→paid, coupon→renewal by campaign). No buyer PII in marketing aggregates.
6. Leak response: single control pauses the campaign, rotates nothing else (codes are random per the generation doc), and optionally voids unredeemed single-use codes in bulk with an audit entry.

## Data / API touches

- New table `promo_campaigns` (schema above) + `coupons` gains `campaign_id FK`; exposure accumulator on the campaign row (updated in the same transaction as redemption). Standard `user_id`-adjacent RLS posture (buyer-readable: own redemptions only; campaign config restricted to marketing/ops roles). All PG via `worker/db/*`.
- Endpoints: buyer `POST /api/billing/coupons/validate` extended with campaign eligibility (response unchanged on success: discounted quote; rejections stay indistinguishable); ops `POST /api/v1/ops/promo-campaigns` (create), `.../:id/launch|pause|end`, `GET /api/v1/ops/promo-campaigns/:id/burndown`. No client-settable campaign fields.
- Reminder/renewal emails never quote campaign prices beyond the campaign window — renewal always re-prices from the active catalog (see `subscription/plan-catalog.md`).

## Acceptance criteria

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Launch-guard drill: cap-less or window-less campaign rejected; unbounded codes uncreatable | A cap-less or window-less campaign | Launch is attempted | It is rejected and unbounded codes are uncreatable |
| AC-02 | Cap-race drill: concurrent redemptions at 99% burn; exactly the remaining count succeeds, rest get clean rejections, exposure never exceeds cap | Concurrent redemptions at 99% burn | Redemptions race | Exactly the remaining count succeeds, the rest get clean rejections, and exposure never exceeds cap |
| AC-03 | Pause drill: mid-campaign pause blocks new redemptions within seconds; in-flight quoted checkouts honor their quote; resume reopens cleanly | A mid-campaign pause and resume | Pause and resume run | New redemptions block within seconds, in-flight quoted checkouts honor their quote, and resume reopens cleanly |
| AC-04 | Targeting drill: win-back code on an active subscriber rejected; invite-only code on a non-invitee rejected; eligibility re-checked at grant after a status flip | A win-back code on an active subscriber and an invite-only code on a non-invitee | Validation and grant run | Both reject and eligibility is re-checked at grant after a status flip |
| AC-05 | Leak drill: pause + bulk-void stops a leaked list; audit shows actor, reason, and affected code count | A leaked code list | Pause + bulk-void runs | The list stops and audit shows actor, reason, and affected code count |
| AC-06 | Auth probe: non-ops campaign endpoints return 403; buyers cannot enumerate campaigns or codes | Non-ops campaign endpoints and buyer enumeration attempts | Access is attempted | Endpoints return 403 and buyers cannot enumerate campaigns or codes |

## Out of scope

- Code formats, bulk generation, and attribution mechanics (see `promo-code/code-generation.md`); stacking and redemption UX (see `promo-code/redemption-rules.md`).
- Coupon kinds, repricing math, and 0-VND bypass (`subscription/trials-promotions.md` — this minor extends, never restates); catalog versioning (`subscription/plan-catalog.md`); referral marketing pages (P4); P5.

## Open questions

- Q1: Campaign approval workflow — single marketing role suffices, or finance sign-off required above a cap threshold?
- Q2: Win-back definition — how long after expiry does a user count as win-back vs simply active-funnel?
