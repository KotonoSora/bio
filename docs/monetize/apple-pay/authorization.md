# Apple Pay Authorization (merchant validation + charge + grant)

PLAN ref: P3-2 adapted. Deviation flagged: PLAN specifies a PayOS webhook; this feature validates the merchant session server-side, then grants on the PSP-confirmed charge of the Apple Pay token (synchronous path) with the PSP's server webhook as confirmation/audit path. Session config/token intake in `monetize/apple-pay/checkout.md`; pending/review repair in `monetize/apple-pay/pending-refund.md`.

## Purpose

Define how the platform trusts Apple Pay money: every sheet runs on a server-fetched, single-use merchant session (minutes TTL), and only the PSP's confirmed charge (amount-bound, order-bound, exactly-once) creates the entitlement. Forged tokens, session replay, wrong amounts, and already-paid races can never create a double grant or a free upgrade.

## Users

- Buyer: authenticates in the validated sheet; receives entitlement on charge-confirmed grant.
- Apple servers: issue single-use merchant sessions to our validated server identity.
- PSP server: confirms charges synchronously and posts asynchronous webhooks (captures, reviews, failures).
- Ops: relies on idempotency during retries, duplicate webhooks, and incident recovery.

## Functional requirements

1. Merchant validation endpoint `POST /api/billing/applepay/validate-merchant` (authenticated, order-owner scoped): body `{orderCode, validationURL}`. Server calls Apple's servers with the Merchant Identity Certificate (TLS client identity), passing the Apple-supplied `validationURL` and our merchant/session details untouched. Returns Apple's opaque merchant session to the client, which completes it via `completeMerchantValidation`. Validation is per-transaction (a fresh session per sheet), server-side only, and fast — sessions expire within minutes, so slow or client-side validation fails closed.
2. Charge execution (server-side, in `POST /api/billing/applepay/authorize`): after intake validation (owner + `pending` + amount binding + `transactionIdentifier` freshness — reject reused identifiers), call the PSP adapter `charge(token, amount_vnd, orderCode, idempotency_key = orderCode)`. Mark the order `authorizing` for the call duration (serialized per order). PSP-confirmed capture → atomic `authorizing → paid` + `psp_txn_id` write + entitlement write + `audit_log{actor, action, target, at}` append, in one transaction; the authorize response carries the verdict the client passes to `completePayment(SUCCESS/FAILURE)`.
3. Idempotency on every path: the `orderCode`-keyed idempotency key goes to the PSP (safe retry of the charge call), and the local `pending|authorizing → paid` guarded transition plus `psp_txn_id UNIQUE` make duplicate tokens, retried requests, and redelivered webhooks durable no-ops. Replays of a paid `orderCode` return success with zero side effects.
4. Amount binding: charged amount must equal stored `amount_vnd`; any PSP-reported divergence → `completePayment(FAILURE)` + alert, no grant. Unknown `orderCode` → failure without disclosing valid codes.
5. PSP webhook handler (public, WAF + strict rate limits, no session, HTTPS only): verifies per the PSP's scheme (signature/HMAC per that provider's docs — never trust bare POSTs), then applies only terminal confirmations through the same guarded transition; non-terminal events (review/pending) route to the settlement doc's states. Unknown refs → logged rejection, no leak.
6. Failure handling: PSP-declined/failed charges → order `failed` + `completePayment(FAILURE)` with a buyer-safe reason (no raw gateway codes leaked); transport/timeout errors → order back to `pending`/`authorizing`-expired for the settlement sweep to resolve (never assume paid, never assume failed — verify via status query).
7. Deployment hygiene: validation + webhook URLs and verified domains re-confirmed per environment after every deploy or certificate renewal that touches them; TEST-vs-production credentials strictly separated; local reproduction via public tunnel (`cloudflared tunnel → wrangler dev`) against the PSP sandbox + Apple sandbox/test cards.

## Data / API touches

- Mutates `orders.status` (`pending|authorizing → paid|failed|cancelled|expired`) keyed by `orderCode`, recording `psp_txn_id UNIQUE` (and `transactionIdentifier` for duplicate detection). Plan grants write the subscription/entitlement row; product grants write the buyer-claim row for the shop major. All writes server-side via `worker/db/*`; validation/charge/webhook paths resolve the owner from the stored order, never from request identity; RLS preserved.
- Cryptography: Merchant Identity Certificate (TLS client auth to Apple) + PSP webhook verification per that provider's scheme, both via platform-safe primitives (WebCrypto/subtle; cert handling per Workers secret-store constraints confirmed at implementation). Keys/certs from secret storage only — never repo, never logs.
- Observability: structured logs per validation/authorize/webhook (orderCode, psp_txn_id, result, latency) with tokens/signatures/keys redacted; alert on consecutive Apple-validation failures, PSP verification failures, or a spike in already-paid hits.

## Acceptance criteria

- Sandbox end-to-end (Apple sandbox/test cards + PSP sandbox): checkout → validated sheet → token → authorize → `paid` + entitlement within the SLA; `completePayment` verdict matches the grant outcome.
- Validation failure drill (bad cert/expired session): sheet fails closed before any payment UI confusion; no charge attempted.
- Replay test: same token/authorize posted twice + redelivered PSP webhook → single transition, single entitlement, single audit entry; repeats are no-ops. Reused `transactionIdentifier` across orders → rejected.
- Tamper tests: forged/malformed token → failure, no grant; altered amount → rejected pre-charge; unknown `orderCode` → no leak.
- Timeout drill (PSP never answers): order recoverable via settlement status query; exactly-once charge preserved by the idempotency key.
- Burst of bogus webhooks throttled by WAF/rate rules; valid traffic unaffected.

## Out of scope

- Session config, gating, and token intake (see `monetize/apple-pay/checkout.md`); pending/3DS/review states, status sweep, refunds, chargebacks (see `monetize/apple-pay/pending-refund.md`).
- Plan matrix/AI caps, agency skeleton (`docs/monetize/` — shared as-is); shop delivery; P4/P5. All other provider paths untouched.

## Open questions

- Q1: Synchronous-grant SLA vs webhook-confirmation lag — does the buyer see "paid" on charge response, or "confirming" until webhook/status check (shared with the plans SLA question)?
- Q2: Certificate storage + renewal mechanics on Workers (secret-store fit, rotation runbook) — design now or at implementation?
