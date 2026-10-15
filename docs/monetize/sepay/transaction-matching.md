# Transaction Matching + Reconciliation (SePay exceptions)

PLAN ref: P3-1/P3-2 gap-fill. Deviation flagged: neither PLAN nor the PayOS path needs this — bank transfers (unlike hosted payment links) can arrive with edited contents, wrong amounts, or no order at all. This doc owns every transfer the webhook doc declines to grant. Happy-path matching in `monetize/sepay/webhook.md`.

## Purpose

Guarantee no real payment goes unnoticed and no ambiguous transfer auto-grants. Unmatched, underpaid, overpaid, duplicate-content, and late (post-expiry) transfers land in a reviewable exceptions queue with a defined resolution; a scheduled API-v2 reconciliation sweep catches anything webhooks dropped. Dropped or duplicate deliveries must never go unnoticed (SePay's own reconciliation guidance).

## Users

- Buyer who mistyped content, sent the wrong amount, or paid after expiry: *As a Buyer who mistyped content, sent the wrong amount, or paid after expiry, I want to get a resolvable outcome (auto-credit where safe, assisted otherwise) instead of silent loss so that no real payment goes unnoticed and no ambiguous transfer auto-grants as defined here.* gets a resolvable outcome (auto-credit where safe, assisted otherwise) instead of silent loss.
- Support/ops: *As a Support/ops, I want to triage the exceptions queue, link transfers to orders, issue entitlements or refund manually so that no real payment goes unnoticed and no ambiguous transfer auto-grants as defined here.* triages the exceptions queue, links transfers to orders, issues entitlements or refunds manually.
- System (scheduled job): *As a System, I want to poll SePay API v2 transaction lists and diff against local records so that no real payment goes unnoticed and no ambiguous transfer auto-grants as defined here.* polls SePay API v2 transaction lists and diffs against local records.

## Functional requirements

1. Exceptions ledger: every acknowledged-but-ungranted inbound transfer persists to `sepay_exceptions{sepay_txn_id UNIQUE, bank_account_id, amount_in, transaction_content, reference_number, transaction_date, reason, status, linked_order_code?, resolved_at?}` with standard `user_id`-scoping where attributable (nullable owner until linked) + RLS. `reason` ∈ `unknown_code|amount_mismatch|order_cancelled|order_expired|duplicate_content|late_transfer`. `status` ∈ `open|linked|refunded|dismissed`.
2. Safe auto-resolution rules (server-side, conservative): exact-code + exact-amount + pending order → grant (this is the webhook happy path, not an exception). Exact-code + overpay within tolerance (see Q1) → grant + log overpay for ops. Everything else stays `open` for human review — underpays and unknown codes never auto-grant.
3. Ops actions (authenticated, audited): link exception → pending order (re-runs the atomic grant), mark refunded (records the SePay API-v2 or manual-bank refund reference; API-v2 refunds are bank-limited — VietinBank enterprise — so manual refund is the default path), or dismiss with reason. Every action appends `audit_log{actor, action, target, at}`.
4. Reconciliation sweep: scheduled job (hourly or per Q2) queries SePay API v2 transaction lists (bearer auth, `transaction:read`) filtered by account + window, and diffs `sepay_txn_id` against `orders` + `sepay_exceptions`. Missing transfers are pulled in and processed through the same match/exception logic; already-recorded ones are skipped by the UNIQUE guard. Webhooks stay the real-time path; the API is lookup-and-repair only.
5. Late-transfer grace: transfers arriving within a short window after order expiry (default 24 h, see Q3) remain linkable by ops; older ones default to refund.
6. Buyer self-service: `GET /api/billing/orders/:orderCode` shows pending-order state including "transfer received, under review" when a same-code exception exists — no silent limbo.

## Data / API touches

- New table `sepay_exceptions` (schema above) + `orders.sepay_txn_id` (webhook doc). Optional ops endpoints: `GET /api/v1/ops/sepay-exceptions`, `POST /api/v1/ops/sepay-exceptions/:id/link|refund|dismiss` — restricted role, rate-limited, fully audited. All PG via `worker/db/*`.
- SePay API v2: server-side bearer calls only (keys in `wrangler secret`); transactions list/lookup for the sweep, bank-accounts list for config validation. Virtual-accounts and refund APIs are opt-in later (bank availability limits).
- Alerting: new `open` exception → ops channel; sweep delta (found-missing count) → metrics log; consecutive sweep auth failures → page.

## Acceptance criteria

- Simulated wrong-content transfer: 200 acknowledged, zero grant, `open` exception with `unknown_code`, visible in ops queue.
  *GWT: Given Simulated wrong-content transfer context; When the checked action occurs; Then 200 acknowledged, zero grant, `open` exception with `unknown_code`, visible in ops queue.*
- Underpay simulation: `amount_mismatch`, no grant; ops link after buyer tops up (or partial handling per Q1) grants exactly once.
  *GWT: Given Underpay simulation context; When `amount_mismatch`, no grant; Then ops link after buyer tops up (or partial handling per Q1) grants exactly once.*
- Post-expiry transfer inside grace: linkable by ops to a single grant; outside grace: refund path.
  *GWT: Given Post-expiry transfer inside grace context; When linkable by ops to a single grant; Then outside grace: refund path.*
- Webhook-suppression drill (drop deliveries, run sweep): all missing transfers recovered with correct outcomes, no doubles on redelivery.
  *GWT: Given Webhook-suppression drill (drop deliveries, run sweep) context; When the checked action occurs; Then all missing transfers recovered with correct outcomes, no doubles on redelivery.*
- Auth probe: non-ops user hitting ops endpoints → 403; RLS denies cross-owner rows.
  *GWT: Given Auth probe: non-ops user hitting ops endpoints; When the transition executes; Then 403; RLS denies cross-owner rows.*
- Refund action records an external reference and flips status to `refunded`; refunded transfers can never later grant.
  *GWT: Given Refund action records an external reference and flips status to `refunded`; When the next step executes; Then refunded transfers can never later grant.*

## Out of scope

- Happy-path verify-and-grant (see `monetize/sepay/webhook.md`); checkout issuance (see `monetize/sepay/checkout-orders.md`).
- Chargeback handling for PayOS/Stripe paths; accounting/revenue-recognition reports; P4/P5.

## Open questions

- Q1: Overpay tolerance — grant on any overpay and log the surplus, or hold all mismatches for review?
- Q2: Sweep cadence and window — hourly + 48 h lookback, or tighter?
- Q3: Late-transfer grace length — 24 h default acceptable?
