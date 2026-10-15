# Recurring Billing (native subscriptions where rails allow)

PLAN ref: P3-3 (decides the lifecycle doc's "Stripe may use native subscriptions" line). One-shot rails stay manual-renewal; this doc adds the native-recurring path without forking the buyer's plan experience.

## Purpose

Define how native recurring charges work on rails that support them (Stripe subscriptions today; any future rail with stored-instrument rebilling later): subscription creation at first grant, invoice-driven renewals feeding the same ledger, card-failure recovery, and cancel-at-period-end — all converging on the identical `subscriptions` rows, gates, reminders, and downgrade behavior as one-shot renewals. The buyer must never be able to tell which rail bills them.

## Users

- Pro subscriber on a recurring-capable rail: subscribes once, gets invoiced per term, updates payment method on failure, cancels cleanly.
- Lapsed recurring payer: gets smart-retry + recovery messaging instead of instant downgrade.
- Support/ops: cancels at provider and ledger together; retries or voids invoices within defined bounds.

## Functional requirements

1. Eligibility: native recurring is enabled per provider config — Stripe (`provider='stripe'`) in v1; all bank-transfer/wallet-redirect rails (PayOS, SePay, VNPay, ZaloPay, NAPAS) stay explicit re-purchase per the lifecycle doc. Checkout offers recurring only when the chosen provider supports it; the order row stamps `billing_mode: one_shot|recurring`.
2. Creation at first grant: after the initial charge confirms (same atomic grant as one-shot), the server creates the provider subscription (Stripe: Customer + Subscription, `metadata: {orderCode, user_id}`, term-aligned billing interval, collection method charge-automatically) and stores `provider_subscription_id` on the ledger row. The first period is the already-paid term — the provider's next invoice defines the renewal date, which the ledger mirrors.
3. Renewal path: provider `invoice.payment_succeeded` (or equivalent) extends the current ledger row's `period_end` by one term through the same idempotent, amount-checked transition family as grants (unexpected amounts → hold + alert, never silent accept). `invoice.payment_failed` does NOT downgrade immediately — it starts the recovery sequence (4). Ledger rows remain the visible truth; provider objects are the charge mechanism.
4. Recovery (dunning): failed recurring payments enter provider smart-retries (bounded count/window per config) while the ledger row stays `active` with a buyer-visible past-due banner + update-payment-method flow; only after retries exhaust does the normal grace → expiry → lossless-downgrade path run. Every retry, reminder, and card-update event is logged; buyers can also switch to any one-shot rail mid-recovery (fresh checkout, period stacking per lifecycle rules).
5. Cancel: buyer cancel sets provider `cancel_at_period_end` AND ledger `cancelled_at` together (single ops action, atomic intent — provider call failure surfaces as an error, never as a half-cancel). Service runs to `period_end`; no further invoices; downgrade executes per the lifecycle doc.
6. Mid-term changes in v1 are intentionally narrow: upgrades from a recurring base create a fresh one-shot term (stacking rule), plan switches re-price at next renewal — no proration math until a dedicated proration spec exists.

## Data / API touches

- `subscriptions` gains `billing_mode` + `provider_subscription_id?`; `orders` gains `billing_mode` for the originating purchase. Invoice/dunning events land in audit + metrics; no new buyer-facing tables. All PG via `worker/db/*`; provider subscription APIs called server-side only (keys in `wrangler secret`).
- Endpoints: `POST /api/v1/plan/cancel` (now provider-aware: one-shot ledgers vs recurring cancel-at-period-end), `POST /api/v1/plan/payment-method` (provider update flow launcher), ops `POST /api/v1/ops/subscriptions/:id/cancel|retry-invoice`. Webhook handlers for invoice/dispute events extend the provider's webhook doc, not this one.
- Reminder content reuses the receipts channel; past-due banners reuse the pending-page UX patterns.

## Acceptance criteria

- Recurring drill (sandbox): subscribe → renewal invoice extends ledger exactly one term → card-failure triggers retries + banner (no instant downgrade) → recovery payment restores good standing → cancel runs service to term end with zero further invoices.
- One-shot parity drill: same buyer journey on a bank rail shows identical plan states, banners, and downgrade behavior — rail-indistinguishable UX.
- Provider-outage drill: failed provider calls during cancel/retry surface errors with ledger untouched (no half-cancels); reconciliation sweep converges afterward.
- Amount-surprise drill: renewal invoice differing from catalog → held + alerted, never auto-accepted.
- Auth probe: cross-user subscription operations → 403; RLS denies cross-user rows.

## Out of scope

- Catalog versioning and term pricing (`subscription/plan-catalog.md`); trials/coupons/credits (`subscription/trials-promotions.md`); generic lifecycle, grace, and downgrade execution (`billing/subscription-lifecycle.md`); ledger schema (`billing/entitlement-ledger.md`).
- Provider charge/webhook mechanics (their own minors, incl. `monetize/stripe/`); proration math (future spec); P4/P5.

## Open questions

- Q1: Dunning bounds — retry count/window and past-due banner copy before grace starts?
- Q2: Provider-switch mid-subscription (recurring Stripe → one-shot rail) — supported at renewal, or cancel-and-rebuy only?
