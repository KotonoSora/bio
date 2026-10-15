# VNPay Checkout + Orders (gateway payment URL)

PLAN ref: P3-1 adapted. Deviation flagged: PLAN specifies PayOS payment links; this feature collects via the VNPay gateway per explicit request. Same `orders` table (plus `provider` column) and same plan/product flow — only the collection mechanism differs. IPN grant in `monetize/vnpay/ipn.md`; verify-and-repair in `monetize/vnpay/query-refund.md`.

## Purpose

Define how money starts moving via VNPay: an authenticated checkout creates a tracked `orders` row with a unique numeric `orderCode`, then returns a signed VNPay payment URL (amount, order ref, expiry, return address — HMAC-SHA512 sealed). The buyer pays on VNPay's hosted page (ATM, card, wallet, QR — VND only); VNPay later notifies us server-to-server. The same flow sells plan subscriptions and single products (`product_id?`), so shop majors plug in without a second payment path.

## Users

- Buyer (logged-in owner for plans; buyer-email claim for products per shop major): starts checkout, pays on VNPay, lands back on the platform.
- Creator/seller: receives entitlement on IPN grant.
- Support/ops: looks up an order by `orderCode` / `vnp_TransactionNo` to reconcile disputes.

## Functional requirements

1. `POST /api/billing/checkout` (authenticated, Turnstile + per-IP/email rate limits): body `{provider: "vnpay", plan? | product_id?, buyer_email?, bank_code?}`. Server re-prices from canonical plan/product tables — client-supplied amounts are never trusted. Response: `{orderCode, amount_vnd, payment_url, expires_at}`.
2. `orderCode` generation: numeric int64 unique (`Date.now()*1000 + rand`), stored on `orders{id, user_id, provider: "vnpay", plan?, product_id?, amount_vnd, status}` and sent as `vnp_TxnRef` (globally unique, satisfying VNPay's same-day uniqueness rule). Integer VND in our DB; multiplied by 100 only inside the signed `vnp_Amount` field.
3. Payment-URL build (server-side, `VNPAY_ENV=sandbox|production`; sandbox `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html`): `vnp_Version=2.1.0`, `vnp_Command=pay`, `vnp_TmnCode`, `vnp_Amount`, `vnp_CurrCode=VND`, `vnp_TxnRef=orderCode`, `vnp_OrderInfo` (ASCII, embeds orderCode), `vnp_OrderType` (configured category), `vnp_Locale=vn`, `vnp_ReturnUrl` (same-origin `/app/billing/return`), `vnp_IpAddr` (buyer IP honoring `CF-Connecting-IP`), `vnp_CreateDate` + `vnp_ExpireDate` (`yyyyMMddHHmmss`, TTL default 30–60 min per Q1), optional `vnp_BankCode` pre-select. Sorted params sealed with HMAC-SHA512 (`HashSecret`) via WebCrypto → `vnp_SecureHash`. Initial status `pending`.
4. Secrets (`VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`) via `wrangler secret` / `.dev.vars` locally — never committed, never logged, never in the client bundle.
5. Buyer-email claim for course/digital products: binds `buyer_email` so the shop major issues the R2 signed download / lesson claim after the paid grant (delivery lives in the shop major).
6. Plan matrix and AI credits unchanged — see `docs/monetize/plans/subscription-plans.md`.

## Data / API touches

- `orders` gains `provider TEXT NOT NULL DEFAULT 'payos'` + `vnp_transaction_no?` + `expires_at?`, keeping `user_id TEXT NOT NULL` + index + RLS. `status` ∈ `pending|paid|cancelled|expired|failed`. Dashboard reads scope `WHERE user_id = session.userId`.
- Endpoints (JSON over `/api/*`, cookie session): `POST /api/billing/checkout`, `GET /api/billing/orders`, `GET /api/billing/orders/:orderCode` (buyer polls this after return — the return page never grants), `POST /api/billing/orders/:orderCode/cancel`. No direct DB from client; all PG via `worker/db/*`, least-privilege app role.
- Retry/double-click while pending: single row, same `orderCode`, fresh signature re-issued — never a duplicate row.

## Acceptance criteria

- Sandbox checkout for plan and for product: VNPay page opens, amount exact to the VND, `orders` row pending with unique `orderCode` echoed as `vnp_TxnRef`.
- Amount tamper (client sends lower amount): server re-prices to canonical; mismatch logged.
- Expired payment attempt on VNPay's page surfaces the expired-checkout UX; order flips `expired` and is never grantable by a late IPN (see IPN doc).
- Off-origin return address: rejected 422 at checkout build.
- IDOR probe: user A reading user B orders returns 403; RLS denies cross-user rows at DB.
- Secrets probe: no TmnCode/HashSecret in repo, logs, or client bundle (grep gate).

## Out of scope

- Return-URL verification and IPN grant (see `monetize/vnpay/ipn.md`); queryDr verify, reconciliation sweep, refunds (see `monetize/vnpay/query-refund.md`).
- Plan matrix, AI caps, agency skeleton (`docs/monetize/` — provider-agnostic, reused as-is); shop delivery; receipt emails (Resend; campaigns P4); P5 scale-out. PayOS and SePay paths untouched; multi-provider checkout UI is not in this feature.

## Open questions

- Q1: Payment-URL TTL — 30 min, 60 min, or VNPay-max with local pending sweep?
- Q2: Guest checkout for products (buyer-email only) in v1, or login required for all purchases?
