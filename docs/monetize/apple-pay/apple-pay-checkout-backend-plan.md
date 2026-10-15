# Apple Pay Checkout Backend Plan (order + sheet + token intake — server build steps)

PLAN ref: P3-1 adapted (explicit `provider: "applepay"`, re-priced server-side, numeric `orderCode`, tokenized sheet) + S-1/S-5/S-6/S-7. BA source: `docs/monetize/apple-pay/checkout.md`. Phasing per `docs/platform/delivery/execution-overview.md` (E3). Contracts in `docs/monetize/apple-pay/apple-pay-checkout-database-plan.md` (columns, association file, serialization — never redefined here). Sheet/button screens in the checkout frontend plan (splits next to this file).

## Purpose

Give the backend developer an ordered procedure for Apple Pay checkout: authenticated order creation with explicit provider, sheet parameters with zero secrets, capability-gated client contract, and token intake that validates ownership/status/amount before any PSP call — with self-decryption forbidden structurally. Follow steps 1–8 in order. Apple Pay tokenizes; it never settles — the PSP executes and confirms every dong.

## Users

- Backend developer: executes steps 1–8 in package `com.app.billing.rail.applepay` (rail adapter behind the uniform `next` shape).
- QA: runs capable/incapable-device UAT, tamper, hygiene, stuck-authorizing, IDOR, and secrets drills (PSP + Apple sandboxes).
- Buyer/creator/support: pays, gets entitled on confirmed charge, reconciles by `orderCode`/PSP id.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — monolith rail adapter (in-process builder behind the dispatcher). Monolith pros: eligibility + re-price + create + sheet-params in one request scope; token intake joins order checks locally; one idempotency story with the dispatcher. Microservices cons avoided: a rail service would split create-from-intake and duplicate no-default enforcement per rail. Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; none fire. Starters: web, validation, data-jpa, security, cache, actuator, micrometer. Load posture: order creation is dispatcher-paced (indexed idempotency lookups); token intake serialized per order (one charge in flight — concurrent authorizes queue, never parallelize); PSP calls bounded (timeout + circuit-breaker per PSP client, preventing slow-PSP thread exhaustion).
2. Step 1 — `POST /api/billing/checkout {provider: "applepay", plan?|product_id?, buyer_email?}` (authenticated, Turnstile + per-IP/email buckets): validate eligibility (plan/product × applepay matrix + kill switch + device capability is client-declared here, server-filtered at methods time) → re-price from catalogs (client amounts never trusted) → create one `orders` row (`provider` immutable, int64 `orderCode` unique with retry, `status=pending`) → respond `{orderCode, amount_vnd, applepay: {merchant_id, country_code: "VN", currency_code: "VND", total, merchant_capabilities: [supports3DS, supportsCredit, supportsDebit], supported_networks}}` (finalized per PSP at implementation). No secrets, certificates, or keys in this response (asserted in test — response-shape allowlist, not review).
3. Step 2 — sheet contract (server-authored, client-executed): session request carries `countryCode: VN`, `currencyCode: VND`, capabilities, networks, `total {label, amount}` formatted from `amount_vnd` (exact normalization confirmed in sandbox). `onvalidatemerchant` MUST call our validation endpoint (never Apple directly — client-side session requests fail review); `onpaymentauthorized` posts `{orderCode, token: {paymentData, transactionIdentifier, paymentMethod}}` to authorize and closes via `completePayment` on the server verdict.
4. Step 3 — `POST /api/billing/applepay/authorize` (token intake, same session/order owner): validate ownership + `pending` status + amount binding (charged == stored, mismatch → reject pre-PSP + alert) + `transactionIdentifier` freshness (reused identifiers rejected — cross-order replay dies here) → hand to the charge path (authorization slice) under per-order serialization. Self-decryption of `paymentData` FORBIDDEN (processing private key lives with the PSP; holding it drags the platform into card-data scope — no decryption code exists to audit, asserted by absence).
5. Step 4 — double-click collapse: same key+fingerprint → same row + same handoff (dispatcher idempotency); keyless rapid clicks → fingerprint window dedupe; concurrent authorizes serialize (one charge attempt in flight — second observes, never duplicates).
6. Step 5 — buyer-email claim (products): binds `buyer_email` for shop delivery on paid grant (delivery owned by shop major; this slice passes the claim through, never interprets it).
7. Step 6 — PSP constraint (open Q1 is WHICH, not whether): PSP must accept Apple Pay tokens AND settle VND to a VN business; internal adapter shape `charge/status/refund/webhook-verify` shared with Google Pay (one PSP may serve both wallets — decide jointly once, fee terms shared).
8. Step 7 — abuse: Turnstile + per-IP/email buckets on checkout/authorize (S-5, card-tier velocity); token-intake floods throttled before PSP calls (PSP call volume is money — throttling here is cost defense); device/session anomaly (sheet opened, token posted from different session) logged for review.
9. Step 8 — tests + container: capable-device UAT (sandbox chain to `paid` + entitlement within SLA); incapable-device test (no button, siblings offered, zero console errors); association-file test (byte-identical 200, no redirect, fallback-excluded); tamper test (altered totals rejected pre-PSP); hygiene test (token material nowhere — grep + log-capture + state-shape tests); stuck-authorizing recovery test (settlement path, no double charge); IDOR test (403 + RLS deny); secrets test (no PSP keys/certs/private keys anywhere). Same image via `docker run` + `podman run` per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`.

## Data / API touches

- Schema: database plan above (no tables here; creation executes the session slice's insert path). Endpoints: `POST /api/billing/checkout`, `POST /api/billing/applepay/validate-merchant` (owned by authorization slice; listed as the sheet's mandatory hop), `POST /api/billing/applepay/authorize`, `GET /api/billing/orders`, `GET /api/billing/orders/:orderCode` (poll), `POST /:orderCode/cancel`. Static: association file (database plan rule 3). Metrics: `applepay.authorize_rejected{reason}`, PSP call latency/outcome.

## Acceptance criteria

- UAT drill: sandbox chain green end-to-end (sheet → token → charge → `paid` + entitlement).
- Gating drill: incapable devices see siblings only, zero errors.
- Association drill: verifiers green per environment.
- Tamper/hygiene/race drills per step 8, all green with logged evidence.
- Container drill: digest pair boots, compose green, rollback green, contract diff clean.

## Out of scope

- Column/file contracts (database plan above); button/sheet screens (frontend plan); validation/charge/grant (authorization plans); pending/sweep/refund/dispute (pending-refund plans); `orders` DDL (session database plan); plan matrix/AI/agency; shop delivery; receipts; other rails; P5.

## Open questions

- Q1: WHICH PSP (see database plan Q1 — joint with Google Pay)?
- Q2: Guest checkout policy (see database plan Q2 — single session decision)?
