# VNPay Return + IPN (verification + idempotent grant)

PLAN ref: P3-2 adapted. Deviation flagged: PLAN specifies a PayOS webhook; this feature verifies VNPay's browser return plus its server-to-server IPN call. Order creation in `monetize/vnpay/checkout-orders.md`; queryDr/repair in `monetize/vnpay/query-refund.md`.

## Purpose

Define how the platform trusts VNPay: the browser return is verified and used for buyer UX only, while the IPN (Instant Payment Notification) call is verified, order-checked, amount-checked, and applied exactly once as the entitlement grant. Forgery, replay, wrong amounts, and already-paid races can never create a double grant or a free upgrade.

## Users

- Buyer: lands on `/app/billing/return`, sees success/pending/failure, polls order status.
- VNPay server: calls the registered IPN URL server-to-server on every completed payment.
- Ops: replays or re-triggers IPN during incident recovery without double-granting.

## Functional requirements

1. Return handler `GET /app/billing/return` (same-origin allowlist): re-sorts params (excluding `vnp_SecureHash`), recomputes HMAC-SHA512 with `VNPAY_HASH_SECRET` via WebCrypto (Workers-safe), constant-time compare. Bad signature → failure UX, nothing written. Valid signature + `vnp_ResponseCode=00` → "payment received, confirming" UX backed by polling `GET /api/billing/orders/:orderCode`. The return handler never mutates order state — grants happen on IPN only.
2. IPN handler (public GET, WAF + strict rate limits, no session): same signature verification first. Then, in order: (a) `vnp_TxnRef` must resolve a `provider='vnpay'` order or respond order-not-found; (b) `vnp_Amount` (÷100) must equal stored `amount_vnd` or respond invalid-amount; (c) success requires `vnp_ResponseCode=00`, otherwise record `failed` and respond accordingly; (d) the `pending → paid` transition, `vnp_transaction_no` write, entitlement write, and `audit_log{actor, action, target, at}` append happen atomically — an already-`paid` order returns already-confirmed with zero side effects.
3. IPN responses use VNPay's exact JSON codes: success `00`, order-not-found `01`, already-confirmed `02`, invalid-amount `04`, checksum-fail `97`, unknown-error `99`. Only transport/DB failures use 5xx-style retry semantics; validation outcomes are final codes (no retry storms).
4. Deployment hygiene: IPN URL registered/confirmed in the VNPay merchant portal after every deploy that changes it; return/IPN paths covered by the same WAF + rate rules as all anonymous routes. Local reproduction via public tunnel (`cloudflared tunnel → wrangler dev`) against the VNPay sandbox; sandbox verification exercises the full redirect + IPN round-trip per milestone.
5. Monitoring: structured logs per IPN (orderCode, vnp_TransactionNo, result, latency) with hashes/secrets redacted; alert on consecutive checksum failures or a spike in already-confirmed responses.

## Data / API touches

- Mutates `orders.status` (`pending → paid|failed|cancelled|expired`) keyed by `vnp_TxnRef = orderCode`, recording `vnp_transaction_no`. Plan grants write the subscription/entitlement row; product grants write the buyer-claim row for the shop major. All writes server-side via `worker/db/*`; IPN resolves the owner from the stored order, never from request identity; RLS preserved.
- Cryptography: HMAC-SHA512 via WebCrypto only (no Node `crypto` import). Key from `wrangler secret VNPAY_HASH_SECRET`.
- Buyer IP capture at checkout honors `CF-Connecting-IP` (Cloudflare edge) for the `vnp_IpAddr` field.

## Acceptance criteria

- Sandbox end-to-end: checkout → VNPay test pay → return page shows confirming → IPN arrives → `paid` + entitlement visible within the SLA.
- Replay test: same IPN delivered twice → single transition, single entitlement, single audit entry; second response already-confirmed, zero side effects.
- Tamper tests: bad `vnp_SecureHash` → failure/`97`; altered amount → invalid-amount, no grant; unknown `vnp_TxnRef` → order-not-found, no leak of valid refs.
- Return-page direct hit with `ResponseCode=00` but no IPN yet: order stays `pending`, UX shows confirming (queryDr repair covers it — settlement doc).
- Burst of bogus IPNs throttled by WAF/rate rules; valid traffic unaffected.

## Out of scope

- Checkout and payment-URL build (see `monetize/vnpay/checkout-orders.md`); queryDr verification, reconciliation sweep, refunds (see `monetize/vnpay/query-refund.md`).
- Plan matrix/AI caps, agency skeleton (`docs/monetize/` — reused as-is); shop delivery; P4/P5. PayOS and SePay paths untouched.

## Open questions

- Q1: Return-page confirming UX — polling interval and timeout before pointing the buyer at support (shared with the plans SLA question)?
- Q2: Expired-but-paid edge (paid on VNPay after local expiry): auto-grant on IPN, or hold for ops review?
