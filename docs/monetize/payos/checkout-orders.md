# PayOS Checkout + Orders (payment-link initiation)

PLAN ref: P3-1, default provider path — PLAN-conformant, no deviation. (Provider section mirrors the other providers' trio shape; PayOS is the PLAN default.) Webhook grant in `monetize/payos/webhook.md`; link cancel/status/sweep in `monetize/payos/link-lifecycle.md`.

## Purpose

Define how money starts moving via PayOS: an authenticated checkout creates a tracked `orders` row with a unique numeric `orderCode`, then creates a PayOS payment link (exact VND amount, expiry, same-origin return/cancel) and hands the buyer its URL. The buyer pays by VietQR/bank on PayOS's page; the webhook later grants entitlement. The same flow sells plan subscriptions and single products (`product_id?`), so shop majors plug in without a second payment path.

## Users

- Buyer (logged-in owner for plans; buyer-email claim for products per shop major): starts checkout, pays on the PayOS link, returns to the platform.
- Creator/seller: receives entitlement on webhook grant.
- Support/ops: looks up an order by `orderCode` to reconcile disputes.

## Functional requirements

1. `POST /api/billing/checkout` (authenticated, Turnstile + per-IP/email rate limits): body `{provider: "payos", plan? | product_id?, buyer_email?}`. Server re-prices from canonical plan/product tables — client-supplied amounts are never trusted. Response: `{orderCode, amount_vnd, payment_url, expires_at}`.
2. `orderCode` generation: numeric int64 unique (`Date.now()*1000 + rand`), stored on `orders{id, user_id, provider: "payos", plan?, product_id?, amount_vnd, status}`. Integer VND, no decimals. Initial `status = pending`.
3. PayOS link creation (server-side, `PAYOS_ENV=sandbox|production`, sandbox base `https://api-merchant.payos.vn`): request carries `orderCode + amount + returnUrl + cancelUrl + expiredAt` (Unix timestamp, Int32) plus optional invoice object, sealed with `signature` (HMAC-SHA256 over the canonical field string with the channel checksum key). Same-origin `returnUrl`/`cancelUrl` under `/app/billing/{return,cancel}` with an allowlist; off-origin rejected at build.
4. Secrets (`PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY`, optional `PAYOS_PARTNER_CODE`) via `wrangler secret` / `.dev.vars` locally — never committed, never logged, never in the client bundle.
5. Buyer-email claim for course/digital products: binds `buyer_email` so the shop major issues the R2 signed download / lesson claim after the paid grant (delivery lives in the shop major).
6. Plan matrix and AI credits unchanged — see `docs/monetize/plans/subscription-plans.md`.

## Data / API touches

- Table `orders{user_id, provider, product_id?, plan?, amount_vnd INT, status, orderCode UNIQUE}` plus standard `user_id TEXT NOT NULL` + index + RLS policy. `status` ∈ `pending|paid|cancelled|expired|failed|refunded`. Dashboard reads scope `WHERE user_id = session.userId`.
- Endpoints (JSON over `/api/*`, cookie session): `POST /api/billing/checkout`, `GET /api/billing/orders`, `GET /api/billing/orders/:orderCode` (buyer polls this after return — the return page never grants), `POST /api/billing/orders/:orderCode/cancel`. No direct DB from client; all PG via `worker/db/*`, least-privilege app role.
- Retry/double-click while pending: single row, single `orderCode`, same link re-issued — never a duplicate row.

## Acceptance criteria

- Sandbox checkout for plan and for product: PayOS link opens, amount exact to the VND, `orders` row pending with unique `orderCode`.
- Amount tamper (client sends lower amount): server re-prices to canonical; mismatch logged.
- Off-origin return/cancel addresses: rejected 422 at checkout build.
- Expired-link attempt surfaces the expired-checkout UX; order flips `expired` (lifecycle doc owns the sweep).
- IDOR probe: user A reading user B orders returns 403; RLS denies cross-user rows at DB.
- Secrets probe: no PayOS key in repo, logs, or client bundle (grep gate).

## Out of scope

- Webhook verification and grant (see `monetize/payos/webhook.md`); link cancel, status query, expiry sweep (see `monetize/payos/link-lifecycle.md`).
- Plan matrix, AI caps, agency skeleton (`docs/monetize/plans/`, `docs/monetize/organizations/` — provider-agnostic, shared).
- Shop delivery; receipt emails (Resend; campaigns P4); P5 scale-out. SePay and VNPay paths untouched; multi-provider checkout UI is not in this feature.

## Open questions

- Q1: Link TTL (`expiredAt`) — 30 min, 60 min, or longer for high-ticket products?
- Q2: Guest checkout for products (buyer-email only) in v1, or login required for all purchases?
