# ZaloPay Checkout + Orders (gateway order creation)

PLAN ref: P3-1 adapted. Deviation flagged: PLAN specifies PayOS; this feature collects via the ZaloPay gateway per explicit request (provider order per AGENTS.md: PayOS default → alternatives only when asked — asked here). Same `orders` table (plus `provider` column) and same plan/product flow. Callback grant in `monetize/zalopay/callback.md`; query/refund in `monetize/zalopay/query-refund.md`.

## Purpose

Define how money starts moving via ZaloPay: an authenticated checkout creates a tracked `orders` row with a unique numeric `orderCode`, then creates a ZaloPay order (exact VND amount, app-scoped transaction id, redirect + callback addresses — key1-sealed) and hands the buyer the `order_url` (wallet, ATM, card, bank channels — VND only). The buyer pays in ZaloPay/app channels; the callback later grants entitlement. The same flow sells plan subscriptions and single products (`product_id?`), so shop majors plug in without a second payment path.

## Users

- Buyer (logged-in owner for plans; buyer-email claim for products per shop major): *As a Buyer, I want to start checkout, pay via the ZaloPay order page, land back on the platform so that money starts moving via ZaloPay as defined here.* starts checkout, pays via the ZaloPay order page, lands back on the platform.
- Creator/seller: *As a Creator/seller, I want to receive entitlement on callback grant so that money starts moving via ZaloPay as defined here.* receives entitlement on callback grant.
- Support/ops: *As a Support/ops, I want to look up an order by `orderCode` / `app_trans_id` / `zp_trans_id` to reconcile disputes so that money starts moving via ZaloPay as defined here.* looks up an order by `orderCode` / `app_trans_id` / `zp_trans_id` to reconcile disputes.

## Functional requirements

1. `POST /api/billing/checkout` (authenticated, Turnstile + per-IP/email rate limits): body `{provider: "zalopay", plan? | product_id?, buyer_email?, bank_code?}`. Server re-prices from canonical plan/product tables — client-supplied amounts are never trusted. Response: `{orderCode, app_trans_id, amount_vnd, order_url, expires_at}`.
2. `orderCode` generation: numeric int64 unique (`Date.now()*1000 + rand`), stored on `orders{id, user_id, provider: "zalopay", plan?, product_id?, amount_vnd, status}`. Integer VND, no decimals. Initial `status = pending`.
3. ZaloPay order creation (server-side, sandbox `sb-openapi.zalopay.vn` vs production host by env flag): `app_id`, `app_trans_id` in ZaloPay-conventional `yyMMdd_<orderCode>` shape (globally unique; confirm exact charset/length rules against the sandbox at implementation), `app_time`, `app_user` (owner id or buyer email), `amount` (= `amount_vnd`, integer VND), `description` (ASCII, embeds orderCode), `bank_code` (empty = all channels, or a pinned channel), `item` (JSON-string SKU list), `embed_data` (JSON string carrying the same-origin redirect URL), per-order `callback_url`, all sealed with `mac` = HMAC-SHA256 over `app_id|app_trans_id|app_user|amount|app_time|embed_data|item` with **key1** via WebCrypto. Response persists `order_url` (+ `zp_trans_token`/`qr_code` where returned) on the order.
4. Secrets (`ZALOPAY_APP_ID`, `ZALOPAY_KEY1`, `ZALOPAY_KEY2`) via `wrangler secret` / `.dev.vars` locally — never committed, never logged, never in the client bundle. key1 signs outbound calls; key2 verifies callbacks; never interchange them.
5. Buyer-email claim for course/digital products: binds `buyer_email` so the shop major issues the R2 signed download / lesson claim after the paid grant (delivery lives in the shop major).
6. Plan matrix and AI credits unchanged — see `docs/monetize/plans/subscription-plans.md`.

## Data / API touches

- `orders` gains `provider TEXT NOT NULL DEFAULT 'payos'` + `app_trans_id UNIQUE?` + `zp_trans_id?` + `expires_at?`, keeping `user_id TEXT NOT NULL` + index + RLS. `status` ∈ `pending|paid|cancelled|expired|failed|refunded`. Dashboard reads scope `WHERE user_id = session.userId`.
- Endpoints (JSON over `/api/*`, cookie session): `POST /api/billing/checkout`, `GET /api/billing/orders`, `GET /api/billing/orders/:orderCode` (buyer polls this after redirect — the redirect page never grants), `POST /api/billing/orders/:orderCode/cancel`. No direct DB from client; all PG via `worker/db/*`, least-privilege app role.
- Retry/double-click while pending: single row, same `orderCode`/`app_trans_id` (ZaloPay treats a repeated `app_trans_id` as the same order — never mint a second one for the same checkout).

## Acceptance criteria

- Sandbox checkout for plan and for product: ZaloPay order page opens, amount exact to the VND, `orders` row pending with unique `orderCode` and well-formed `app_trans_id`.
  *GWT: Given Sandbox checkout for plan and for product context; When the checked action occurs; Then ZaloPay order page opens, amount exact to the VND, `orders` row pending with unique `orderCode` and well-formed `app_trans_id`.*
- Amount tamper (client sends lower amount): server re-prices to canonical; mismatch logged.
  *GWT: Given Amount tamper (client sends lower amount) context; When server re-prices to canonical; Then mismatch logged.*
- Off-origin redirect/callback addresses: rejected 422 at order build.
  *GWT: Given Off-origin redirect/callback addresses context; When the checked action occurs; Then rejected 422 at order build.*
- Expired-unpaid order flips `expired` and is never grantable by a late callback (settlement doc owns the sweep).
  *GWT: Given the preconditions for this check hold; When expired-unpaid order flips `expired` and is never grantable by a late callback (settlement doc owns the sweep); Then the expected outcome stated here is observed.*
- IDOR probe: user A reading user B orders returns 403; RLS denies cross-user rows at DB.
  *GWT: Given IDOR probe context; When user A reading user B orders returns 403; Then RLS denies cross-user rows at DB.*
- Secrets probe: no app_id/key1/key2 in repo, logs, or client bundle (grep gate).
  *GWT: Given Secrets probe context; When the checked action occurs; Then no app_id/key1/key2 in repo, logs, or client bundle (grep gate).*

## Out of scope

- Callback verification and grant (see `monetize/zalopay/callback.md`); status query, expiry sweep, refunds (see `monetize/zalopay/query-refund.md`).
- Plan matrix, AI caps, agency skeleton (`docs/monetize/` — provider-agnostic, shared as-is); shop delivery; receipt emails (Resend; campaigns P4); P5 scale-out. PayOS, SePay, and VNPay paths untouched; multi-provider checkout UI is not in this feature.

## Open questions

- Q1: Order TTL — ZaloPay's ~15-min default payment window, or a longer window with local pending sweep?
- Q2: Guest checkout for products (buyer-email only) in v1, or login required for all purchases?
