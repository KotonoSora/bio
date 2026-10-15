# Stripe Checkout + Orders (PaymentIntent initiation)

PLAN ref: P3-1 adapted. Deviation flagged: PLAN specifies PayOS links (and AGENTS.md says no Stripe-for-VND unless asked — asked here); this feature collects via Stripe PaymentIntents per explicit request. Same `orders` table (plus `provider` column) and same plan/product flow. Webhook grant in `monetize/stripe/webhook.md`; pending/refund/dispute handling in `monetize/stripe/pending-refund.md`.

## Purpose

Define how money starts moving via Stripe: an authenticated checkout creates a tracked `orders` row with a unique numeric `orderCode`, then creates a Stripe PaymentIntent (exact VND amount, zero-decimal, order-bound metadata, idempotent) and hands the client its `client_secret`. The buyer pays in-page — card via Payment Element, wallets via the Express Checkout / Payment Request Button (Apple Pay + Google Pay through Stripe, unifying both wallet minors) — with 3DS handled by Stripe.js. The webhook later confirms the grant.

## Users

- Buyer (logged-in owner for plans; buyer-email claim for products per shop major): *As a Buyer, I want to start checkout, pay in-page (card or wallet), see confirmation or a pending state so that money starts moving via Stripe as defined here.* starts checkout, pays in-page (card or wallet), sees confirmation or a pending state.
- Creator/seller: *As a Creator/seller, I want to receive entitlement on webhook-confirmed grant so that money starts moving via Stripe as defined here.* receives entitlement on webhook-confirmed grant.
- Support/ops: *As a Support/ops, I want to look up an order by `orderCode` / PaymentIntent id to reconcile disputes and chargebacks so that money starts moving via Stripe as defined here.* looks up an order by `orderCode` / PaymentIntent id to reconcile disputes and chargebacks.

## Functional requirements

1. `POST /api/billing/checkout` (authenticated, Turnstile + per-IP/email rate limits): body `{provider: "stripe", plan? | product_id?, buyer_email?}`. Server re-prices from canonical plan/product tables — client-supplied amounts are never trusted. Response: `{orderCode, amount_vnd, client_secret, publishable_key}`. Secret keys never leave the server; only the publishable key + `client_secret` reach the client.
2. `orderCode` generation: numeric int64 unique (`Date.now()*1000 + rand`), stored on `orders{id, user_id, provider: "stripe", plan?, product_id?, amount_vnd, status, stripe_pi_id UNIQUE?}`. Integer VND, no conversion math — VND is a Stripe zero-decimal currency, so `amount_vnd` passes through as-is with `currency: "vnd"`. Presentment-vs-settlement split (settlement currency follows the Stripe account's country) is finance-visible, never buyer-visible.
3. PaymentIntent creation (server-side, TEST vs LIVE keys by env): `amount = amount_vnd`, `currency = "vnd"`, `metadata: {orderCode, user_id}`, `description` embedding orderCode, idempotency key = `orderCode` (safe retry of creation), `return_url` same-origin under `/app/billing/return` for redirect-based methods. Client confirms with Stripe.js; wallets render through Stripe's checkout elements (Apple Pay domain registration done in the Stripe dashboard, not via our `/.well-known/` file).
4. Secrets (`STRIPE_SECRET_KEY` restricted least-privilege, `STRIPE_PUBLISHABLE_KEY`, `STRIPE_WEBHOOK_SECRET whsec_…`) via `wrangler secret` / `.dev.vars` locally — never committed, never logged, secret key never in the client bundle.
5. Buyer-email claim for course/digital products: binds `buyer_email` (also set as the PaymentIntent receipt email) so the shop major issues the R2 signed download / lesson claim after the paid grant (delivery lives in the shop major).
6. Plan matrix and AI credits unchanged — see `docs/monetize/plans/subscription-plans.md`.

## Data / API touches

- `orders` gains `provider TEXT NOT NULL DEFAULT 'payos'` + `stripe_pi_id UNIQUE?` + `stripe_event_ids?` (or a `stripe_events{event_id UNIQUE}` table — implementation choice, same guarantee), keeping `user_id TEXT NOT NULL` + index + RLS. `status` ∈ `pending|authorizing|paid|cancelled|expired|failed|refunded|disputed`. Dashboard reads scope `WHERE user_id = session.userId`.
- Endpoints (JSON over `/api/*`, cookie session): `POST /api/billing/checkout`, `POST /api/billing/stripe/confirm-intent` (optional status-sync helper — never grants), `GET /api/billing/orders`, `GET /api/billing/orders/:orderCode` (buyer polls this), `POST /api/billing/orders/:orderCode/cancel` (cancels the PaymentIntent while still cancellable). No direct DB from client; all PG via `worker/db/*`, least-privilege app role. Stripe API calls server-side only.
- Retry/double-click while pending: single row, same `orderCode`; PaymentIntent creation idempotent on the key — one live intent per order.

## Acceptance criteria

- TEST-mode checkout for plan and for product (Stripe test cards, incl. 3DS challenge cards): PaymentIntent exact to the VND, `orders` row pending with unique `orderCode` in metadata, in-page confirmation works end-to-end.
  *GWT: Given the preconditions for this check hold; When tEST-mode checkout for plan and for product (Stripe test cards, incl. 3DS challenge cards): PaymentIntent exact to the VND, `orders` row pending with unique `orderCode` in metadata, in-page confirmation works end-to-end; Then the expected outcome stated here is observed.*
- Amount tamper (client sends lower amount): server re-prices to canonical; mismatch logged.
  *GWT: Given Amount tamper (client sends lower amount) context; When server re-prices to canonical; Then mismatch logged.*
- Wallet buttons (Apple Pay + Google Pay via Stripe elements): render on capable devices, tokenize through Stripe, settle into the same grant path — one flow, not three.
  *GWT: Given Wallet buttons (Apple Pay + Google Pay via Stripe elements) context; When the checked action occurs; Then render on capable devices, tokenize through Stripe, settle into the same grant path — one flow, not three.*
- Cancel while cancellable: PaymentIntent canceled, order `cancelled`, late webhooks rejected with no grant.
  *GWT: Given Cancel while cancellable context; When the checked action occurs; Then PaymentIntent canceled, order `cancelled`, late webhooks rejected with no grant.*
- IDOR probe: user A operating on user B orders returns 403; RLS denies cross-user rows at DB.
  *GWT: Given IDOR probe context; When user A operating on user B orders returns 403; Then RLS denies cross-user rows at DB.*
- Secrets probe: no secret/restricted keys or `whsec_` in repo, logs, or client bundle (grep gate).
  *GWT: Given Secrets probe context; When the checked action occurs; Then no secret/restricted keys or `whsec_` in repo, logs, or client bundle (grep gate).*

## Out of scope

- Webhook verification and grant (see `monetize/stripe/webhook.md`); pending/3DS states, status sweep, refunds, disputes (see `monetize/stripe/pending-refund.md`).
- Plan matrix, AI caps, agency skeleton (`docs/monetize/` — provider-agnostic, shared as-is); shop delivery; receipt emails beyond Stripe's native receipts (Resend campaigns stay P4); P5 scale-out. All other provider paths untouched; multi-provider checkout UI is not in this feature.

## Open questions

- Q1 (gating): Stripe account-country eligibility and VND presentment for our account type — confirmed before build, or does entity setup come first?
- Q2: Guest checkout for products (buyer-email only) in v1, or login required for all purchases?
