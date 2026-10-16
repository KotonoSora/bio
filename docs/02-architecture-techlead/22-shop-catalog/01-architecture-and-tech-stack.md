# Shop Catalog - Architecture and Tech Stack (feature 22)

Phase 2 TL · Feature 22 of 53 — shop 1/5; starts `shop-service` (port 8087, `shop_db`, Redis db 9) and registers it in the data baseline in this change. Implements `docs/01-requirements-ba/22-shop-catalog/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | DEVIATION CARRIED: the legacy S0 verdict "monolith `shop` module, not per-kind services" maps to `shop-service` + `shop_db` under the mandated microservices baseline. The verdict's substance is preserved inside one service: catalog and (23-25) claims/POD/progress rows share one transaction domain and one RLS set; kinds never split. Recorded split trigger (carried from the S0 plan): a kind's fulfillment workload firing ≥ 2 framework triggers at P4/P5 extracts that kind's async worker — the catalog stays. | Database-per-service is in force; distributing the paid-grant boundary across seven kinds has no ≥ 10x asymmetry to justify it. |
| D-2 | Money boundary: `shop-service` owns products; orders, ledger, refunds, and grants are 32-40. Two internal seams: checkout reads `GET /internal/v1/products/{id}/checkout-context`; the delete path asks the monetize-owned `GET /internal/v1/orders/has-product-orders/{productId}` (lands with 32-40; orders cannot exist before the seam does). | Price-at-purchase freezing is a ledger property; the catalog never settles anything. |
| D-3 | Storefront SSR lives in public-site-service (feature 10): `/{username}/shop` reuses the resolve → read → render pipeline, cache keys, purge listener, and 404-parity oracle rules. Shop-service ships the read API; public-site renders. | The legacy "same Worker as bio" maps to the same service as bio; a second SSR origin would fork cache and purge semantics. |
| D-4 | Click counting rides the existing beacon (feature 12) with one additive field: `productId` for shop-page link-out clicks (for `kind=click`: exactly one of `blockId|productId`). No product target check in v1 (no cheap verification path; bounded by throttle + dedupe). | One analytics pipeline; the earnings dashboard reads the same rows. |
| D-5 | Covers: 2 MB cap + magic-byte sniff (JPEG/PNG/WebP/AVIF), random blob keys under a `shop-covers` prefix — the feature 09 pipeline pattern, implemented in shop-service. | Blob ownership follows the owning service; profile-service is not a general upload gateway. |
| D-6 | Product blocks: the `product|course|tip` payload becomes `{productId}` (feature 08 cross-edit; DEVIATION FLAGGED — the migrated 08 spec had generalized these kinds to link-shaped payloads; the legacy `payload product_id` binding is restored). Availability resolves at render via the batch public read; foreign/non-live targets render unavailable. | One source of truth per product; blocks never copy price or title. |
| D-7 | Freshness: `shop.catalog_changed.v1` (`username, profileId, at`) emitted after any mutation that changes public rendering; public-site purges `ssr:*:shop` plus bio keys (block availability). Lost events self-heal via TTL ≤ 60 s. | Mirrors `profile.published.v1` posture; one listener extension point. |
| D-8 | Audit + price history: transitions, price, cover, and delete actions audited (baseline append-only shape); `product_price_history` is append-only; the freeze itself is the order's price snapshot (D-2). | Disputes reconstruct from history + audit without content. |
| D-9 | Purge participant ships with this feature (feature 02 roster row landed here): products + price history + cover objects, counted. | Deletion must not wait for the rest of the major. |

## Component map

| Piece | Home | Note |
|---|---|---|
| Product CRUD + transitions + audit | `shop-service` | owner-scoped; compare-and-set `status` guard |
| Cover upload | `shop-service` | multipart → sniff → MinIO random key |
| Public reads | `shop-service` | `by-profile` (shop page) + `products:public` (block availability) |
| Storefront rows | `public-site-service` | route + cache key + purge consumer land in feature 10's files |
| Beacon `productId` | `analytics-service` | additive column + DTO (feature 12 files) |
| Block payload binding | `profile-service` | validator flip to `{productId}` (feature 08 files) |

## Boundaries

Owns: products, price history, covers, catalog validation, transitions and audit, public read APIs, `shop.catalog_changed.v1`, purge participant. Does not own: orders/ledger/grants/refunds (32-40), fulfillment (23-25), fee values and display (26), block authoring (08), SSR/cache/purge (10), beacon intake (12).

## Consumers

| Consumer | Contract |
|---|---|
| public-site-service | `by-profile` + `products:public` reads (feature 10 rows) |
| checkout 32-40 | `checkout-context` + `has-product-orders` (forward) |
| dashboard 15 | product click rows via feature 12 |
| fee engine 26 | kind + price + payload via owner/internal reads |
