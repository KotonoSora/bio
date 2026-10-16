# Invoices and Receipts - API Contracts and Payloads (feature 37)

Phase 2 TL · Browser + ops-visible contracts. Auth/rate rules: `docs/02-architecture-techlead/37-invoices-and-receipts/05-security-logging-and-rate-limiting.md`. JSON only except the two HTML print surfaces; problems follow RFC 9457. Registry rows land in `docs/02-architecture-techlead/32-monetize-overview/03-api-contracts-and-payloads.md`.

## Endpoint map

| Method & path | Auth | Request | Success | Errors (code) |
|---|---|---|---|---|
| `GET /api/v1/billing/receipts/{orderCode}` | session owner OR guest token `?t=&x=` | - | 200 `text/html` (A4 print CSS, `no-store`) | 401 `receipt_link_expired`; 403/404 `order_not_found` parity (34); 409 `receipt_unavailable`; 429 |
| `POST /api/v1/billing/receipts/{orderCode}/resend` | public + Turnstile | `ResendRequest` | 202 (always; no body semantics) | 422 `challenge_failed`; 429 |
| `POST /api/v1/billing/proformas` | session + CSRF | `ProformaCreateRequest` | 201 `ProformaView` (or 200 existing open) | 404 `product_absent`; 409 `plan_not_published`, `seller_profile_absent`; 422 `term_required`, `term_invalid`, `plan_not_purchasable`, `validation_failed`; 429 |
| `GET /api/v1/billing/proformas?cursor=` | session | - | 200 `ProformaPage` | 401; 429 |
| `GET /api/v1/billing/proformas/{ref}` | session owner | - | 200 `text/html` (A4 print CSS, `no-store`) | 401; 404 `proforma_absent`; 429 |

Consumed (no edits): `GET /api/v1/billing/orders` (34; history page base), `GET /internal/v1/products/{id}/checkout-context` (22; product pricing), `POST /internal/v1/notifications` (32; sends), `GET /internal/v1/users/{userId}/email` (01; owners).

## Kotlin DTOs (source of truth for the SPA type mirror)

```kotlin
data class ResendRequest(val email: String)
data class ProformaCreateRequest(val plan: String? = null, val term: String? = null, val productId: String? = null)
data class ProformaView(val ref: String, val status: String, val targetKind: String,
  val title: String, val amountVnd: Long, val expiresAt: Instant, val printPath: String,
  val pack: Map<String, String> = emptyMap())          // company-buyer fields when captured
data class ProformaPage(val items: List<ProformaView>, val nextCursor: String?)
data class ReceiptFacts(                                // server-side render input; not an API response
  val receiptId: String, val buyer: String, val lines: List<OrderLineView>,
  val amountVnd: Long, val provider: String, val providerRefs: List<String>,
  val grantAt: Instant, val creditNotes: List<JsonNode>) // credit notes arrive with 38
```

## Rules

- Receipt render input = 34's `OrderDetailView` + ledger titles + provider references + grant audit timestamp; `receiptId` only exists for `paid|refunded|disputed|chargeback_reversed` (34's rule); anything else `409 receipt_unavailable`.
- Guest token: `t = hex(HMAC-SHA256(RECEIPT_LINK_KEY, orderCode + "|" + x))`, `x` = unix expiry (30 days); verify constant-time and `x > now`; sessions of the owning account bypass tokens; no email appears in the URL; never logged.
- Resend: always 202 with the generic body; a pipeline send fires only when `email` matches the order's buyer email (case-insensitive, trimmed) and the order is money-confirmed; mismatches increment a metric only. Turnstile required; `challenge_failed` passes through from feature 06's contract.
- Proforma create: exactly one of (plan+term | productId); plan pricing from the active term row (35's passthrough codes); product pricing from 22's freeze (`priceVnd`; `product_not_live` passthrough); `billing.seller` must be set (`seller_profile_absent`); `billing.proforma.validityDays` (default 7, 1..30) stamps `expires_at`; second request in scope returns the open document with 200.
- Proforma statuses: `open -> converted | superseded | expired`; only these transitions exist; `converted` carries `converted_order_code`.
- Checkout link (33's create): `proformaRef` resolves owner-scoped; open + unexpired + `amount_vnd` equal -> `orders.proforma_id` set; drift -> status guard to `superseded` + `proformaRepriced=true` in the create response; foreign/absent/expired -> ignored (flag false). The charged amount is always the live catalog amount.
- Conversion (34's grant): status-guarded flip in the grant transaction + `proforma.converted` audit; replays and second grants write zero rows.
- Print surfaces share one template engine + one disclaimer constant (`NonVatDisclaimer` EN+VI); amounts-in-words via one `AmountWords` formatter; proforma print shows validity, catalog-version lock note, and payment instructions from 33's methods read.
- Email content mirrors the print view; subject carries title + amount; templates versioned in code.

## Error catalog additions

| Code | Status | UI Behavior |
|---|---|---|
| `receipt_link_expired` | 401 | Resend form page |
| `receipt_unavailable` | 409 | "Appears once payment is confirmed" |
| `proforma_absent` | 404 | Back to the proforma list |
| `seller_profile_absent` | 409 | "Quotes aren't available right now" |
| `challenge_failed` | 422 | Turnstile inline retry (06's contract) |

Reused: `order_not_found` (34), `plan_not_published` (35), `plan_not_purchasable`, `term_required`, `term_invalid`, `product_absent` (22), `validation_failed`, `throttled`.

```json
{ "type": "https://api.example.com/problems/receipt_unavailable", "title": "Receipt not available",
  "status": 409, "code": "receipt_unavailable", "detail": "Your receipt appears once payment is confirmed." }
```
