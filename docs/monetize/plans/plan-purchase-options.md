# Plan Purchase Options (buy pro on any rail, no default)

PLAN ref: P3-1 (checkout) + P3-2 (webhook grant) + P3-3 (plan matrix). DEVIATION FLAGGED: PLAN's PayOS-default reading is not implemented — every plan term is purchasable on all eight specified rails with explicit `provider` and no fallback. Matrix in `plans/subscription-plans.md`; catalog in `subscription/plan-catalog.md`; registry in `checkout/provider-selection.md`; session lifecycle in `checkout/checkout-session.md`; ledger in `billing/entitlement-ledger.md`.

## Purpose

Define how a plan is bought without privileging any rail: the same pro monthly/yearly terms quoted and charged identically on payos, sepay, vnpay, zalopay, napas, googlepay, applepay, and stripe, with the buyer always choosing explicitly. Plan purchase is a thin intent over the shared checkout session — this doc owns the plan × provider matrix, and nothing else builds provider links for plans.

## Users

| Role | Permissions | Notes |
|---|---|---|
| Buyer | Pick a term (monthly/yearly), pick any eligible rail, pay, receive pro | Same terms quoted and charged identically on every rail |
| Frontend plan page | Quote every eligible rail for the chosen term, send chosen `provider` explicitly | Buyer always chooses explicitly with no fallback |
| Support/ops | Explain per-rail differences (handoff kind, renewal shape, refund path) from one matrix | Avoids eight consoles |

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Buyer | Pick a term (monthly/yearly), pick any eligible rail, pay, and receive pro | The same terms are quoted and charged identically on every rail | Must |
| US-02 | Frontend plan page | Quote every eligible rail for the chosen term and send the chosen `provider` explicitly | The buyer always chooses explicitly with no fallback | Must |
| US-03 | Support/ops | Explain per-rail differences (handoff kind, renewal shape, refund path) from one matrix | I avoid eight consoles | Must |

## Functional requirements

1. Provider set (closed, eight): `payos`, `sepay`, `vnpay`, `zalopay`, `napas`, `googlepay`, `applepay`, `stripe` — each per its specified checkout minor. All eight are offered for plans unless removed by eligibility config, capability filter, or kill switch. Omitted `provider` on plan checkout → 422 with the eligible list; unknown code → 422. No silent fallback, including to PayOS.
2. Term × provider matrix: pro monthly and yearly (per `subscription/plan-catalog.md` active rows) are both purchasable on every one-shot rail as a single charge (`period_end` +1/+12 months). Stripe native recurring is an optional renewal shape where enabled (auto-charge with `cancel_at_period_end`); all other rails renew by explicit re-purchase. Buyer experience (entitlement, grace, downgrade) is identical regardless of rail per `billing/subscription-lifecycle.md`.
3. Price identity: quote and create re-price server-side from the active `plan_terms` row minus validated coupon/credit (see `subscription/trials-promotions.md`); client amounts ignored. VND integers throughout (VNPay ×100 normalized at the edge). Fully-discounted (0 VND) plan orders skip provider handoff and grant directly through the atomic ledger path with `provider='promo'`.
4. Purchase flow (delegated, not duplicated): quote via `POST /api/billing/quote {plan, term, provider?}`, create via `POST /api/billing/checkout {provider, plan, term, coupon_code?}` with `Idempotency-Key`, handoff via the member's `next` shape, return to the one pending page, grant via that member's webhook/callback/IPN → ledger. Plan code never builds provider URLs/QRs/sheets/intents directly.
5. Renewal and cancel per rail kind: one-shot rails (all except Stripe-native) — renewal is a fresh explicit checkout; cancel stops reminders and pre-schedules lossless downgrade with no provider call. Stripe-native (where enabled) — cancel also cancels at the PSP; no further invoices. Refunds follow the originating member's settlement doc and never change the no-default rule.
6. Receipts and history: every plan grant/refund on every rail emits the uniform receipt (`billing/invoices-receipts.md`: `RCPT-<orderCode>`, amount, provider name + reference, no PAN data) by email and dashboard history.
7. Degradation: a killed/disabled rail disappears from plan options within seconds; its in-flight plan pendings follow its settlement path with buyer messaging. Display order follows ops config (or API order verbatim when config is absent) — never a code-level provider-first constant.

## Data / API touches

- No new tables. Reads: `plans` + active `plan_terms` rows, provider registry + eligibility config + kill switches, coupon/credit state. Writes: `orders{user_id TEXT NOT NULL + index + RLS, provider, plan, plan_term_version, amount_vnd INT, status, orderCode UNIQUE numeric int64, expires_at}` then member grant → `subscriptions` ledger row stamped with `provider + order_id`. All PG via `worker/db/*`, least-privilege app role.
- Endpoints: `POST /api/billing/quote`, `POST /api/billing/checkout` (explicit `provider` enforced), `GET /api/billing/payment-methods` (plan-scoped eligible rails + hints), `GET /api/billing/orders/:orderCode`, `POST /api/billing/orders/:orderCode/cancel`, `GET /api/v1/plan` (entitlement view). Member callbacks keep their minor URLs.
- Secrets: per-member PSP keys via `wrangler secret` (`.dev.vars` locally; never committed/logged/bundled); `.gitignore` guards `.wrangler/`, `.dev.vars*`. Rate/abuse state in KV `RATE_LIMIT` (Turnstile + per-IP/email buckets on quote/checkout).

## Acceptance criteria

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Eight-rail plan matrix drill: pro monthly x 8 + pro yearly x 8 quote/create/grant paths verified via API; identical amounts per term across rails (modulo documented rail fees, if any), identical ledger entitlements and gate effects | Pro monthly x 8 + pro yearly x 8 quote/create/grant paths | Each path is verified via API | Amounts are identical per term across rails with identical ledger entitlements and gate effects |
| AC-02 | No-default drill: omitted `provider` on plan checkout returns 422 with eligible list; unknown code returns 422; grep gate finds no hardcoded provider default or fallback ordering in plan/checkout code | A plan checkout with omitted or unknown `provider` | Checkout is created and the grep gate scans plan/checkout code | Omitted `provider` returns 422 with the eligible list, unknown codes return 422, and the grep gate finds no hardcoded default or fallback ordering |
| AC-03 | Quote-charged-ledger identity: quoted amount equals charged `amount_vnd` equals receipt amount to the VND on every plan x term x rail cell; tampered client amounts re-priced and logged | Every plan x term x rail cell | Quote, charge, and receipt are compared including with tampered client amounts | Quoted amount equals charged `amount_vnd` equals receipt amount to the VND with tamper re-priced and logged |
| AC-04 | Grant-visibility drill: a paid plan grant on each rail is readable at `GET /api/v1/plan` and passes every gate (domain, realtime, templates, AI) within the SLA; 0-VND promo plan grants without touching any rail | A paid plan grant on each rail and a 0-VND promo plan grant | `GET /api/v1/plan` and every gate are checked | Each rail grant is readable and passes every gate within the SLA and the 0-VND grant completes without touching any rail |
| AC-05 | Renewal/cancel drill: one-shot rails renew by fresh checkout with early-renewal extension from `period_end`; Stripe-native (where enabled) cancels at PSP with no stray invoices; downgrade is lossless on every rail | Renewal/cancel on one-shot rails and Stripe-native where enabled | Renewal and cancel run | One-shot rails renew by fresh checkout extending from `period_end`, Stripe-native cancels at PSP with no stray invoices, and downgrade is lossless on every rail |
| AC-06 | Baseline: IDOR 403 cross-user with RLS deny (S-1/S-6); plan/quote/error rendering XSS-stripped with CSP holding (S-2); anonymous quote/checkout Turnstile + WAF throttled (S-5); secrets grep clean with quarterly rotation (P5-4) | Cross-user access, rendered plan/quote/error output, anonymous quote/checkout, and repo under secrets grep | IDOR, XSS, abuse, and secrets probes run | Cross-user access returns 403 with RLS deny (S-1/S-6), rendering is XSS-stripped with CSP holding (S-2), anonymous traffic is Turnstile + WAF throttled (S-5), and secrets grep stays clean with quarterly rotation |

## Out of scope

- Plan matrix, gates, and AI caps (see `plans/subscription-plans.md`); catalog versioning (see `subscription/plan-catalog.md`); trials/coupons/credits detail (see `subscription/trials-promotions.md`, `promo-code/`).
- Session quote/create/expire/return internals (see `checkout/checkout-session.md`); selection/eligibility/display-order mechanics (see `checkout/provider-selection.md`, `payment/checkout-orchestration.md`); transitions (see `payment/order-status-model.md`); probe battery (see `payment/payment-security.md`).
- Ledger/lifecycle/receipts internals (`billing/`); member handoff/verify/settle mechanics (each member minor); P4/P5.

## Open questions

- Q1: Stripe-native recurring for plans — enable at launch (non-VND/international) or one-shot-only everywhere in v1?
- Q2: Yearly on wallet rails (Google Pay/Apple Pay) — offer identically, or constrain high-ticket yearly to redirect rails?
- Q3: Renewal reminders per rail — identical copy for all eight, or rail-specific instructions (e.g. re-purchase vs auto-charge)?
