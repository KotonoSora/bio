# Recurring Billing (native subscriptions where rails allow)

PLAN ref: P3-3 (decides the lifecycle doc's "Stripe may use native subscriptions" line). One-shot rails stay manual-renewal; this doc adds the native-recurring path without forking the buyer's plan experience.

## Purpose

Define how native recurring charges work on rails that support them (Stripe subscriptions today; any future rail with stored-instrument rebilling later): subscription creation at first grant, invoice-driven renewals feeding the same ledger, card-failure recovery, and cancel-at-period-end — all converging on the identical `subscriptions` rows, gates, reminders, and downgrade behavior as one-shot renewals. The buyer must never be able to tell which rail bills them.

## Users

| Role | Permissions | Notes |
|---|---|---|
| Pro subscriber on a recurring-capable rail | Subscribe once, get invoiced per term, update payment method on failure, cancel cleanly | Plan experience matches one-shot renewals |
| Lapsed recurring payer | Receive smart-retry + recovery messaging instead of instant downgrade | Restores good standing before grace starts |
| Support/ops | Cancel at provider and ledger together; retry or void invoices within defined bounds | No half-cancel ever occurs |

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Pro subscriber on a recurring-capable rail | Subscribe once, get invoiced per term, update payment method on failure, and cancel cleanly | The plan experience matches one-shot renewals | Must |
| US-02 | Lapsed recurring payer | Get smart-retry + recovery messaging instead of instant downgrade | I can restore good standing before grace starts | Must |
| US-03 | Support/ops | Cancel at provider and ledger together and retry or void invoices within defined bounds | No half-cancel ever occurs | Must |

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

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Recurring drill (sandbox): subscribe to renewal invoice extending ledger exactly one term to card-failure triggering retries + banner (no instant downgrade) to recovery payment restoring good standing to cancel running service to term end with zero further invoices | A sandbox recurring subscription | Subscribe, renewal invoice, card-failure, recovery payment, and cancel run | Renewal extends the ledger exactly one term, failure triggers retries + banner, recovery restores good standing, and cancel runs service to term end with zero further invoices |
| AC-02 | One-shot parity drill: same buyer journey on a bank rail shows identical plan states, banners, and downgrade behavior with rail-indistinguishable UX | The same buyer journey on a bank rail | Plan states, banners, and downgrade are compared | They are identical with rail-indistinguishable UX |
| AC-03 | Provider-outage drill: failed provider calls during cancel/retry surface errors with ledger untouched (no half-cancels); reconciliation sweep converges afterward | Failed provider calls during cancel/retry | Cancel/retry is attempted | Errors surface with ledger untouched and the reconciliation sweep converges afterward |
| AC-04 | Amount-surprise drill: renewal invoice differing from catalog is held + alerted, never auto-accepted | A renewal invoice differing from catalog | Renewal is processed | It is held + alerted and never auto-accepted |
| AC-05 | Auth probe: cross-user subscription operations return 403; RLS denies cross-user rows | Cross-user subscription operations | The operation is attempted | The API returns 403 and RLS denies cross-user rows |

## Out of scope

- Catalog versioning and term pricing (`subscription/plan-catalog.md`); trials/coupons/credits (`subscription/trials-promotions.md`); generic lifecycle, grace, and downgrade execution (`billing/subscription-lifecycle.md`); ledger schema (`billing/entitlement-ledger.md`).
- Provider charge/webhook mechanics (their own minors, incl. `monetize/stripe/`); proration math (future spec); P4/P5.

## Open questions

- Q1: Dunning bounds — retry count/window and past-due banner copy before grace starts?
- Q2: Provider-switch mid-subscription (recurring Stripe → one-shot rail) — supported at renewal, or cancel-and-rebuy only?
