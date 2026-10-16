# Monetize Overview - API Contracts and Payloads (feature 32)

Phase 2 TL · This feature owns the money API *conventions*, the ops config + trace routes, the notification-pipeline enqueue contract, and the canonical event catalog restatement. Endpoint owners: 33-40 (see map), 46 (agency billing), 47 (reporting read). Security: `docs/02-architecture-techlead/32-monetize-overview/05-security-logging-and-rate-limiting.md`. BA sources: `docs/01-requirements-ba/32-monetize-overview/`.

## Conventions (binding on 33-40)

- Base: `/api/v1/billing/*` (legacy `/api/billing/*` mapped); internal `/internal/v1/billing/*` (service JWT).
- IDs: `orderCode` non-sequential int64 in every money URL/payload; no sequential public ids.
- Errors: RFC 9457 problem JSON, stable `code` strings; features extend this catalog, never redefine it.
- Evolution: additive-only under an OpenAPI diff gate.
- Money: VND integers only (`amount_vnd INT`); client input never transitions state.

## Endpoint map

### Owned here

| Method & path | Auth | Request | Success | Errors (code) |
|---|---|---|---|---|
| `GET /api/v1/ops/billing/config` | ops | - | 200 `ConfigView` | 403; 429 |
| `POST /api/v1/ops/billing/config` | ops + CSRF | `ConfigFlipRequest` | 200 `ConfigView` | 403; 422 `flip_invalid`; 429 |
| `GET /api/v1/ops/billing/trace/{orderCode}` | ops | - | 200 `OrderTraceView` | 403; 404; 429 |
| `POST /internal/v1/notifications` | service JWT | `SendRequest` | 202 `{sendId}` | 422 `marketing_not_accepted`, `template_unknown`, `recipient_invalid`; 429 |
| `POST /webhooks/mail` | provider signature | bounce/complaint event | 202 | 401; 429 |

### Registered to owners (contracts defined in their files)

| Path | Owner |
|---|---|
| `GET /api/v1/billing/payment-methods`, `POST /api/v1/billing/quote\|checkout` | 33 |
| `GET /api/v1/billing/orders`, `GET /api/v1/billing/orders/{orderCode}` | 34 |
| `POST /api/v1/billing/orders/{orderCode}/cancel` | 33 |
| `GET /api/v1/billing/plan` | 35 |
| `POST /api/v1/billing/plan/cancel`, `POST /api/v1/billing/trial/start` | 36 |
| `GET /api/v1/billing/receipts/{orderCode}`, `POST /api/v1/billing/receipts/{orderCode}/resend` | 37 |
| `POST\|GET /api/v1/billing/proformas`, `GET .../{ref}` | 37 |
| `POST /api/v1/billing/orders/{orderCode}/refund-request`, `GET /api/v1/billing/refunds`, `GET /api/v1/billing/refunds/{id}` | 38 |
| `POST /api/v1/ops/refunds/{id}/approve\|execute\|reject`, `GET /api/v1/ops/refunds` | 38 |
| `POST /internal/v1/billing/refunds` (system-class reasons; shop 25) | 38 |
| `POST /api/v1/billing/promo/validate`, `GET /api/v1/billing/credits`, `GET /api/v1/ops/promo-campaigns`, `POST /api/v1/ops/promo-campaigns` | 39 |
| `POST /api/v1/ops/promo-campaigns/{id}/launch\|pause\|resume\|end`, `GET /api/v1/ops/promo-campaigns/{id}/burndown` | 39 |
| `/api/v1/ops/coupon-batches` (POST), `POST .../{id}/ack-export\|void-unredeemed`, `GET .../{id}/stats` | 39 |
| `POST /api/v1/ops/credits/goodwill`, `GET /api/v1/ops/credits` | 39 |
| `/webhooks/{rail}` × 8 | 40 |
| `POST /api/v1/billing/applepay/validate-merchant`, `POST /api/v1/billing/{googlepay\|applepay}/authorize` | 40 |
| `POST /api/v1/ops/rails/{provider}/verify`, `GET /api/v1/ops/rails/exceptions`, `GET /api/v1/ops/rails/disputes` | 40 |
| `POST /api/v1/ops/rails/exceptions/{id}/link\|refund-record\|dismiss`, `POST /api/v1/ops/rails/disputes/{id}/evidence` | 40 |
| `POST /internal/v1/billing/checkout` (session-or-guest order creation; shop 25) | 33 |
| `GET /internal/v1/orders/has-product-orders/{productId}` (catalog delete guard; shop 22) | 34 |
| `GET /api/v1/plans` (public pricing read; shape frozen by 03) | 35 |
| `/api/v1/ops/billing/plans/terms` (GET/POST), `POST .../terms/{termId}/activate\|retire` | 35 |
| `GET /internal/v1/billing/plan/{userId}`, `GET /internal/v1/billing/plans/current-prices` | 35 |
| `GET /internal/v1/billing/subscriptions/{userId}` | 36 |
| `POST /api/v1/ops/billing/subscriptions/{userId}/period-fix` | 36 |
| `POST /internal/v1/billing/ai/debit`, `POST /internal/v1/billing/ai/credit-return` | 35 |
| `POST /api/v1/ai/tailor-resume` (executes in resume-service) | 35 |
| `/api/v1/agency/billing/*` (overview, subscribe, cancel, seats/history, invoices, contact/transfer + confirms) | 46 |
| `GET /api/v1/ops/billing/agency/reconciliation`, `POST .../reconcile` | 46 |
| `GET /internal/v1/billing/agency/entitlement` (45's staff gates) | 46 |
| `GET /internal/v1/billing/sales-summary` (47's reporting read) | 47 |

## DTOs (this feature)

```kotlin
data class ConfigView(val version: Long, val eligibility: JsonNode, val displayOrder: JsonNode,
  val killSwitches: JsonNode, val pendingTtl: Map<String, Int>, val updatedAt: Instant, val updatedBy: String)
data class ConfigFlipRequest(val surface: String, val value: JsonNode, val reason: String)
data class OrderTraceView(val order: JsonNode, val providerRefs: List<Map<String, String>>,
  val timeline: List<JsonNode>, val ledgerRows: List<JsonNode>, val receipts: List<JsonNode>,
  val refunds: List<JsonNode>, val audit: List<JsonNode>)
data class SendRequest(val sendClass: String /* "transactional" only */, val template: String, val to: String,
  val payload: JsonNode, val idempotencyKey: String, val ownerRef: String?)
```

## Event catalog (canonical restatement — shapes frozen by consumers; additive-only)

| Event | Stream | Payload | Emitted by | Consumers |
|---|---|---|---|---|
| `order.granted.v1` | `billing.events` | `{orderId, buyerUserId, lines[{line, productId, quantity}], shipTo{name, phone, address}, extras{bookingId?, planId?}, paidAt}` | 34 (verified grant step) | 23/24/25 — `extras` is additive; consumers ignore unknown fields |
| `order.refunded.v1` | `billing.events` | `{orderId, refundId, refundedAt}` | 38 | 23/24/25 (cancel/revoke/notice) |
| `order.chargeback_reversed.v1` | `billing.events` | `{orderId, disputeRef, reversedAt}` | 40 (dispute loss) | 23/24 (same invalidation path as refunds) |

## Money error catalog (additions)

| Code | HTTP | Meaning |
|---|---|---|
| `provider_required` | 422 | create without `provider` |
| `unknown_provider` | 422 | registry or eligibility miss |
| `idempotency_key_reuse` | 422 | key bound to a different intent |
| `quote_expired` | 410 | quote older than 15 min |
| `amount_changed` | 409 | re-price differs from quote |
| `rail_unavailable` | 422 | killed or delisted rail |
| `handoff_failed` | 502 | provider handoff failed |
| `flip_invalid` | 422 | config flip rejected (shape/value) |
| `marketing_not_accepted` | 422 | non-transactional send class |
| `template_unknown`, `recipient_invalid` | 422 | notification pipeline rejections |
| `throttled` | 429 | any rate envelope |

## Forward seams recorded

| Seam | Owner | Contract |
|---|---|---|
| preference-center superset (28's page) | gated marketing major 48-53 (`consent-preferences`) | pipeline `sendClass` + 27's suppression read are the extension surface; no stub |
| affiliate `/r/` attribution, ad measurement | gated 48-53 | consume money events read-only (BR-16) |
| earnings traces (15) | this batch | orders read APIs (34/38) + the ops trace route |
| ship-to collection | 33/34 | collected at checkout, minimized at the edge |
| order extras (`bookingId`) | 33/34 | carried through `order.granted.v1` (25's CAS confirm) |

## Cross-edits in this batch

| File | Change |
|---|---|
| baseline 01 + `MIGRATION.md` | billing-service row (landed) |
| `MIGRATION.md` rows 32-40 + batch line | `done` marks at the batch tail |
| `docs/02-architecture-techlead/02-account-deletion/03-api-contracts-and-payloads.md` | billing-service purge rows (landed with 33's tables) |
| baseline 01 public-paths row | `/webhooks/*` anonymous callback entry (landed with 40) |
| shop 23/24/25, leads 27/28 | forward refs become satisfied — their build-order gates clear; no text edits needed |
| legacy `monetize/*` + delivery plans | sources deleted per `MIGRATION.md` rows (batch tail) |
