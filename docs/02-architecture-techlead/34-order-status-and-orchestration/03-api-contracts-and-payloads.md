# Order Status and Orchestration - API Contracts and Payloads (feature 34)

Phase 2 TL · Endpoints in `billing-service`; conventions from `docs/02-architecture-techlead/32-monetize-overview/03-api-contracts-and-payloads.md` (registry rows for these paths landed there). Security: `docs/02-architecture-techlead/34-order-status-and-orchestration/05-security-logging-and-rate-limiting.md`. BA sources: `docs/01-requirements-ba/34-order-status-and-orchestration/`.

## Endpoint map

| Method & path | Auth | Request | Success | Errors (code) |
|---|---|---|---|---|
| `GET /api/v1/billing/orders` | session | `?status=&cursor=&limit=` (default 20, max 50) | 200 `OrderPage` | 401; 429 |
| `GET /api/v1/billing/orders/{orderCode}` | session owner | - | 200 `OrderDetailView` | 403 (foreign); 404 `order_not_found`; 429 |
| `GET /internal/v1/orders/has-product-orders/{productId}` | service JWT | - | 200 `{exists: Boolean}` | 401; 429 |

Creation, quote, methods, and cancel stay 33's routes; grant rides 40's verified-confirmation paths calling the guard — no new public write route exists in this feature, and reads never transition.

## Kotlin DTOs

```kotlin
data class OrderView(val orderCode: Long, val status: String, val statusDetail: String,
  val kind: String, val title: String, val amountVnd: Long, val provider: String,
  val createdAt: Instant, val expiresAt: Instant?)
data class OrderPage(val items: List<OrderView>, val nextCursor: String?)
data class OrderLineView(val line: Int, val kind: String, val title: String,
  val productId: String?, val quantity: Int, val amountVnd: Long)
data class FulfillmentItem(val status: String, val quantity: Int, val slaDate: LocalDate?)
data class FulfillmentView(val items: List<FulfillmentItem>, val degraded: Boolean = false)
data class OrderDetailView(val order: OrderView, val lines: List<OrderLineView>,
  val fulfillment: FulfillmentView?, val receiptId: String?)
```

## Rules

- Projection: only `{status, status_detail}` describes state; `status_detail` is server-composed (vi/en per `Accept-Language`) and rendered verbatim; provider-native codes never appear in any payload. Cursor pagination is opaque; `limit` clamps to 50.
- Detail composition: `lines[]` is single-line in v1 (quantity 1; `extras.quantity` is the recorded additive path); product/plan titles resolve via 22's `products:public` batch (omissions → fallback `Item <id>`), plan titles via 35's in-service catalog (realized by active or retired `plan_terms`; fixtures are test-database-only — 33's precedent); `fulfillment` for `merch` orders only, calling 23's `jobs-by-order` with a 150 ms budget — timeout/failure returns `degraded: true` and never 5xx (money fields always render; ship-to never crosses).
- Receipt link: `receiptId` = `RCPT-<orderCode>` for `paid|refunded|disputed|chargeback_reversed`; absent otherwise; 37 renders it.
- Guests: no sessionless money reads; 37's signed magic link reuses the projection (single-order, read-only); a sessionless attempt is 401.
- Delete guard: `exists` = any `orders` row referencing the product (any status); single bounded query (5 s statement timeout); returns no rows, no PII; powers 22's 409 `product_has_orders` / 503 `orders_check_unavailable` semantics.
- Event emission (restatement of 32's catalog; shape frozen): `order.granted.v1` on `billing.events` post-commit, at-least-once, `{orderId, buyerUserId, lines[{line, productId, quantity}], shipTo{name, phone, address}|null, extras{bookingId?, planId?}, paidAt}`; `paidAt` is the grant transaction timestamp; retry → `billing.events.dlq` + alert; `grant.emitted` audit marker + 24 h reconciliation re-emit (consumers idempotent: 23 `uq_job_line`, 24 enrollment uniqueness, 25 booking CAS).

## Consumed internal contracts

| Seam | Provider | Use |
|---|---|---|
| `GET /internal/v1/merch/jobs/by-order/{orderId}` | shop 23 | fulfillment block (status fields only) |
| `GET /internal/v1/products:public?ids=` | shop 22 | title resolution for product-kind orders (batch ≤ 50; omissions = fallback label) |
| plan catalog (in-service) | feature 35 | plan titles from active or retired `plan_terms` (realized) |
| `TransitionGuard` callers | feature 33 (sweep/cancel), feature 40 (verifiers/sweeps) | single status writer |

## Cross-edits in this batch

| File | Change |
|---|---|
| `docs/01-requirements-ba/33-checkout-session/` (BA 02, 04, 05) + `docs/02-architecture-techlead/33-checkout-session/01-architecture-and-tech-stack.md` | cancel aligned to the canonical matrix: `pending → cancelled` only; `authorizing` resolves by capture outcome or expiry (landed) |
| `docs/01-requirements-ba/32-monetize-overview/04-ui-data-dictionary.md` | StatusBadge copy completed with the ninth state (`chargeback_reversed`) (landed) |
| `docs/02-architecture-techlead/32-monetize-overview/03-api-contracts-and-payloads.md` | no edit needed: `order_not_found` lives in this feature's BA 05 / TL 03 (per-feature additions; the kernel catalog stays 32's) |
| 23's `jobs-by-order` + 22's delete guard/`products:public` | forward refs satisfied — no text edits needed |
| `MIGRATION.md` | row 34 `done` at the batch tail |
| legacy `monetize/payment/*` | deleted at the batch tail |
