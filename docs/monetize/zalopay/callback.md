# ZaloPay Callback (verification + idempotent grant)

PLAN ref: P3-2 adapted. Deviation flagged: PLAN specifies a PayOS webhook; this feature verifies ZaloPay's callback (`data` + key2 `mac`) and grants on it. Order creation in `monetize/zalopay/checkout-orders.md`; query/repair in `monetize/zalopay/query-refund.md`.

## Purpose

Define how the platform trusts ZaloPay: every callback's `mac` is recomputed with HMAC-SHA256 (**key2**, Workers-safe WebCrypto) over the raw `data` string and constant-time compared; only then is the embedded `app_trans_id` resolved to an order and granted exactly once. Forgery, replay, wrong amounts, and already-paid races can never create a double grant or a free upgrade.

## Users

- ZaloPay server: posts `{data, mac, type}` to the per-order `callback_url` on successful collection.
- Buyer: receives entitlement (plan upgrade or product claim) after a genuine callback.
- Ops: relies on idempotency during incident recovery and duplicate deliveries.

## Functional requirements

1. Callback handler `POST /api/billing/callback-zalopay` (public, WAF + strict rate limits, no session, HTTPS only, CSRF-exempt route): parses `{data: <JSON string>, mac, type}`. Recomputes `HMAC-SHA256(data, key2)` over the raw `data` string exactly as received (no re-serialization). Mismatch → `return_code -1` ("mac not equal"), nothing written.
2. On valid mac, parse `data` → `{app_trans_id, amount, zp_trans_id, ...}` and, atomically: (a) `app_trans_id` must resolve a `provider='zalopay'` order or respond failure without disclosing valid refs; (b) `amount` must equal stored `amount_vnd` or respond failure + alert, no grant; (c) the `pending → paid` transition, `zp_trans_id` write, entitlement write, and `audit_log{actor, action, target, at}` append happen in one transaction — an already-`paid` order (duplicate delivery) returns success with zero side effects.
3. Response codes follow ZaloPay's callback contract: `return_code 1` + "success" only after the grant (or duplicate-safe no-op) is durable; `0` on transient/processing failures so ZaloPay retries (up to 3 redeliveries); `-1` on mac mismatch (no retry-worthy work). Validation outcomes are final; only transport/DB failures ask for redelivery.
4. Deployment hygiene: `callback_url` values confirmed reachable/registered per environment after every deploy that changes them; callback logs reviewed in the Merchant Portal (sandbox devtool) per milestone. Local reproduction via public tunnel (`cloudflared tunnel → wrangler dev`) against the ZaloPay sandbox.
5. Monitoring: structured logs per callback (orderCode, app_trans_id, zp_trans_id, result, latency) with macs/keys redacted; alert on consecutive mac failures or a spike in duplicate deliveries.

## Data / API touches

- Mutates `orders.status` (`pending → paid|failed|cancelled|expired`) keyed by `app_trans_id`, recording `zp_trans_id` (needed later for refund/reconciliation). Plan grants write the subscription/entitlement row; product grants write the buyer-claim row for the shop major. All writes server-side via `worker/db/*`; callback resolves the owner from the stored order, never from request identity; RLS preserved.
- Cryptography: HMAC-SHA256 via WebCrypto only (no Node `crypto` import). key2 from `wrangler secret ZALOPAY_KEY2`; key1 never accepted here.
- The redirect landing page (`embed_data` redirect URL) verifies nothing and mutates nothing — grant authority belongs to this callback plus the settlement doc's query path only.

## Acceptance criteria

- Sandbox end-to-end: checkout → ZaloPay test pay → callback → `paid` + entitlement visible within the SLA.
- Replay test: same callback delivered twice (and more, mirroring redelivery) → single transition, single entitlement, single audit entry; repeats are durable no-ops.
- Tamper tests: bad `mac` → `-1`, nothing written; altered amount in `data` → failure, no grant; unknown `app_trans_id` → failure, no leak of valid refs.
- Redirect hit with no callback yet: order stays `pending`, UX shows confirming (query repair covers it — settlement doc).
- Burst of bogus callbacks throttled by WAF/rate rules; valid traffic unaffected.

## Out of scope

- Order creation and `order_url` issuance (see `monetize/zalopay/checkout-orders.md`); status query, expiry sweep, refunds (see `monetize/zalopay/query-refund.md`).
- Plan matrix/AI caps, agency skeleton (`docs/monetize/` — shared as-is); shop delivery; P4/P5. PayOS, SePay, and VNPay paths untouched.

## Open questions

- Q1: Redirect-page confirming UX — polling interval and timeout before pointing the buyer at support (shared with the plans SLA question)?
- Q2: Expired-but-paid edge (callback for a locally-expired order): auto-grant on proof, or hold for ops review?
