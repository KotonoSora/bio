# PayOS Webhook (verification + idempotent grant)

PLAN ref: P3-2, default provider path — PLAN-conformant, no deviation. Order creation in `monetize/payos/checkout-orders.md`; cancel/status/sweep in `monetize/payos/link-lifecycle.md`.

## Purpose

Define how the platform trusts PayOS: every payment notification's `signature` is re-verified with HMAC-SHA256 (checksum key, Workers-safe WebCrypto), then applied exactly once as an entitlement grant. Replay, tampering, or amount mismatch can never create a double grant or a free upgrade.

## Users

- PayOS server: *As a PayOS server, I want to post signed payment notifications to the webhook URL so that the platform trusts PayOS as defined here.* posts signed payment notifications to the webhook URL.
- Buyer: *As a Buyer, I want to receive entitlement (plan upgrade or product claim) after genuine payment so that the platform trusts PayOS as defined here.* receives entitlement (plan upgrade or product claim) after genuine payment.
- Ops: *As an Ops, I want to replay safely during incident recovery without double-granting so that the platform trusts PayOS as defined here.* replays safely during incident recovery without double-granting.

## Functional requirements

1. `POST /api/billing/webhook` (public, WAF + strict rate limits, no session): parses the PayOS payload, recomputes HMAC-SHA256 over the canonical field string with `PAYOS_CHECKSUM_KEY` via WebCrypto, constant-time compare. Mismatch → 401, nothing written.
2. Idempotent grant: `pending → paid` on the matching `orders` row exactly once (guarded by `orderCode UNIQUE` + status check inside one transaction), then entitlement write plus `audit_log{actor, action, target, at}` append. Replays of a paid `orderCode` return 200 with zero side effects.
3. Amount binding: notification amount must equal stored `amount_vnd`; mismatch → reject + alert, no grant. Unknown `orderCode` → 404-equivalent without disclosing valid codes.
4. Post-grant entitlements: plan subscriptions set the user's plan/period; product orders mark the buyer claim redeemable (delivery mechanics live in the shop major).
5. Deployment hygiene: after every deploy that changes the webhook URL, run PayOS `webhooks/confirm` (confirm endpoint) until the dashboard shows confirmed. Local reproduction via public tunnel (`cloudflared tunnel → wrangler dev`) against the PayOS sandbox — documented runbook, exercised per milestone.
6. Failure handling: transient PayOS/DB errors → 5xx for PayOS retry; persistent validation failures → 4xx (no retry) with a structured error code.

## Data / API touches

- Mutates `orders.status` (`pending → paid|cancelled|expired|failed`) keyed by `orderCode UNIQUE`. Plan grants write the subscription/entitlement row; product grants write the buyer-claim row for the shop major. All writes server-side via `worker/db/*`; webhook resolves the owner from the stored order, never from request identity; RLS preserved.
- Cryptography: HMAC-SHA256 via WebCrypto only (no Node `crypto` import). Key from `wrangler secret PAYOS_CHECKSUM_KEY`.
- Observability: structured logs per webhook (orderCode, result, latency) with signatures/keys redacted; alert on consecutive verification failures.

## Acceptance criteria

- Sandbox end-to-end: checkout → PayOS test pay → webhook → `paid` + entitlement visible within the SLA.
  *GWT: Given Sandbox end-to-end: checkout; When the transition executes; Then PayOS test pay → webhook → `paid` + entitlement visible within the SLA.*
- Replay test: same valid payload twice → single transition, single entitlement, single audit entry; second is a 200 no-op.
  *GWT: Given Replay test: same valid payload twice; When the transition executes; Then single transition, single entitlement, single audit entry; second is a 200 no-op.*
- Tamper tests: bad signature → 401; altered amount → rejected, no grant; unknown `orderCode` → no information leak.
  *GWT: Given Tamper tests: bad signature; When the transition executes; Then 401; altered amount → rejected, no grant; unknown `orderCode` → no information leak.*
- `webhooks/confirm` runbook verified after deploy (PayOS dashboard shows confirmed URL).
  *GWT: Given the preconditions for this check hold; When `webhooks/confirm` runbook verified after deploy (PayOS dashboard shows confirmed URL); Then the expected outcome stated here is observed.*
- Burst of bogus webhooks throttled by WAF/rate rules; valid traffic unaffected.
  *GWT: Given Burst of bogus webhooks throttled by WAF/rate rules; When the next step executes; Then valid traffic unaffected.*

## Out of scope

- Checkout and link creation (see `monetize/payos/checkout-orders.md`); link cancel, status query, expiry sweep (see `monetize/payos/link-lifecycle.md`).
- Plan matrix/AI caps, agency skeleton (`docs/monetize/` — shared as-is); shop delivery; P4/P5. SePay and VNPay paths untouched.

## Open questions

- Q1: Webhook-to-entitlement SLA for buyer UX — polling interval on the return page before pointing at support (shared with the plans SLA question)?
- Q2: Partial-refund/chargeback handling — manual ops flow v1, or PayOS-side reversal integration?
