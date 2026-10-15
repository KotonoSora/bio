# Google Pay Checkout + Orders (tokenized card collection)

PLAN ref: P3-1 adapted. Deviation flagged: PLAN specifies PayOS links; this feature collects via Google Pay tokenized checkout per explicit request. Same `orders` table (plus `provider` column) and same plan/product flow. Token charge + grant in `monetize/google-pay/authorization.md`; pending/refund/dispute handling in `monetize/google-pay/pending-refund.md`.

## Purpose

Define how money starts moving via Google Pay: an authenticated checkout creates a tracked `orders` row with a unique numeric `orderCode`, then returns a Google Pay session configuration (gateway tokenization parameters + exact VND amount). The buyer authorizes in the Google Pay sheet without leaving the page; the returned payment token goes to our server, which charges it through the chosen payment service provider (PSP). Google Pay tokenizes — it never settles — so the PSP executes and confirms every dong.

## Users

- Buyer (logged-in owner for plans; buyer-email claim for products per shop major): *As a Buyer, I want to start checkout, authoriz in the Google Pay sheet, see immediate confirmation or a pending state so that money starts moving via Google Pay as defined here.* starts checkout, authorizes in the Google Pay sheet, sees immediate confirmation or a pending state.
- Creator/seller: *As a Creator/seller, I want to receive entitlement on charge-confirmed grant so that money starts moving via Google Pay as defined here.* receives entitlement on charge-confirmed grant.
- Support/ops: *As a Support/ops, I want to look up an order by `orderCode` / PSP transaction id to reconcile disputes and chargebacks so that money starts moving via Google Pay as defined here.* looks up an order by `orderCode` / PSP transaction id to reconcile disputes and chargebacks.

## Functional requirements

1. `POST /api/billing/checkout` (authenticated, Turnstile + per-IP/email rate limits): body `{provider: "googlepay", plan? | product_id?, buyer_email?}`. Server re-prices from canonical plan/product tables — client-supplied amounts are never trusted. Response: `{orderCode, amount_vnd, googlepay: {environment, merchant_id, merchant_name, gateway, gateway_merchant_id, allowed_networks, transaction_info}}`. No secrets in this response.
2. `orderCode` generation: numeric int64 unique (`Date.now()*1000 + rand`), stored on `orders{id, user_id, provider: "googlepay", plan?, product_id?, amount_vnd, status}`. Integer VND in our DB, no float math; the server formats the Google `totalPrice` string and the PSP minor-unit amount from it (exact normalization rule confirmed in sandbox per PSP).
3. Client sheet (dashboard `/app/*`): Google Pay button (per brand guidelines) → `paymentsClient` in `TEST`/`PRODUCTION` by env → `PaymentDataRequest` with `allowedPaymentMethods: CARD` + `tokenizationSpecification: {type: PAYMENT_GATEWAY, gateway, gatewayMerchantId}` (Direct/PAN_ONLY integration is forbidden — it requires QSA-validated PCI DSS scope we do not hold) + `merchantInfo {merchantId (Google-issued via Wallet Console), merchantName}` + `transactionInfo {countryCode: VN, currencyCode: VND, totalPrice, totalPriceStatus: FINAL}`. Optional `onPaymentAuthorized` pre-check validates amount/order state server-side before the sheet closes.
4. Token intake `POST /api/billing/googlepay/authorize` (authenticated, same session/order owner): body `{orderCode, payment_token}`. Server validates token shape, order ownership + `pending` status + amount binding, then charges via the PSP adapter — never logs or stores raw PAN/cryptogram material beyond the PSP's reference.
5. PSP selection constraint (Open Q1 is WHICH provider, not whether): the PSP must sit on Google's participating-processors list for gateway tokens AND settle VND to a VN business. Until chosen, all PSP calls go through a narrow internal adapter (`charge / status / refund / webhook-verify`) so swapping providers is config + adapter work, never a rewrite.
6. Buyer-email claim for course/digital products: binds `buyer_email` so the shop major issues the R2 signed download / lesson claim after the paid grant (delivery lives in the shop major).
7. Plan matrix and AI credits unchanged — see `docs/monetize/plans/subscription-plans.md`.

## Data / API touches

- `orders` gains `provider TEXT NOT NULL DEFAULT 'payos'` + `psp_txn_id?` + `psp_name?`, keeping `user_id TEXT NOT NULL` + index + RLS. `status` ∈ `pending|authorizing|paid|cancelled|expired|failed|refunded|disputed`. Dashboard reads scope `WHERE user_id = session.userId`.
- Endpoints (JSON over `/api/*`, cookie session): `POST /api/billing/checkout`, `POST /api/billing/googlepay/authorize`, `GET /api/billing/orders`, `GET /api/billing/orders/:orderCode` (buyer polls this after the sheet closes), `POST /api/billing/orders/:orderCode/cancel`. No direct DB from client; all PG via `worker/db/*`, least-privilege app role.
- Google-side prerequisites (tracked as launch checklist, not code): ToS + Acceptable Use acceptance, Wallet Console production access after integration testing, registered domains, brand-guideline-compliant button.
- Retry/double-click while pending: single row, same `orderCode`; concurrent authorize calls serialized per order — one charge attempt in flight at a time.

## Acceptance criteria

- UAT with Google sample tokens in `TEST` environment: checkout → sheet → token → PSP-sandbox charge → `paid` + entitlement visible within the SLA.
  *GWT: Given UAT with Google sample tokens in `TEST` environment: checkout; When the transition executes; Then sheet → token → PSP-sandbox charge → `paid` + entitlement visible within the SLA.*
- Amount tamper (client alters totalPrice): server-side binding rejects before any PSP call; mismatch logged.
  *GWT: Given Amount tamper (client alters totalPrice) context; When server-side binding rejects before any PSP call; Then mismatch logged.*
- Direct/PAN_ONLY tokenization attempt: refused by configuration (gateway-only allowlist, grep gate on tokenization type).
  *GWT: Given Direct/PAN_ONLY tokenization attempt context; When the checked action occurs; Then refused by configuration (gateway-only allowlist, grep gate on tokenization type).*
- Raw token/PAN material in logs, DB (beyond PSP reference), or client storage: none (grep + review gate).
  *GWT: Given the preconditions for this check hold; When raw token/PAN material in logs, DB (beyond PSP reference), or client storage: none (grep + review gate); Then the expected outcome stated here is observed.*
- Stuck "authorizing" past timeout: order recovers via the settlement doc's status path, never double-charges.
  *GWT: Given Stuck "authorizing" past timeout context; When the checked action occurs; Then order recovers via the settlement doc's status path, never double-charges.*
- IDOR probe: user A operating on user B orders returns 403; RLS denies cross-user rows at DB.
  *GWT: Given IDOR probe context; When user A operating on user B orders returns 403; Then RLS denies cross-user rows at DB.*
- Secrets probe: no PSP keys or Google merchant credentials in repo, logs, or client bundle (grep gate).
  *GWT: Given Secrets probe context; When the checked action occurs; Then no PSP keys or Google merchant credentials in repo, logs, or client bundle (grep gate).*

## Out of scope

- Token charge confirmation and grant (see `monetize/google-pay/authorization.md`); pending/3DS, status sweep, refunds, chargebacks (see `monetize/google-pay/pending-refund.md`).
- Plan matrix, AI caps, agency skeleton (`docs/monetize/` — provider-agnostic, shared as-is); shop delivery; receipt emails (Resend; campaigns P4); P5 scale-out. PayOS, SePay, VNPay, ZaloPay, and NAPAS paths untouched; multi-provider checkout UI is not in this feature.

## Open questions

- Q1: WHICH PSP (must support Google Pay gateway tokens + VND settlement to a VN business) — decision drives gateway/gatewayMerchantId values and fee terms?
- Q2: Guest checkout for products (buyer-email only) in v1, or login required for all purchases?
