# Shop Fulfillment - API Contracts and Payloads (feature 23)

Phase 2 TL · Endpoints in `shop-service`; forward money events consumed from 32-40. RFC 9457 problem bodies; auth/rate rules: `docs/02-architecture-techlead/23-shop-fulfillment/05-security-logging-and-rate-limiting.md`.

## Endpoint map

| Method & path | Auth | Request | Success | Errors (code) |
|---|---|---|---|---|
| `POST /api/v1/merch/designs` | session + CSRF | multipart `file` | 201 `DesignResponse` | 422 `artwork_too_large`, `artwork_bad_type`, `validation_failed`; 401; 429 |
| `GET /api/v1/merch/designs` | session | `?cursor=` | 200 list | 401 |
| `POST /api/v1/merch/designs/{designId}/versions` | session + CSRF | multipart `file` | 201 `DesignResponse` | 404 `design_not_found`; 422 `artwork_too_large`, `artwork_bad_type`; 429 |
| `GET /api/v1/merch/jobs` | session | `?status=&cursor=` | 200 `PrintJobPage` | 401 |
| `GET /internal/v1/merch/jobs/by-order/{orderId}` | service JWT | - | 200 list `PrintJobStatus` (status fields only; never ship-to) | 401 |
| `POST /api/merch/partner-callback` | signature header | `PartnerCallback` envelope | 202 accepted (queued) | 401 `callback_signature_invalid`; 429 |
| `POST /api/v1/digital/products/{productId}/versions` | session + CSRF | multipart `file` (role `file`) or body text (role `body`) | 201 `DigitalVersionResponse` | 404 `product_not_found`; 422 `file_too_large`, `file_bad_type`; 429 |
| `POST /api/v1/digital/products/{productId}/versions/{version}/publish` | session + CSRF | - | 200 `DigitalVersionResponse` | 404 `version_not_found`; 409 `version_conflict`; 429 |
| `POST /api/v1/digital/{productId}/access-link` | session | - | 200 `AccessLinkResponse` | 403 `entitlement_required`, `entitlement_expired`, `entitlement_exhausted`, `entitlement_flagged`; 404; 429 |
| `GET /api/v1/digital/{productId}/body` | session | - | 200 sanitized HTML fragment (`private, no-store`) | 403 `entitlement_required`, `entitlement_expired`, `entitlement_flagged`; 404 |
| `POST /api/v1/digital/{productId}/entitlements/{entitlementId}/extend` | session + CSRF (owner) | `ExtendRequest` | 200 entitlement view | 404; 422 `extension_invalid`; 429 |

## Kotlin DTOs

```kotlin
data class DesignResponse(val designId: String, val version: Int, val fileKey: String, val source: String)
data class PrintJobStatus(val id: String, val orderId: String, val status: String,
  val template: String, val variant: String, val quantity: Int,
  val partnerName: String?, val partnerSlaDate: LocalDate?, val reprintOf: String?)
data class PrintJobPage(val items: List<PrintJobStatus>, val nextCursor: String?)
data class DigitalVersionResponse(val productId: String, val version: Int, val fileId: String,
  val role: String, val sizeBytes: Long, val mimeVerified: String,
  val upgradePolicy: String, val status: String)
data class AccessLinkResponse(val url: String, val expiresAt: Instant, val downloadsRemaining: Int)
data class ExtendRequest(val days: Int = 0, val downloadsDelta: Int = 0)
data class PartnerCallback(val eventId: String, val jobRef: String, val status: String,
  val partnerSlaDate: LocalDate? = null, val costBaseVnd: Long? = null)
```

## Rules

- Dispatch: on `order.granted.v1` (forward, 32-40; shape `{orderId, buyerUserId, lines[{line, productId, quantity}], shipTo{name, phone, address}, paidAt}`) create one job per merch line; non-merch lines are ignored; duplicate delivery collapses on `uq_job_line`. Checkout (33/34) collects ship-to (forward; minimized at the edge).
- Refunds: on `order.refunded.v1` (forward, 38) — pre-print jobs `cancelled` (adapter `cancelJob`), entitlements `revoked`; post-print follows the partner policy.
- Callbacks: the DTO above is the normalized envelope; vendor outer framing is adapted and the raw signature verified before enqueue; deduped replays still get `202`; unknown `jobRef` is dropped after `202` (counter `merch.cb.unknown`).
- Minting: entitlement checked + incremented under buyer context; the file row is read under service context; `role='body'` refuses mint; URL TTL 15 min, single-use.
- Extension: `days ≤ 90`, `downloadsDelta ≤ 20`, owner-scoped, audited; `expires_at` recomputes from now, never decreases.
- Publish precondition (feature 22 cross-edit): `draft→live` requires a merch design pin or a `current` file version for `digital|paywall` — else 409 `publish_precondition_unmet`.

## Events

| Direction | Event | Notes |
|---|---|---|
| consumes | `order.granted.v1` | forward, 32-40; dispatch trigger |
| consumes | `order.refunded.v1` | forward, 38; cancel/revoke |
| emits | none | status is read on demand; no cache-affecting public change |

## Cross-edits landed in this change

| File | Change |
|---|---|
| `docs/02-architecture-techlead/22-shop-catalog/` | payload registry note (merch/digital/paywall schemas); publish precondition in `PATCH` errors + rules |
| `docs/02-architecture-techlead/10-share-and-seo/` | product-page route row; `:shop:{productId}` cache key; read-path step 6; purge-prefix wording |
| `docs/02-architecture-techlead/00-platform-baseline/01-architecture-and-service-topology.md` | public-paths row (partner callback) |

## Governance

- Codes appended to the shop catalog; forward event names/shapes confirmed with 32-40 and recorded here.
- Contract tests: dispatch replay, callback tamper + replay, mint matrix (required/expired/exhausted/flagged), extension caps, publish precondition, fragment `no-store` header.
