# Checkout Session - API Contracts and Payloads (feature 33)

Phase 2 TL · Endpoints in `billing-service`; conventions from `docs/02-architecture-techlead/32-monetize-overview/03-api-contracts-and-payloads.md`. Security: `docs/02-architecture-techlead/33-checkout-session/05-security-logging-and-rate-limiting.md`. BA sources: `docs/01-requirements-ba/33-checkout-session/`.

## Endpoint map

| Method & path | Auth | Request | Success | Errors (code) |
|---|---|---|---|---|
| `POST /api/v1/billing/quote` | session; guest allowed with email context | `QuoteRequest` | 200 `QuoteView` | 404 `product_absent`/`plan_not_found`; 409 `product_not_live`/`schedule_not_published`/`plan_not_published`; 422 `unknown_provider`, `term_required`, `term_invalid`, `plan_not_purchasable`; 429 |
| `GET /api/v1/billing/payment-methods` | session; guest-safe | query `plan?|productId?` | 200 `PaymentMethodsView` | 404; 429 |
| `POST /api/v1/billing/checkout` | session or guest + Turnstile | `CheckoutCreateRequest` + `Idempotency-Key?` | 201 `CheckoutView` | 404/409 passthroughs; 422 `unknown_provider`, `term_required`, `term_invalid`, `plan_not_purchasable`, `coupon_invalid`, `coupon_expired`, `email_required`, `return_url_invalid`, `ship_to_required`, `idempotency_key_reuse`; 409 `amount_changed`; 410 `quote_expired`; 502 `handoff_failed`; 429 |
| `POST /api/v1/billing/orders/{orderCode}/cancel` | session owner (support/ops via ops route) | - | 200 `CancelView` | 403; 404; 409 `cancel_conflict`; 429 |
| `POST /internal/v1/billing/checkout` | service JWT (`aud=billing-service`) | `InternalCheckoutRequest` | 201 `CheckoutView` | same as public minus Turnstile; 403 caller-scope; 429 |

Order reads (`GET /api/v1/billing/orders{,/{orderCode}}`) and the grant step belong to feature 34; the pending page polls 34's read.

## Kotlin DTOs

```kotlin
data class QuoteRequest(val plan: String? = null, val term: String? = null, val productId: String? = null,
  val provider: String? = null, val couponCode: String? = null)
data class QuoteView(val amountVnd: Long, val provider: String?, val eligibleMethods: List<String>,
  val catalogVersion: String, val quoteToken: String, val expiresAt: Instant)
data class ShipTo(val name: String, val phone: String, val address: String)
data class CheckoutCreateRequest(val provider: String, val plan: String? = null, val term: String? = null, val productId: String? = null,
  val buyerEmail: String? = null, val couponCode: String? = null, val proformaRef: String? = null,
  val returnUrl: String? = null, val cancelUrl: String? = null,
  val shipTo: ShipTo? = null, val extras: Map<String, String>? = null,
  val quoteToken: String? = null, val leadOptIn: Boolean = false)
data class Handoff(val kind: String /* url|qr|sheet|token|none */, val payload: JsonNode)
data class CheckoutView(val orderCode: Long, val status: String, val amountVnd: Long,
  val expiresAt: Instant, val next: Handoff, val proformaRepriced: Boolean = false)
data class MethodView(val provider: String, val displayName: String, val handoffKind: String,
  val disabledReason: String? = null)
data class PaymentMethodsView(val methods: List<MethodView>, val configVersion: Long)
data class CancelView(val orderCode: Long, val status: String)
data class OnBehalfOf(val userId: String? = null, val buyerEmail: String? = null)   // exactly one
data class InternalCheckoutRequest(val onBehalfOf: OnBehalfOf, val provider: String,
  val plan: String? = null, val term: String? = null, val productId: String? = null, val extras: Map<String, String>? = null,
  val shipTo: ShipTo? = null, val returnUrl: String? = null, val cancelUrl: String? = null,
  val leadOptIn: Boolean = false)
```

## Rules

- Validation ladder (fail closed, in order): target exists/live → plan intents: `term` required/valid + active term row → provider in registry + eligible → coupon valid → email deliverable (guests) → URLs same-origin (`/app/billing/{return,cancel}`, `orderCode` only) → ship-to present for `merch` → quote token (if sent: expired → 410; amount/catalog mismatch → 409) → `checkout-context` price freeze → `fees/quote` snapshot → insert. Any failure creates no row and leaks no catalog internals.
- `quoteToken` = `qt1.<base64url(payload)>.<base64url(HMAC-SHA256(secret, payload))>`; payload = `{intent, amountVnd, catalogVersion, issuedAt, expiresAt}`; 15-min validity; secret via env/vault; verification is constant-time.
- Fingerprint = `sha256(userId|kind|intent|provider|amountVnd|returnUrl|cancelUrl)` (`intent` = `plan|term` for plan intents, product id otherwise); stored on keyed creates; keyless collapse key `bill:fp:{fingerprint}` (TTL 90 s → order_code).
- Handoff: the stub adapter (non-prod) implements `next{kind,payload}` so drills run without PSP keys; real rail adapters land in 40 without contract change. `kind` is stable; `payload` is adapter-defined.
- Coupons (39's pricing service is the only discount authority): create re-validates `couponCode`, stores the returned math as `discount_vnd`/`amount_vnd`, and a 0-VND result mints `provider='promo'` server-side (reserved literal; clients sending it get `unknown_provider`), skips handoff (`kind='none'`) and grants immediately through the canonical checked path; coupon orders that end without a grant restore the campaign decrement exactly once via the terminal transition (sweep/cancel included).
- Guest create: `buyerEmail` mandatory; `user_id` minted as in D-8; the response never echoes the email (masked display only).
- Proforma link (37): `proformaRef` on plan/product creates only; resolved owner-scoped while `open` and unexpired -> quote amount equal to the created amount links (`orders.proforma_id`; conversion happens in 34's grant); drift supersedes the proforma at create and sets `proformaRepriced=true` (the charged amount is always the live amount, never the stale quote); absent/foreign/expired refs are ignored with the same flag false - no enumeration.
- Lead opt-in: when `leadOptIn=true` (explicit checkbox only), a post-commit write calls feature 27's `POST /internal/v1/leads` (`source=checkout`, `sourceRef=orderCode`, consent fields per 27); bounded retry; failures are ops metrics only.
- Internal create: same ladder without Turnstile (shop 25 already enforced it); `OnBehalfOf` exactly one of `userId|buyerEmail`; tips pass `extras` per 25 and produce `kind=tip`.

## Consumed internal contracts

| Seam | Provider | Use |
|---|---|---|
| `GET /internal/v1/products/{id}/checkout-context` | shop 22 | price freeze + kind (`priceVnd`, 404/409 errors pass through) |
| `GET /internal/v1/fees/quote` | shop 26 | snapshot `{grossVnd, feeLines[], netVnd, rateVersion}`; `tier` from the owner's current plan where applicable; `railPassthroughVnd` when the rail config declares one (40) |
| `POST /internal/v1/leads` | profile 27 | checkout opt-in capture |
| plan catalog read (in-service) | feature 35 | active `plan_terms` rows for plan intents: price + explicit `term` + the stamped row id; fixtures are test-database-only |

## Cross-edits in this batch

| File | Change |
|---|---|
| `docs/02-architecture-techlead/32-monetize-overview/03-api-contracts-and-payloads.md` | registry rows: cancel → 33; internal create + has-product-orders rows added (landed) |
| `docs/02-architecture-techlead/02-account-deletion/03-api-contracts-and-payloads.md` | billing-service roster row (landed) |
| `docs/02-architecture-techlead/22-shop-catalog/03-api-contracts-and-payloads.md` | forward refs satisfied: checkout-context consumed here; `has-product-orders` realized by 34 |
| shop 25/26 + leads 27 | forward seams satisfied (`kind=tip`, snapshot, opt-in) — no text edits needed |
| `MIGRATION.md` | row 33 `done` at the batch tail |
| legacy `monetize/checkout/*` | deleted at the batch tail |
