# VNPay Query + Refund (verify, reconcile, reverse)

PLAN ref: P3-1/P3-2 gap-fill. Deviation flagged: this doc extends the adapted VNPay path only. It covers VNPay's queryDr verify API, the scheduled reconciliation sweep, and the refund API. Happy-path grant in `monetize/vnpay/ipn.md`.

## Purpose

Close every gap the redirect + IPN round-trip can leave: buyers who close the browser before the return, IPNs lost in transit, return-success/IPN-missing races, and legitimate refund requests. An on-demand queryDr verify plus a scheduled sweep means no real payment stays ungranted and no ambiguous case auto-grants; a defined refund path reverses what should not stand.

## Users

- Buyer stuck on "confirming" (return seen, IPN delayed): *As a Buyer stuck on "confirming", I want to get resolution via on-demand verify instead of limbo so that gap the redirect + IPN round-trip can leave as defined here.* gets resolution via on-demand verify instead of limbo.
- Support/ops: *As a Support/ops, I want to look up a transaction by `orderCode`/`vnp_TransactionNo`, trigger verify, issue refunds so that gap the redirect + IPN round-trip can leave as defined here.* looks up a transaction by `orderCode`/`vnp_TransactionNo`, triggers verify, issues refunds.
- System (scheduled job): *As a System, I want to sweep stale `pending` orders and diff them against VNPay truth so that gap the redirect + IPN round-trip can leave as defined here.* sweeps stale `pending` orders and diffs them against VNPay truth.

## Functional requirements

1. On-demand verify: when the return page polls an order still `pending` past a short threshold, the server calls VNPay queryDr (`vnp_Command=querydr`, `vnp_TmnCode`, `vnp_TxnRef=orderCode`, `vnp_TransactionDate` of the order, request signed HMAC-SHA512) and applies the authoritative result through the same atomic grant logic as IPN (unknown / amount-mismatch / already-paid rules identical). Throttled per order to bound gateway calls.
2. Reconciliation sweep: scheduled job scans `pending` orders older than N minutes, queryDr-verifies each, and settles them (`paid` on gateway-success, `expired`/`failed` otherwise). Gateway-success-but-locally-unpaid is granted exactly once via the same idempotent transition; already-`paid` rows are skipped. Cadence and lookback per Q1.
3. Amount binding on every path: gateway amount (÷100) must equal `amount_vnd`; mismatch → no grant + ops alert, never auto-credit.
4. Refunds (ops-initiated, authenticated, audited): `vnp_Command=refund` with the original `vnp_TransactionNo`, amount, and transaction date, signed per VNPay's refund spec; records the gateway refund reference on the order, flips status to `refunded`, and revokes the entitlement (plan downgrade per the plans doc's downgrade rule; product claim invalidated in the shop major). Partial refunds per Q2. Every refund appends `audit_log{actor, action, target, at}`.
5. Ops endpoints (restricted role, rate-limited, audited): `POST /api/v1/ops/vnpay/verify` (by orderCode), `GET /api/v1/ops/vnpay/orders/:orderCode`, `POST /api/v1/ops/vnpay/refund`. All PG via `worker/db/*`.
6. Buyer self-service: order-status endpoint distinguishes `pending` (no payment seen), `confirming` (return seen or gateway-success seen, grant in flight), and final states — no silent limbo.

## Data / API touches

- No new tables. Reads/writes `orders` (`status`, `vnp_transaction_no`) plus entitlement/claim rows owned by sibling specs. Idempotency comes from the `pending → paid` guarded transition, shared with IPN.
- VNPay APIs called server-side only (TmnCode/HashSecret from `wrangler secret`): queryDr for verify + sweep, refund for reversals. Request signing HMAC-SHA512 via WebCrypto; responses logged minus secrets.
- Alerting: sweep-settled counts → metrics; consecutive queryDr auth failures → page; every refund → ops channel.

## Acceptance criteria

- IPN-suppression drill (block IPN, complete sandbox payment): return page shows confirming, on-demand queryDr grants exactly once; redelivered IPN afterward is a no-op already-confirmed.
  *GWT: Given IPN-suppression drill (block IPN, complete sandbox payment) context; When return page shows confirming, on-demand queryDr grants exactly once; Then redelivered IPN afterward is a no-op already-confirmed.*
- Sweep drill (stale pendings with gateway-success): all settled correctly, no doubles, mismatches held for ops.
  *GWT: Given Sweep drill (stale pendings with gateway-success) context; When the checked action occurs; Then all settled correctly, no doubles, mismatches held for ops.*
- Refund drill (sandbox): status `refunded` with gateway reference recorded, entitlement revoked, re-grant impossible.
  *GWT: Given Refund drill (sandbox) context; When the checked action occurs; Then status `refunded` with gateway reference recorded, entitlement revoked, re-grant impossible.*
- Auth probe: non-ops user on ops endpoints → 403; RLS denies cross-owner rows.
  *GWT: Given Auth probe: non-ops user on ops endpoints; When the transition executes; Then 403; RLS denies cross-owner rows.*
- Gateway-error handling: transient queryDr failures retry with backoff; persistent auth errors page instead of spinning.
  *GWT: Given Gateway-error handling context; When transient queryDr failures retry with backoff; Then persistent auth errors page instead of spinning.*

## Out of scope

- Payment-URL build and checkout (see `monetize/vnpay/checkout-orders.md`); return/IPN verification and grant (see `monetize/vnpay/ipn.md`).
- SePay/PayOS exception handling (their own minors); plan downgrade UX detail (`docs/monetize/plans/`); accounting/revenue reports; P4/P5.

## Open questions

- Q1: Sweep cadence and stale-pending threshold — every 15 min + 30-min threshold, or tighter?
- Q2: Partial refunds in v1, or full-only with re-purchase for adjustments?
