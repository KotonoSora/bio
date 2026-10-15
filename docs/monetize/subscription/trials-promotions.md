# Trials + Promotions (evaluation access and incentive credits)

PLAN ref: P3-3 (extends the plan matrix with time-boxed and discounted entry paths; grant machinery unchanged). Catalog definitions in `subscription/plan-catalog.md`; ledger rows in `billing/entitlement-ledger.md`.

## Purpose

Define how users legitimately experience pro without paying full price: trial periods, coupon/promo codes, and referral or goodwill credits. Nothing in the current specs covers evaluation access — without this doc, trials become ad-hoc database edits by support, which is exactly how entitlement drift and audit gaps are born. Every free-day of pro flows through the same ledger, gates, and audit trail as paid time.

## Users

- Evaluator: tries pro for a bounded period with full gates open and a clear conversion path.
- Buyer with a coupon: redeems a code at checkout for a defined discount or extended term.
- Advocate/goodwill recipient: earns or receives credit (referral reward, support compensation) applied as term time, never as cash.
- Support/ops: issues goodwill within defined bounds instead of hand-editing rows.

## Functional requirements

1. Trials: time-boxed pro (`trial/active` ledger status, see 4) with full gates, no payment instrument required on one-shot rails (card rails follow the PSP trial convention where enabled). One trial per user, ever (device/account abuse signals logged); trial length from config (default 14 days, see Q1); trial users receive the standard renewal-reminder sequence reworded as conversion nudges; trial end follows the normal expiry → grace → downgrade path (lifecycle doc) with zero data loss.
2. Coupons: codes with `{code UNIQUE, kind (percent|fixed_vnd|term_extend), value, max_redemptions, per_user_limit 1, expires_at, status}`. Redeemed at checkout BEFORE provider handoff: server recomputes the amount from catalog − coupon and charges the discounted total (provider sees the final amount only). Percent caps and fixed floors prevent zero/negative charges; a fully-discounted (0 VND) order skips provider handoff entirely and grants directly through the same atomic ledger write + audit path.
3. Credits (referral/goodwill): denominated in pro-days, applied as term extension on the current or next period — never as withdrawable balance, never transferable. Referral awards vest only after the referred user holds a paid grant past the refund window (kills self-referral farming). Goodwill issuance is an audited ops action with per-case caps.
4. Ledger statuses extended: `trial` alongside `active|grace|cancelled|expired` (partial-unique-current-row rule covers it); coupon/credit provenance stamped on the subscription row (`source: paid|trial|coupon|credit`, `source_ref`) so support can always answer "why is this user pro".
5. Abuse guards: trial-once enforced at account + signal level; coupon brute-force throttled like checkout (Turnstile/rate limits, invalid-code responses indistinguishable timing-wise); referral velocity caps; all issuance/redemption in `audit_log`.
6. Conversion accounting: trial→paid, coupon→paid-renewal, and credit-burn rates flow into the metrics log (no PII) for pricing decisions — measurement only, no behavior change.

## Data / API touches

- New tables `coupons{...}` + `credits_ledger{user_id, days, source, source_ref, consumed}` with standard `user_id` + RLS; `subscriptions` gains `status=trial` + `source/source_ref` columns. Trial/consumed-days math runs in the lifecycle job's existing cadence. All PG via `worker/db/*`.
- Endpoints: `POST /api/billing/coupons/validate` (returns discounted quote, throttled), checkout accepts optional `coupon_code`, `GET /api/v1/plan` surfaces trial/credit state, audited ops endpoints for goodwill issue. No client-settable trial flags anywhere.
- 0-VND orders bypass provider minors completely (no `orderCode` on provider rails; local `orders` row with `provider='promo'`, status transitions through the canonical model).

## Acceptance criteria

- Trial drill: full gates during trial, one-trial-ever enforced across sock-puppet signals, expiry follows the lossless downgrade path, conversion nudge sequence delivered.
- Coupon drill: percent/fixed/term-extend all price correctly; brute-force throttled; 0-VND order grants without touching any provider; expired/exhausted codes rejected with indistinguishable errors.
- Credit drill: referral vests only post-refund-window; goodwill capped and audited; credits extend terms as days, never cash out.
- Ledger probe: every pro row carries a valid `source`; support can explain any account's status from ledger + audit alone.
- Auth probe: non-ops issuance endpoints → 403; users cannot self-stamp trials/credits; RLS denies cross-user rows.

## Out of scope

- Catalog versioning and term pricing (`subscription/plan-catalog.md`); recurring-charge mechanics (`subscription/recurring-billing.md`); ledger schema and lifecycle transitions (`billing/`); plan matrix and gates (`plans/`).
- Provider checkout/settlement internals (their own minors); referral marketing pages/campaigns (P4); P5.

## Open questions

- Q1: Trial length and scope — 14-day full-pro default, or shorter/reduced-scope trials?
- Q2: Coupon kinds at launch — percent-only to bound exposure, or full kind set from day one?
