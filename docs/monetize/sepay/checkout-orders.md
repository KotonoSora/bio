# SePay Checkout + Orders (bank-transfer initiation)

PLAN ref: P3-1 adapted. Deviation flagged: PLAN specifies PayOS payment links; this feature uses SePay bank-transfer collection per explicit request. Same `orders` table and same plan/product flow — only the collection mechanism differs. Webhook grant in `monetize/sepay/webhook.md`; unmatched-transfer handling in `monetize/sepay/transaction-matching.md`.

## Purpose

Define how money starts moving via SePay: an authenticated checkout creates a tracked `orders` row with a unique numeric `orderCode`, then returns VietQR/transfer instructions (company account + exact VND amount + transfer content embedding the `orderCode`) instead of a hosted payment link. The buyer pays from any VN bank app; the SePay webhook later matches the transfer and grants entitlement. The same flow sells plan subscriptions and single products (`product_id?`), so shop majors plug in without a second payment path.

## Users

- Buyer (logged-in owner for plans; buyer-email claim for products per shop major): *As a Buyer, I want to start checkout, scan VietQR or copy transfer details, pay from their bank app so that money starts moving via SePay as defined here.* starts checkout, scans VietQR or copies transfer details, pays from their bank app.
- Creator/seller: *As a Creator/seller, I want to receive entitlement on matched-transfer grant so that money starts moving via SePay as defined here.* receives entitlement on matched-transfer grant.
- Support/ops: *As a Support/ops, I want to look up an order by `orderCode`, or hunt an unmatched transfer by content/amount in the SePay dashboard so that money starts moving via SePay as defined here.* looks up an order by `orderCode`, or hunts an unmatched transfer by content/amount in the SePay dashboard.

## Functional requirements

1. `POST /api/billing/checkout` (authenticated, Turnstile + per-IP/email rate limits): body `{provider: "sepay", plan? | product_id?, buyer_email?}`. Server re-prices from canonical plan/product tables — client-supplied amounts are never trusted. Response: `{orderCode, amount_vnd, bank, account_number, account_name, transfer_content, vietqr_payload, expires_at}`.
2. `orderCode` generation: numeric int64 unique (`Date.now()*1000 + rand`), stored on `orders{id, user_id, provider: "sepay", plan?, product_id?, amount_vnd, status}`. `amount` integer VND, no decimals. Initial `status = pending`.
3. Transfer content: fixed ASCII template embedding the code (e.g. `RB{orderCode}`, uppercase, no diacritics/spaces — bank contents mangle both). Displayed verbatim plus inside the VietQR payload so scanners prefill it; copy-button provided because buyers edit contents most often at this step.
4. Pending-order expiry: configurable TTL (default 30–60 min, see Open Q1); expired orders flip to `expired` and their codes are never recycled while any transfer could still match (grace window in `monetize/sepay/transaction-matching.md`).
5. Buyer-email claim for course/digital products: the order binds `buyer_email` so the shop major issues the R2 signed download / lesson claim after the paid grant (delivery itself lives in the shop major).
6. Same `orders` table as PayOS (`provider` column distinguishes); plan matrix and AI credits unchanged — see `docs/monetize/plans/subscription-plans.md`.

## Data / API touches

- `orders` gains `provider TEXT NOT NULL DEFAULT 'payos'` + `transfer_content TEXT?` + `expires_at?`, keeping existing `user_id TEXT NOT NULL` + index + RLS policy. `status` ∈ `pending|paid|cancelled|expired|failed`. Dashboard reads scope `WHERE user_id = session.userId`.
- Endpoints (JSON over `/api/*`, cookie session): `POST /api/billing/checkout`, `GET /api/billing/orders`, `GET /api/billing/orders/:orderCode`, `POST /api/billing/orders/:orderCode/cancel`. No direct DB from client; all PG via `worker/db/*`, least-privilege app role.
- Company bank details served from server config (env, never client input); secrets and SePay API keys via `wrangler secret` / `.dev.vars` — never committed, never logged, never in the client bundle.
- Optional v2: per-order Virtual Accounts via SePay API v2 (bank-dependent: BIDV, Sacombank, Vietcombank, VietinBank, Shinhan) as an alternative to shared-account + code matching; not required for launch.

## Acceptance criteria

- Checkout for plan and for product: `orders` row pending with unique `orderCode`, VietQR scans to the exact amount with `transfer_content` prefilled, details render ASCII-safe.
  *GWT: Given Checkout for plan and for product context; When the checked action occurs; Then `orders` row pending with unique `orderCode`, VietQR scans to the exact amount with `transfer_content` prefilled, details render ASCII-safe.*
- Amount tamper (client sends lower amount): server re-prices to the canonical amount; mismatch logged.
  *GWT: Given Amount tamper (client sends lower amount) context; When server re-prices to the canonical amount; Then mismatch logged.*
- Retry/double-click while pending: single row, single `orderCode`, same transfer details re-issued — no duplicate rows.
  *GWT: Given Retry/double-click while pending context; When the checked action occurs; Then single row, single `orderCode`, same transfer details re-issued — no duplicate rows.*
- Cancel sets `cancelled`; TTL lapse sets `expired`; both stop matching (except the settlement grace rule).
  *GWT: Given Cancel sets `cancelled`; When the next step executes; Then both stop matching (except the settlement grace rule).*
- IDOR probe: user A reading user B orders returns 403; RLS denies cross-user rows at DB.
  *GWT: Given IDOR probe context; When user A reading user B orders returns 403; Then RLS denies cross-user rows at DB.*
- Secrets probe: no SePay key or bank credential in repo, logs, or client bundle (grep gate).
  *GWT: Given Secrets probe context; When the checked action occurs; Then no SePay key or bank credential in repo, logs, or client bundle (grep gate).*

## Out of scope

- Webhook verification and grant (see `monetize/sepay/webhook.md`); unmatched/wrong-amount transfer handling (see `monetize/sepay/transaction-matching.md`).
- Plan matrix, AI credits, agency skeleton (`docs/monetize/plans/`, `docs/monetize/organizations/` — provider-agnostic, reused as-is).
- Product catalog, signed-URL delivery, course progress (shop major); receipt emails (Resend; campaigns P4); P5 scale-out. PayOS remains the alternate provider path (`docs/monetize/`); dual-provider checkout UI is not in this feature.

## Open questions

- Q1: Pending-order TTL — 30 min, 60 min, or no expiry with indefinite matching?
- Q2: Guest checkout for products (buyer-email only) in v1, or login required for all purchases?
