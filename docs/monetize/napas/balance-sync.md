# NAPAS Balance Sync (verification + idempotent grant)

PLAN ref: P3-2 adapted. Deviation flagged: PLAN specifies a PayOS webhook; this feature grants on the rail's balance-fluctuation sync push to our partner-hosted endpoint. Order creation in `monetize/napas/checkout-orders.md`; exceptions/repair in `monetize/napas/reconciliation.md`.

## Purpose

Define how the platform trusts rail money: every balance-fluctuation delivery is bearer-authenticated, then matched to exactly one pending order through the `napas_order_ref` map with amount equality, and granted exactly once. Replays, forgeries, edited contents, wrong amounts, and already-paid races can never create a double grant or a free upgrade.

## Users

- Rail/VietQR service: *As a Rail/VietQR service, I want to push balance-fluctuation notifications to our Transaction Sync endpoint on each collected transfer so that the platform trusts rail money as defined here.* pushes balance-fluctuation notifications to our Transaction Sync endpoint on each collected transfer.
- Buyer: *As a Buyer, I want to receive entitlement (plan upgrade or product claim) after a genuine matched sync so that the platform trusts rail money as defined here.* receives entitlement (plan upgrade or product claim) after a genuine matched sync.
- Ops: *As an Ops, I want to rely on idempotency during incident recovery and duplicate deliveries so that the platform trusts rail money as defined here.* relies on idempotency during incident recovery and duplicate deliveries.

## Functional requirements

1. Transaction Sync endpoint `POST /api/billing/sync-napas` (public, WAF + strict rate limits, no session, HTTPS only, CSRF-exempt route): bearer-token authenticated (tokens from our partner-side token endpoint, Basic-auth secured, short-lived). Missing/invalid token → 401-equivalent, nothing written.
2. On authenticated delivery, resolve the echoed order ref through the `napas_order_ref → orderCode` map and, atomically: (a) unknown ref → acknowledge 200 but route to the exceptions ledger (settlement doc), never grant — this is the documented shape of an edited-content transfer, which arrives without ref; (b) `amount` must equal stored `amount_vnd` or acknowledge + exception, no grant; (c) the `pending → paid` transition, rail transaction-id write (`napas_txn_id UNIQUE` — the replay guard), entitlement write, and `audit_log{actor, action, target, at}` append happen in one transaction — an already-`paid` order (duplicate push) is a durable no-op.
3. Response discipline: transport/DB failures → 5xx so the rail side retries; validation outcomes (unknown ref, amount mismatch, already-paid) → 200 (final, no retry storm) with the outcome recorded locally.
4. Sync latency is package-dependent (~10–15 s standard, ~1–3 s priority): the buyer-facing order page polls `GET /api/billing/orders/:orderCode` and shows confirming until the grant lands — the display/sync endpoints never mutate order state themselves.
5. Deployment hygiene: sync endpoint + token credentials declared per environment (TEST vs GOLIVE) after every deploy that changes them; UAT simulation (provider test-callback API) exercised per milestone; local reproduction via public tunnel (`cloudflared tunnel → wrangler dev`). TEST and GOLIVE environments are fully separate (own accounts, quotas, credentials).
6. Monitoring: structured logs per delivery (orderCode, napas_txn_id, result, latency) with tokens/secrets redacted; alert on consecutive auth failures or a spike in duplicates/unmatched.

## Data / API touches

- Mutates `orders.status` (`pending → paid|failed|cancelled|expired`) keyed by `napas_order_ref`, recording `napas_txn_id UNIQUE`. Plan grants write the subscription/entitlement row; product grants write the buyer-claim row for the shop major. All writes server-side via `worker/db/*`; sync resolves the owner from the stored order, never from request identity; RLS preserved.
- Authentication: partner-side bearer tokens (Basic-auth token endpoint, ~300 s default validity, server-side refresh); rail calls authenticated the same way. No Node-only crypto — WebCrypto/subtle where signatures apply; secrets from `wrangler secret`.
- Buyer IP is irrelevant on this path (payment happens in the buyer's bank app); no IP fields are fabricated.

## Acceptance criteria

- UAT end-to-end (simulated successful payment): checkout → dynamic QR → simulated sync → `paid` + entitlement visible within the package SLA.
  *GWT: Given UAT end-to-end (simulated successful payment): checkout; When the transition executes; Then dynamic QR → simulated sync → `paid` + entitlement visible within the package SLA.*
- Replay test: same delivery twice → single transition, single entitlement, single audit entry; repeat is a durable no-op.
  *GWT: Given Replay test: same delivery twice; When the transition executes; Then single transition, single entitlement, single audit entry; repeat is a durable no-op.*
- Tamper tests: bad/missing token → rejected, nothing written; edited-content delivery (no ref echo) → 200, no grant, appears in the exceptions queue; wrong amount → no grant.
  *GWT: Given Tamper tests: bad/missing token; When the transition executes; Then rejected, nothing written; edited-content delivery (no ref echo) → 200, no grant, appears in the exceptions queue; wrong amount → no grant.*
- Display-page polling with no sync yet: order stays `pending`, UX shows confirming (check-transaction repair covers it — settlement doc).
  *GWT: Given Display-page polling with no sync yet context; When the checked action occurs; Then order stays `pending`, UX shows confirming (check-transaction repair covers it — settlement doc).*
- Burst of bogus deliveries throttled by WAF/rate rules; valid traffic unaffected.
  *GWT: Given Burst of bogus deliveries throttled by WAF/rate rules; When the next step executes; Then valid traffic unaffected.*

## Out of scope

- QR issuance and checkout (see `monetize/napas/checkout-orders.md`); unmatched/late transfers, check-transaction sweep, refunds (see `monetize/napas/reconciliation.md`).
- Plan matrix/AI caps, agency skeleton (`docs/monetize/` — shared as-is); shop delivery; P4/P5. PayOS, SePay, VNPay, and ZaloPay paths untouched.

## Open questions

- Q1: Confirming-page UX — polling interval and timeout before pointing the buyer at support (shared with the plans SLA question)?
- Q2: Expired-but-paid edge (sync arrives after local expiry): auto-grant on proof, or hold for ops review?
