# PayOS Link Lifecycle (cancel, status, expiry sweep)

PLAN ref: P3-1/P3-2 gap-fill, default provider path — PLAN-conformant, no deviation. Happy-path checkout in `monetize/payos/checkout-orders.md`; webhook grant in `monetize/payos/webhook.md`. This doc owns everything after link creation that is not the webhook grant.

## Purpose

Close every gap the payment-link round-trip can leave: buyers who cancel on PayOS's page, links that expire unpaid, return-success/webhook-missing races, and orders stuck `pending` because a notification was lost. Server-side cancel, on-demand status query, and a scheduled sweep mean no real payment stays ungranted and no ambiguous case auto-grants.

## Users

- Buyer: *As a Buyer, I want to cancel cleanly, see accurate link/order state, escape "confirming" limbo via status re-check so that gap the payment-link round-trip can leave as defined here.* cancels cleanly, sees accurate link/order state, escapes "confirming" limbo via status re-check.
- Support/ops: *As a Support/ops, I want to cancel links, look up live link status by `orderCode`, resolve stuck orders so that gap the payment-link round-trip can leave as defined here.* cancels links, looks up live link status by `orderCode`, resolves stuck orders.
- System (scheduled job): *As a System, I want to sweep stale `pending` orders against PayOS truth so that gap the payment-link round-trip can leave as defined here.* sweeps stale `pending` orders against PayOS truth.

## Functional requirements

1. Buyer cancel: PayOS redirects cancellations to our same-origin `cancelUrl` (`/app/billing/cancel`), which marks the order `cancelled` and calls PayOS cancel-payment-link (`{orderCode}` + `cancellationReason`) so the link stops accepting money. Cancelled orders are never grantable afterward — a late webhook for a cancelled code is rejected and logged.
2. Server-side cancel endpoint `POST /api/billing/orders/:orderCode/cancel` (owner-scoped): same PayOS cancel call, same terminal state. Used by the buyer dashboard ("cancel unpaid order") and ops.
3. Expiry: `expiredAt` (Unix timestamp, Int32) set at link creation per the checkout doc's TTL decision. Expired links surface the expired-checkout UX; a scheduled job flips lapsed `pending` rows to `expired`. Late webhooks for expired codes are rejected, never granted (edge-case review per Q2).
4. On-demand status query: when the return page polls an order still `pending` past a short threshold, the server calls PayOS get-payment-link-information (`{orderCode}`) and maps the PayOS-reported link status to local state — gateway-paid routes through the same atomic, amount-checked, idempotent grant logic as the webhook; gateway-cancelled/expired flips local state to match. Throttled per order to bound API calls. (Confirm the exact PayOS status enum strings against the sandbox during implementation; the mapping table lives in code, not prose.)
5. Reconciliation sweep: scheduled job scans stale `pending` orders, status-queries each, and settles them (`paid` on gateway-paid with the idempotent grant, `expired`/`cancelled` otherwise). Cadence and stale threshold per Q1.
6. Buyer self-service: order-status endpoint distinguishes `pending` (no payment seen), `confirming` (return seen or gateway activity seen, grant in flight), and terminal states — no silent limbo.

## Data / API touches

- No new tables. Reads/writes `orders` (`status`, plus gateway link/payment references where useful). Idempotency comes from the guarded `pending → paid` transition shared with the webhook.
- PayOS APIs called server-side only (client/API/checksum keys from `wrangler secret`): get-payment-link-information for verify + sweep, cancel-payment-link for cancels. Requests signed per PayOS spec; responses logged minus secrets.
- Alerting: sweep-settled counts → metrics; consecutive PayOS API auth failures → page; late-webhook-on-terminal-order events → ops log (tune to alert if frequent).

## Acceptance criteria

- Cancel drill (sandbox): buyer cancels on PayOS page → lands on cancel URL → order `cancelled`, PayOS link dead (re-open refuses payment), late webhook rejected with no grant.
  *GWT: Given Cancel drill (sandbox): buyer cancels on PayOS page; When the transition executes; Then lands on cancel URL → order `cancelled`, PayOS link dead (re-open refuses payment), late webhook rejected with no grant.*
- Expiry drill: unpaid link past `expiredAt` → order `expired`, link unusable.
  *GWT: Given Expiry drill: unpaid link past `expiredAt`; When the transition executes; Then order `expired`, link unusable.*
- Webhook-suppression drill (block receiver, complete sandbox payment): return page shows confirming, on-demand status query grants exactly once; redelivered webhook afterward is a no-op.
  *GWT: Given Webhook-suppression drill (block receiver, complete sandbox payment) context; When return page shows confirming, on-demand status query grants exactly once; Then redelivered webhook afterward is a no-op.*
- Sweep drill: stale pendings with gateway-paid settle correctly, no doubles; mismatches held for ops.
  *GWT: Given Sweep drill context; When stale pendings with gateway-paid settle correctly, no doubles; Then mismatches held for ops.*
- Auth probe: user A cancelling/querying user B orders → 403; RLS denies cross-owner rows.
  *GWT: Given Auth probe: user A cancelling/querying user B orders; When the transition executes; Then 403; RLS denies cross-owner rows.*

## Out of scope

- Link creation and checkout (see `monetize/payos/checkout-orders.md`); webhook verification and grant (see `monetize/payos/webhook.md`).
- Chargeback/refund accounting beyond cancel-before-pay; SePay/VNPay exception handling (their own minors); plan downgrade UX (`docs/monetize/plans/`); P4/P5.

## Open questions

- Q1: Sweep cadence and stale-pending threshold — every 15 min + 30-min threshold, or tighter?
- Q2: Paid-after-expiry edge — auto-grant on status-proof, or hold for ops review?
