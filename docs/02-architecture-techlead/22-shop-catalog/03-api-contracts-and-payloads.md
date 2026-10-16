# Shop Catalog - API Contracts and Payloads (feature 22)

Phase 2 TL · Owner endpoints in `shop-service`; public reads consumed by public-site (10) and blocks (08); forward seams to 32-40. RFC 9457 problem bodies; TEXT ids; auth/rate rules: `docs/02-architecture-techlead/22-shop-catalog/05-security-logging-and-rate-limiting.md`.

## Endpoint map

| Method & path | Auth | Request | Success | Errors (code) |
|---|---|---|---|---|
| `GET /api/v1/products` | session | `?status=&cursor=&limit=` | 200 `ProductPage` | 401 |
| `POST /api/v1/products` | session + CSRF | `CreateProductRequest` | 201 `ProductResponse` | 422 `unknown_kind`, `price_invalid`, `validation_failed`, `product_limit_reached`; 401; 429 |
| `PATCH /api/v1/products/{id}` | session + CSRF | `UpdateProductRequest` (partial) | 200 `ProductResponse` | 404 `product_not_found`; 409 `transition_conflict`, `publish_precondition_unmet`; 422 `kind_immutable`, `price_invalid`, `bad_url_scheme`, `title_too_long`; 401; 429 |
| `POST /api/v1/products/{id}/cover` | session + CSRF | multipart `file` | 200 `{coverKey}` | 404; 422 `cover_too_large`, `cover_bad_type`; 401; 429 |
| `POST /api/v1/products/reorder` | session + CSRF | `{ids[]}` | 200 list `ProductResponse` | 422 `reorder_mismatch`; 401 |
| `DELETE /api/v1/products/{id}` | session + CSRF + step-up | - | 204 | 404; 409 `product_has_orders`; 503 `orders_check_unavailable`; 401 |
| `GET /internal/v1/shops/by-profile/{profile_id}/products` | service JWT | - | 200 list `PublicProduct` (live only, sorted) | 401 |
| `GET /internal/v1/products:public?ids=` | service JWT | `ids` CSV ≤ 50 | 200 list `PublicProductRef` (live + existing only; omissions = unavailable state) | 401 |
| `GET /internal/v1/products/{id}/checkout-context` | service JWT | - | 200 `CheckoutContext` | 404 `product_absent`; 409 `product_not_live`; 401 |
| `GET /internal/v1/orders/has-product-orders/{productId}` | service JWT | forward, monetize-owned, lands with 32-40 | 200 `{exists: bool}` | 401 |

## Kotlin DTOs (source of truth for the SPA type mirror)

```kotlin
data class ProductResponse(
  val id: String, val kind: String, val title: String, val description: String,
  val priceVnd: Long, val coverKey: String?, val payloadJson: Map<String, Any>,
  val status: String, val sortPosition: Int, val createdAt: Instant, val updatedAt: Instant,
)
data class ProductPage(val items: List<ProductResponse>, val nextCursor: String?)
data class CreateProductRequest(
  @field:Size(max = 120) val title: String,
  @field:Size(max = 2000) val description: String = "",
  val kind: String,
  @field:Min(0) @field:Max(99_999_999) val priceVnd: Long = 0,
  val payloadJson: Map<String, Any> = emptyMap(),
)
data class UpdateProductRequest(val title: String? = null, val description: String? = null,
  val kind: String? = null, val priceVnd: Long? = null, val payloadJson: Map<String, Any>? = null,
  val status: String? = null, val expectedUpdatedAt: Instant? = null)
data class PublicProduct(val id: String, val kind: String, val title: String,
  val priceVnd: Long, val coverKey: String?, val payloadJson: Map<String, Any>)
data class PublicProductRef(val id: String, val ownerProfileId: String, val kind: String,
  val title: String, val priceVnd: Long, val status: String)
data class CheckoutContext(val productId: String, val ownerProfileId: String, val kind: String,
  val title: String, val priceVnd: Long)
```

## Rules

- `products:public` returns live rows only; `ownerProfileId` lets the renderer reject foreign targets (TL 01 D-6); missing/archived ids are omitted — renderers show the unavailable state, never 404 for a block target.
- `checkout-context` refuses non-live targets (`409 product_not_live`); 32-40 snapshots `priceVnd` onto the order at creation (price freeze, AC-03).
- Payload validation: registry per kind; `linkout` requires an `https:` `externalUrl` (`bad_url_scheme`); unknown keys reject.
- Optimistic guard: `PATCH` may carry `expectedUpdatedAt`; mismatch → 409 `transition_conflict`.
- Delete consults `has-product-orders`: exists → 409 `product_has_orders` (archive instead); seam unreachable → 503 `orders_check_unavailable`, nothing deleted; seam route absent (32-40 not deployed) → no orders can exist, hard-delete allowed.
- Publish precondition (features 23-25 cross-edit): `draft→live` requires a pinned design version for `merch`, a `current` file version for `digital|paywall`, a complete curriculum (≥ 1 section, ≥ 1 lesson, every video lesson readable) for `course`, ≥ 1 availability window + a valid https meeting link for `service`, and presets within floor/ceiling for `tip` — otherwise 409 `publish_precondition_unmet` (offending items named).

## Events

| Event | Payload | Consumers |
|---|---|---|
| `shop.catalog_changed.v1` | `username, profileId, at` | public-site (purge `ssr:*:shop` + bio keys for the username; feature 10 rows) |

Emitted after commit on create → live, transitions, and edits affecting live rendering (title, price, cover, sort, linkout URL). No inbound events.

## Cross-edits landed in this change

| File | Change |
|---|---|
| `docs/02-architecture-techlead/10-share-and-seo/` | `/shop` route row; `:shop` cache key + purge; two internal consumption rows; event row |
| `docs/02-architecture-techlead/08-link-blocks/03-api-contracts-and-payloads.md` | `product|course|tip` payload → `{productId}` + availability note |
| `docs/02-architecture-techlead/12-profile-analytics/` | `productId` beacon field; `link_clicks` V2; `LinkRow` field |
| `docs/02-architecture-techlead/00-platform-baseline/01-architecture-and-service-topology.md` | shop-service row |
| `docs/02-architecture-techlead/02-account-deletion/03-api-contracts-and-payloads.md` | purge participant row |

## Governance

- OpenAPI additive-only; new codes appended to the catalog; `shop` is already on the reserved-slug list (feature 06).
- Contract tests: kind matrix, transition CAS, `products:public` omissions, purge listener parity, delete seam paths (409/503/absent).
