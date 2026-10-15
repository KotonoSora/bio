# ZaloPay Query + Refund (verify, reconcile, reverse)

PLAN ref: P3-1/P3-2 gap-fill. Deviation flagged: extends the adapted ZaloPay path only (ZaloPay's own docs prescribe this: no callback within ~15 min of order establishment → merchant must query proactively). Happy-path grant in `monetize/zalopay/callback.md`.

## Purpose

Close every gap the order + callback round-trip can leave: buyers who close the browser mid-payment, callbacks lost to network/service errors, redirect-seen/callback-missing races, and legitimate refund requests. A scheduler-backed query sweep plus an on-demand verify means no real payment stays ungranted and no ambiguous case auto-grants; a defined refund path reverses what should not stand.

## Users

- Buyer stuck on "confirming" (redirect seen, callback delayed): gets resolution via on-demand verify instead of limbo.
- Support/ops: looks up live status by `app_trans_id`, triggers verify, issues refunds.
- System (scheduled job): sweeps callback-less orders until each resolves or its payment window expires (ZaloPay's recommended Option 1; Option 2 minimum is one query after the window lapses).

## Functional requirements

1. On-demand verify: when the redirect page polls an order still `pending` past a short threshold, the server calls Query Order Status (`app_id`, `app_trans_id`, `mac` = HMAC-SHA256 over `app_id|app_trans_id|<key>` with the query mac key via WebCrypto) and applies the authoritative result through the same atomic, amount-checked, idempotent grant logic as the callback. A gateway-paid result grants exactly once; gateway-failed/processing maps to local state without granting. Throttled per order to bound API calls.
2. Reconciliation sweep: scheduled job scans `pending` orders lacking callbacks, queries each, and settles them (`paid` on gateway-paid with the idempotent grant; `expired`/`failed` otherwise). Already-`paid` rows are skipped. Cadence and lookback per Q1; minimum bar is one query per order after its payment window lapses with no callback.
3. Amount binding on every path: gateway-reported `amount` must equal `amount_vnd`; mismatch → no grant + ops alert, never auto-credit. Recorded `zp_trans_id` (from callback or query) is the key for all later refund/reconciliation work.
4. Refunds (ops-initiated, authenticated, audited): ZaloPay refund API with a merchant-generated refund transaction id, the original `zp_trans_id`, and the amount (full or partial per Q2); records the gateway refund reference on the order, flips status to `refunded`, and revokes the entitlement (plan downgrade per the plans doc's downgrade rule; product claim invalidated in the shop major). Every refund appends `audit_log{actor, action, target, at}`.
5. Ops endpoints (restricted role, rate-limited, audited): `POST /api/v1/ops/zalopay/verify` (by orderCode), `GET /api/v1/ops/zalopay/orders/:orderCode`, `POST /api/v1/ops/zalopay/refund`. All PG via `worker/db/*`.
6. Buyer self-service: order-status endpoint distinguishes `pending` (no payment seen), `confirming` (redirect seen or gateway activity seen, grant in flight), and terminal states — no silent limbo.

## Data / API touches

- No new tables. Reads/writes `orders` (`status`, `zp_trans_id`, `app_trans_id`) plus entitlement/claim rows owned by sibling specs. Idempotency comes from the guarded `pending → paid` transition shared with the callback.
- ZaloPay APIs called server-side only (app_id/keys from `wrangler secret`): query for verify + sweep, refund for reversals. Request macs via WebCrypto; responses logged minus secrets.
- Alerting: sweep-settled counts → metrics; consecutive query auth failures → page; every refund → ops channel.

## Acceptance criteria

- Callback-suppression drill (block receiver, complete sandbox payment): redirect page shows confirming, on-demand query grants exactly once; redelivered callback afterward is a no-op.
- Sweep drill: callback-less orders with gateway-paid settle correctly, no doubles; mismatches held for ops.
- Refund drill (sandbox): status `refunded` with gateway reference recorded, entitlement revoked, re-grant impossible.
- Auth probe: non-ops user on ops endpoints → 403; RLS denies cross-owner rows.
- Gateway-error handling: transient query failures retry with backoff; persistent auth errors page instead of spinning.

## Out of scope

- Order creation and `order_url` issuance (see `monetize/zalopay/checkout-orders.md`); callback verification and grant (see `monetize/zalopay/callback.md`).
- PayOS/SePay/VNPay handling (their own minors); plan downgrade UX detail (`docs/monetize/plans/`); accounting/revenue reports; P4/P5.

## Open questions

- Q1: Sweep cadence and stale-pending threshold — every 15 min + 30-min threshold, or tighter?
- Q2: Partial refunds in v1, or full-only with re-purchase for adjustments?
