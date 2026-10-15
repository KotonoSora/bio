# Apple Pay Checkout + Orders (tokenized card collection)

PLAN ref: P3-1 adapted. Deviation flagged: PLAN specifies PayOS links; this feature collects via Apple Pay tokenized checkout per explicit request. Same `orders` table (plus `provider` column) and same plan/product flow. Merchant validation + charge/grant in `monetize/apple-pay/authorization.md`; pending/refund/dispute handling in `monetize/apple-pay/pending-refund.md`.

## Purpose

Define how money starts moving via Apple Pay: an authenticated checkout creates a tracked `orders` row with a unique numeric `orderCode`, then starts an Apple Pay sheet (merchant-validated session, exact VND total). The buyer authenticates with biometrics; the encrypted payment token goes to our server, which charges it through the chosen payment service provider (PSP). Apple Pay tokenizes — it never settles — so the PSP executes and confirms every dong. Apple Pay only renders on capable devices/browsers, so graceful fallback to sibling providers is part of this spec, not an afterthought.

## Users

- Buyer on a capable Apple device/browser (logged-in owner for plans; buyer-email claim for products per shop major): *As a Buyer on a capable Apple device/browser, I want to start checkout, pay in the sheet, see confirmation or a pending state so that money starts moving via Apple Pay as defined here.* starts checkout, pays in the sheet, sees confirmation or a pending state.
- Buyer on an incapable device/browser: *As a Buyer on an incapable device/browser, I want to never see a dead button — is offered the sibling providers instead so that money starts moving via Apple Pay as defined here.* never sees a dead button — is offered the sibling providers instead.
- Creator/seller: *As a Creator/seller, I want to receive entitlement on charge-confirmed grant so that money starts moving via Apple Pay as defined here.* receives entitlement on charge-confirmed grant.
- Support/ops: *As a Support/ops, I want to look up an order by `orderCode` / PSP transaction id to reconcile disputes and chargebacks so that money starts moving via Apple Pay as defined here.* looks up an order by `orderCode` / PSP transaction id to reconcile disputes and chargebacks.

## Functional requirements

1. `POST /api/billing/checkout` (authenticated, Turnstile + per-IP/email rate limits): body `{provider: "applepay", plan? | product_id?, buyer_email?}`. Server re-prices from canonical plan/product tables — client-supplied amounts are never trusted. Response: `{orderCode, amount_vnd, applepay: {merchant_id, country_code: "VN", currency_code: "VND", total, merchant_capabilities, supported_networks}}`. No secrets or certificates in this response.
2. `orderCode` generation: numeric int64 unique (`Date.now()*1000 + rand`), stored on `orders{id, user_id, provider: "applepay", plan?, product_id?, amount_vnd, status}`. Integer VND in our DB, no float math; the server formats the sheet `total.amount` string from it (exact normalization confirmed in sandbox per PSP).
3. Client gating + sheet (dashboard `/app/*`): render the Apple Pay button only when `ApplePaySession.canMakePayment()` (and prefer `canMakePaymentsWithActiveCard` where available); otherwise show sibling-provider options. Session request carries `countryCode: VN`, `currencyCode: VND`, `merchantCapabilities: [supports3DS, supportsCredit, supportsDebit]`, `supportedNetworks` (e.g. visa/masterCard, finalized per PSP at implementation), and `total {label, amount}`. `onvalidatemerchant` always calls our server validation endpoint (never Apple directly — Apple warns against client-side session requests); `onpaymentauthorized` posts the `PKPaymentToken` to our authorize endpoint and closes via `completePayment` on the server's verdict.
4. Token intake `POST /api/billing/applepay/authorize` (authenticated, same session/order owner): body `{orderCode, token: {paymentData, transactionIdentifier, paymentMethod}}`. Server validates order ownership + `pending` status + amount binding, then charges via the PSP adapter — self-decryption of `paymentData` is forbidden (the processing private key lives with the PSP; holding it would drag us into card-data scope). Raw token material is never logged or stored beyond the PSP's reference.
5. PSP selection constraint (Open Q1 is WHICH provider, not whether): the PSP must accept Apple Pay tokens AND settle VND to a VN business. The same internal adapter shape as the Google Pay minor (`charge / status / refund / webhook-verify`) applies, so one PSP may serve both wallets — decide jointly, not twice.
6. Buyer-email claim for course/digital products: binds `buyer_email` so the shop major issues the R2 signed download / lesson claim after the paid grant (delivery lives in the shop major).
7. Plan matrix and AI credits unchanged — see `docs/monetize/plans/subscription-plans.md`.

## Data / API touches

- `orders` gains `provider TEXT NOT NULL DEFAULT 'payos'` + `psp_txn_id?` + `psp_name?`, keeping `user_id TEXT NOT NULL` + index + RLS. `status` ∈ `pending|authorizing|paid|cancelled|expired|failed|refunded|disputed`. Dashboard reads scope `WHERE user_id = session.userId`.
- Endpoints (JSON over `/api/*`, cookie session): `POST /api/billing/checkout`, `POST /api/billing/applepay/validate-merchant`, `POST /api/billing/applepay/authorize`, `GET /api/billing/orders`, `GET /api/billing/orders/:orderCode` (buyer polls this after the sheet closes), `POST /api/billing/orders/:orderCode/cancel`. Static: `/.well-known/apple-developer-merchantid-domain-association` served byte-identical with no redirect (see acceptance). No direct DB from client; all PG via `worker/db/*`, least-privilege app role.
- Apple-side prerequisites (launch checklist, not code): Merchant ID, Payment Processing Certificate + Merchant Identity Certificate with renewal calendar (both expire), every checkout domain (prod + staging) registered and verified, Wallet/Console-side PSP linkage where required.
- Retry/double-click while pending: single row, same `orderCode`; concurrent authorize calls serialized per order — one charge attempt in flight at a time.

## Acceptance criteria

- Capable-device UAT (PSP sandbox + Apple sandbox/test cards): checkout → validated sheet → token → charge → `paid` + entitlement within the SLA.
  *GWT: Given Capable-device UAT (PSP sandbox + Apple sandbox/test cards): checkout; When the transition executes; Then validated sheet → token → charge → `paid` + entitlement within the SLA.*
- Incapable browser/device: no Apple Pay button rendered; sibling providers offered; zero console errors.
  *GWT: Given Incapable browser/device context; When no Apple Pay button rendered; Then zero console errors.*
- Domain association file: byte-identical at `/.well-known/…` with HTTP 200, no redirect, reachable by Apple's verifiers (SPA fallback must not swallow it); domain shows Verified.
  *GWT: Given Domain association file context; When byte-identical at `/.well-known/…` with HTTP 200, no redirect, reachable by Apple's verifiers (SPA fallback must not swallow it); Then domain shows Verified.*
- Amount tamper (client alters total): server-side binding rejects before any PSP call; mismatch logged.
  *GWT: Given Amount tamper (client alters total) context; When server-side binding rejects before any PSP call; Then mismatch logged.*
- Raw token material in logs, DB (beyond PSP reference), or client storage: none (grep + review gate).
  *GWT: Given the preconditions for this check hold; When raw token material in logs, DB (beyond PSP reference), or client storage: none (grep + review gate); Then the expected outcome stated here is observed.*
- Stuck "authorizing" past timeout: recoverable via the settlement doc's status path, never double-charged.
  *GWT: Given Stuck "authorizing" past timeout context; When the checked action occurs; Then recoverable via the settlement doc's status path, never double-charged.*
- IDOR probe: user A operating on user B orders returns 403; RLS denies cross-user rows at DB.
  *GWT: Given IDOR probe context; When user A operating on user B orders returns 403; Then RLS denies cross-user rows at DB.*
- Secrets probe: no PSP keys, certificates, or private keys in repo, logs, or client bundle (grep gate).
  *GWT: Given Secrets probe context; When the checked action occurs; Then no PSP keys, certificates, or private keys in repo, logs, or client bundle (grep gate).*

## Out of scope

- Merchant validation details and charge confirmation/grant (see `monetize/apple-pay/authorization.md`); pending/3DS, status sweep, refunds, chargebacks (see `monetize/apple-pay/pending-refund.md`).
- Plan matrix, AI caps, agency skeleton (`docs/monetize/` — provider-agnostic, shared as-is); shop delivery; receipt emails (Resend; campaigns P4); P5 scale-out. All other provider paths untouched; multi-provider checkout UI is not in this feature.

## Open questions

- Q1: WHICH PSP (Apple Pay tokens + VND settlement to VN business) — same provider as Google Pay for one adapter family and shared fee terms?
- Q2: Guest checkout for products (buyer-email only) in v1, or login required for all purchases?
