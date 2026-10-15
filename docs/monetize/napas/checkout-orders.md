# NAPAS Checkout + Orders (dynamic VietQR per order)

PLAN ref: P3-1 adapted. Deviation flagged: PLAN specifies PayOS links; this feature collects over NAPAS rails via dynamic VietQR codes per explicit request. Same `orders` table (plus `provider` column) and same plan/product flow. Balance-sync grant in `monetize/napas/balance-sync.md`; exceptions/sweep in `monetize/napas/reconciliation.md`.

## Purpose

Define how money starts moving over NAPAS rails: an authenticated checkout creates a tracked `orders` row with a unique numeric `orderCode`, then mints a one-order dynamic VietQR code (company account + exact VND amount + ASCII content + short order ref, 15-minute validity) and hands the buyer its image/link. The buyer pays from any VietQR-capable bank/e-wallet app over the NAPAS 247 instant rail; the balance-sync push later grants entitlement. The same flow sells plan subscriptions and single products (`product_id?`), so shop majors plug in without a second payment path.

## Users

- Buyer (logged-in owner for plans; buyer-email claim for products per shop major): *As a Buyer, I want to start checkout, scan the dynamic QR, pay in their bank app within the validity window so that money starts moving over NAPAS rails as defined here.* starts checkout, scans the dynamic QR, pays in their bank app within the validity window.
- Creator/seller: *As a Creator/seller, I want to receive entitlement on matched-sync grant so that money starts moving over NAPAS rails as defined here.* receives entitlement on matched-sync grant.
- Support/ops: *As a Support/ops, I want to look up an order by `orderCode` / rail order ref to reconcile disputes so that money starts moving over NAPAS rails as defined here.* looks up an order by `orderCode` / rail order ref to reconcile disputes.

## Functional requirements

1. `POST /api/billing/checkout` (authenticated, Turnstile + per-IP/email rate limits): body `{provider: "napas", plan? | product_id?, buyer_email?}`. Server re-prices from canonical plan/product tables — client-supplied amounts are never trusted. Response: `{orderCode, napas_order_ref, amount_vnd, qr_image, qr_link, expires_at}`.
2. `orderCode` generation: numeric int64 unique (`Date.now()*1000 + rand`), stored on `orders{id, user_id, provider: "napas", napas_order_ref UNIQUE, plan?, product_id?, amount_vnd, status}`. Integer VND, no decimals. Initial `status = pending`.
3. Rail reference mapping (provider quirk): dynamic-QR fields are length-capped (`content` ≤ 19 ASCII chars, no special chars/diacritics; order ref ≤ 13 chars), so the full `orderCode` never travels raw. The server mints `napas_order_ref` (short, unique, ASCII) mapped 1:1 to `orderCode`, embeds the ref in both `content` and the order-ref field, and matches inbound syncs through the map. The QR payload locks amount + content: buyers cannot edit them for dynamic codes.
4. Dynamic-QR issuance (server-side, TEST vs GOLIVE hosts by env flag; bearer token from Basic-auth token call, ~300 s default validity, refreshed server-side): `bankCode + bankAccount` (company account), `amount`, `content`, `qrType = dynamic`, `transType = C`, order-ref field, account-holder name. Response persists the QR image/link + `expires_at` (15-minute provider validity). Company banking credentials live in `wrangler secret` / `.dev.vars` — never committed, logged, or client-visible.
5. Buyer-email claim for course/digital products: binds `buyer_email` so the shop major issues the R2 signed download / lesson claim after the paid grant (delivery lives in the shop major).
6. Plan matrix and AI credits unchanged — see `docs/monetize/plans/subscription-plans.md`.

## Data / API touches

- `orders` gains `provider TEXT NOT NULL DEFAULT 'payos'` + `napas_order_ref UNIQUE?` + `expires_at?`, keeping `user_id TEXT NOT NULL` + index + RLS. `status` ∈ `pending|paid|cancelled|expired|failed|refunded`. Dashboard reads scope `WHERE user_id = session.userId`.
- Endpoints (JSON over `/api/*`, cookie session): `POST /api/billing/checkout`, `GET /api/billing/orders`, `GET /api/billing/orders/:orderCode` (buyer polls this while waiting — the display page never grants), `POST /api/billing/orders/:orderCode/cancel`. No direct DB from client; all PG via `worker/db/*`, least-privilege app role.
- Retry/double-click while pending: single row, same `napas_order_ref`, fresh QR re-issued — never a duplicate row or a second live code for one order.

## Acceptance criteria

- Checkout for plan and for product: `orders` row pending with unique `orderCode` + mapped `napas_order_ref`; QR scans to the exact amount with locked content.
  *GWT: Given Checkout for plan and for product context; When `orders` row pending with unique `orderCode` + mapped `napas_order_ref`; Then QR scans to the exact amount with locked content.*
- Amount tamper (client sends lower amount): server re-prices to canonical; mismatch logged.
  *GWT: Given Amount tamper (client sends lower amount) context; When server re-prices to canonical; Then mismatch logged.*
- UAT: provider test-callback simulation flips a test order through the sync path end-to-end.
  *GWT: Given UAT context; When the checked action occurs; Then provider test-callback simulation flips a test order through the sync path end-to-end.*
- Unpaid past the 15-minute window: order flips `expired`; the QR is dead and late payments follow the settlement doc (never auto-grant).
  *GWT: Given Unpaid past the 15-minute window context; When order flips `expired`; Then the QR is dead and late payments follow the settlement doc (never auto-grant).*
- IDOR probe: user A reading user B orders returns 403; RLS denies cross-user rows at DB.
  *GWT: Given IDOR probe context; When user A reading user B orders returns 403; Then RLS denies cross-user rows at DB.*
- Secrets probe: no banking/API credentials in repo, logs, or client bundle (grep gate).
  *GWT: Given Secrets probe context; When the checked action occurs; Then no banking/API credentials in repo, logs, or client bundle (grep gate).*

## Out of scope

- Balance-sync verification and grant (see `monetize/napas/balance-sync.md`); unmatched/late transfers, reconciliation sweep, refunds (see `monetize/napas/reconciliation.md`).
- Plan matrix, AI caps, agency skeleton (`docs/monetize/` — provider-agnostic, shared as-is); shop delivery; receipt emails (Resend; campaigns P4); P5 scale-out. PayOS, SePay, VNPay, and ZaloPay paths untouched; multi-provider checkout UI is not in this feature.

## Open questions

- Q1: Rail package — standard (amount+content reconciliation, ~10–15 s sync) vs priority (sync independent of amount/content, ~1–3 s, refund support)?
- Q2: Guest checkout for products (buyer-email only) in v1, or login required for all purchases?
