# SePay Webhook (verification + idempotent grant)

PLAN ref: P3-2 adapted. Deviation flagged: PLAN specifies PayOS HMAC over its canonical field string; this feature verifies SePay webhook delivery (HMAC-SHA256 mode) and matches on bank-transfer fields. Order creation in `monetize/sepay/checkout-orders.md`; exceptions in `monetize/sepay/transaction-matching.md`.

## Purpose

Define how the platform trusts SePay: every bank-transaction notification is authenticated (HMAC-SHA256 mode required — SePay also offers API Key, OAuth 2.0, or none, which this feature forbids), then matched to exactly one pending order and granted exactly once. Replays, forgeries, wrong amounts, and duplicate deliveries can never create a double grant or a free upgrade.

## Users

- SePay server: posts signed transaction events to the webhook URL on every inbound transfer to the linked company account.
- Buyer: receives entitlement (plan upgrade or product claim) after a genuine matched transfer.
- Ops: replays deliveries from SePay logs during incident recovery without double-granting.

## Functional requirements

1. `POST /api/billing/webhook-sepay` (public, WAF + strict rate limits, no session): parses the SePay transaction payload (`id, bank_account_id, bank_brand_name, account_number, transaction_date, amount_in, amount_out, transaction_content, reference_number, code`), verifies HMAC-SHA256 per SePay's authentication doc using WebCrypto (Workers-safe), constant-time compare. Mismatch → 401, nothing written. Only `amount_in > 0` (inbound) events are processed; outbound events are acknowledged 200 and ignored.
2. Matching (happy path): extract candidate `orderCode` from `transaction_content` (ASCII template from the checkout doc) AND require `amount_in == orders.amount_vnd` AND order `status = pending` AND `provider = 'sepay'`. All three must hold, atomically, inside one transaction: `pending → paid`, write entitlement, append `audit_log{actor, action, target, at}`. SePay transaction `id` recorded on the order as `sepay_txn_id UNIQUE` — the replay guard.
3. Replay safety: SePay retries failed deliveries up to 7 times on Fibonacci spacing (~33 min total) and ops can manually replay from delivery logs. A repeated `sepay_txn_id` (or an already-`paid` order) returns 200 with zero side effects.
4. Non-matching transfers never grant: unknown code, amount mismatch, cancelled/expired order, or duplicate `sepay_txn_id` → 200 acknowledged (so SePay stops retrying) but routed to the exceptions ledger in `monetize/sepay/transaction-matching.md`, never to an entitlement.
5. Deployment hygiene: webhook URL allowlisted/confirmed in the SePay dashboard after every deploy that changes it; local reproduction via public tunnel (`cloudflared tunnel → wrangler dev`, SePay Test-mode simulated transactions) — documented runbook, exercised per milestone. SePay Test mode is fully separate from Live (own bank accounts, webhooks, quotas); launch verification uses simulated transfers, never real ones.
6. Monitoring: structured logs per delivery (orderCode, sepay_txn_id, result, latency) with signatures/keys redacted; SePay-side Telegram/Slack/Discord alerts plus delivery-log review on consecutive failures.

## Data / API touches

- Mutates `orders.status` (`pending → paid`) keyed by `orderCode`, guarded by `sepay_txn_id UNIQUE`. Plan grants write the subscription/entitlement row; product grants write the buyer-claim row for the shop major. All writes server-side via `worker/db/*`; webhook resolves the owner from the stored order, never from request identity; RLS preserved.
- Cryptography: HMAC-SHA256 via WebCrypto only (no Node `crypto` import). Verification secret from `wrangler secret SEP..._WEBHOOK_SECRET` (exact name in implementation; never committed).
- Optional hardening: allowlist SePay's published egress IPs at WAF in addition to HMAC (defense in depth, not a substitute).

## Acceptance criteria

- Sandbox end-to-end (SePay Test mode): checkout → simulate inbound transfer with correct content+amount → webhook → `paid` + entitlement visible within the SLA.
- Replay test: same delivery posted twice (and SePay-side manual replay) → single transition, single entitlement, single audit entry; repeats are 200 no-ops.
- Tamper tests: bad signature → 401; altered content/amount → no grant, exception logged; outbound transfer → 200 ignored.
- Burst of bogus deliveries throttled by WAF/rate rules; valid traffic unaffected.
- Unknown-code transfer with valid signature → 200, no grant, appears in the exceptions queue (settlement doc's flow).

## Out of scope

- Checkout and transfer-detail issuance (see `monetize/sepay/checkout-orders.md`); exception matching, reconciliation polling, refunds (see `monetize/sepay/transaction-matching.md`).
- Plan matrix/AI caps, agency skeleton (`docs/monetize/` — reused as-is); shop delivery; P4/P5. The PayOS webhook path is untouched.

## Open questions

- Q1: Buyer-facing pending UX — polling order status vs push/SSE on the return page (shared with the plans SLA question)?
- Q2: IP allowlisting — maintain SePay egress-IP list in WAF from day one, or HMAC-only until abuse is observed?
