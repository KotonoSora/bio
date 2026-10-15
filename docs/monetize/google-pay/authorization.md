# Google Pay Authorization (charge + idempotent grant)

PLAN ref: P3-2 adapted. Deviation flagged: PLAN specifies a PayOS webhook; this feature grants on the PSP-confirmed charge of the Google Pay token (synchronous path) with the PSP's server webhook as confirmation/audit path. Order/token intake in `monetize/google-pay/checkout.md`; pending/review repair in `monetize/google-pay/pending-refund.md`.

## Purpose

Define how the platform trusts Google Pay money: the token alone authorizes nothing — only the PSP's confirmed charge (amount-bound, order-bound, exactly-once) creates the entitlement. Forged tokens, replays, wrong amounts, and already-paid races can never create a double grant or a free upgrade.

## Users

- Buyer: authorized in the sheet; receives entitlement on charge-confirmed grant.
- PSP server: confirms charges synchronously and posts asynchronous webhooks (captures, reviews, failures).
- Ops: relies on idempotency during retries, duplicate webhooks, and incident recovery.

## Functional requirements

1. Charge execution (server-side, in `POST /api/billing/googlepay/authorize`): after intake validation (owner + `pending` + amount binding), call the PSP adapter `charge(token, amount_vnd, orderCode, idempotency_key = orderCode)`. Mark the order `authorizing` for the call duration (serialized per order). PSP-confirmed capture → atomic `authorizing → paid` + `psp_txn_id` write + entitlement write + `audit_log{actor, action, target, at}` append, in one transaction.
2. Idempotency on every path: the `orderCode`-keyed idempotency key goes to the PSP (safe retry of the charge call), and the local `pending|authorizing → paid` guarded transition plus `psp_txn_id UNIQUE` make duplicate tokens, retried requests, and redelivered webhooks durable no-ops. Replays of a paid `orderCode` return success with zero side effects.
3. Amount binding: charged amount must equal stored `amount_vnd`; any PSP-reported divergence → no grant + alert. Unknown `orderCode` → failure without disclosing valid codes.
4. PSP webhook handler (public, WAF + strict rate limits, no session, HTTPS only): verifies per the PSP's scheme (signature/HMAC per that provider's docs — never trust bare POSTs), then applies only terminal confirmations through the same guarded transition; non-terminal events (review/pending) route to the settlement doc's states. Unknown refs → logged rejection, no leak.
5. Failure handling: PSP-declined/failed charges → order `failed` with a buyer-safe reason (no raw gateway codes leaked); transport/timeout errors → order back to `pending`/`authorizing`-expired for the settlement sweep to resolve (never assume paid, never assume failed — verify via status query).
6. Deployment hygiene: PSP webhook URL registered/confirmed in the provider dashboard after every deploy that changes it; TEST-vs-production credentials strictly separated; local reproduction via public tunnel (`cloudflared tunnel → wrangler dev`) against the PSP sandbox + Google `TEST` wallet with sample tokens.

## Data / API touches

- Mutates `orders.status` (`pending|authorizing → paid|failed|cancelled|expired`) keyed by `orderCode`, recording `psp_txn_id UNIQUE`. Plan grants write the subscription/entitlement row; product grants write the buyer-claim row for the shop major. All writes server-side via `worker/db/*`; charge/webhook paths resolve the owner from the stored order, never from request identity; RLS preserved.
- Cryptography: PSP webhook verification per that provider's scheme via WebCrypto; Google sheet payloads carry no secret to verify (token authenticity is established by the PSP accepting the token). Keys from `wrangler secret` only.
- Observability: structured logs per authorize/webhook (orderCode, psp_txn_id, result, latency) with tokens/signatures/keys redacted; alert on consecutive verification failures or a spike in already-paid hits.

## Acceptance criteria

- Sandbox end-to-end (Google `TEST` + PSP sandbox): checkout → sheet → token → authorize → `paid` + entitlement within the SLA.
- Replay test: same token/authorize posted twice + redelivered PSP webhook → single transition, single entitlement, single audit entry; repeats are no-ops.
- Tamper tests: forged/malformed token → failure, no PSP charge attempted... or PSP-declined with no grant; altered amount → rejected pre-charge; unknown `orderCode` → no leak.
- Timeout drill (PSP never answers): order recoverable via settlement status query; exactly-once charge preserved by the idempotency key (no double capture on retry).
- Burst of bogus webhooks throttled by WAF/rate rules; valid traffic unaffected.

## Out of scope

- Session config and token intake (see `monetize/google-pay/checkout.md`); pending/3DS/review states, status sweep, refunds, chargebacks (see `monetize/google-pay/pending-refund.md`).
- Plan matrix/AI caps, agency skeleton (`docs/monetize/` — shared as-is); shop delivery; P4/P5. PayOS, SePay, VNPay, ZaloPay, and NAPAS paths untouched.

## Open questions

- Q1: Synchronous-grant SLA vs webhook-confirmation lag — does the buyer see "paid" on charge response, or "confirming" until webhook/status check (shared with the plans SLA question)?
- Q2: `onPaymentAuthorized` server pre-check — enforced for all checkouts, or only above an amount threshold?
