# Shop Fulfillment - Architecture and Tech Stack (feature 23)

Phase 2 TL · Feature 23 of 53 — shop 2/5; extends `shop-service` (port 8087, `shop_db`, Redis db 9) — no new service. Implements `docs/01-requirements-ba/23-shop-fulfillment/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`; catalog kernel: `docs/02-architecture-techlead/22-shop-catalog/`.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | DEVIATION CARRIED (legacy S0): fulfillment is a module of `shop-service`, not a separate orchestrator. The partner adapter interface (`createJob|cancelJob|getStatus|verifyCallback`) is vendor-neutral; exactly one implementation lands at partner selection; tests run against a mock. No SLA floor is invented — displayed dates are partner-provided. | One transaction domain for catalog + jobs + entitlements (feature 22 D-1 carries); partner selection stays open. |
| D-2 | Dispatch is event-driven: consume `order.granted.v1` (forward, 32-40) and create one job per order line, exactly-once via unique `(order_id, order_line)`; consume `order.refunded.v1` (forward, 38) to cancel pre-print jobs and revoke entitlements. Forward names/shapes confirmed with 32-40; the consumption contract is fixed here. | Exactly-once dispatch without polling order tables or cross-service DB reads. |
| D-3 | Partner callbacks: `POST /api/merch/partner-callback` is the only anonymous non-page route (baseline public-paths row updated); signature verified, deduped by event id, enqueued to Redis stream `merch:callbacks` (db 9); a worker applies transitions. Callbacks never write the database directly. | Tamper-safe, replay-safe, rate-bounded; the ack path stays ≤ 300 ms. |
| D-4 | Digital delivery: a paid grant creates an entitlement row; each download mints a single-use download grant (TTL ≤ 15 min) bound to order + file version + buyer; no PostgreSQL write on the byte path (bytes stream from the uploads volume through the Nginx `internal` location to the buyer). | The legacy download-link pattern maps to a service-mediated grant; the counter write happens at mint, not fetch. |
| D-5 | Paywall locked body is a session-gated fragment (`GET /api/v1/digital/{productId}/body`, `private, no-store`); the SSR product page is preview-only and publicly cacheable. Inline personalized SSR was rejected: per-buyer HTML on a shared cache key is a poisoning vector. | BR-10 holds byte-for-byte: preview responses contain zero locked bytes; the entitlement check gates every fragment request. |
| D-6 | AI design generation ships only with the gated feature 16 (and its `ai_generations` pool): no endpoint, no `source='ai'` value, no credit path here — never stubbed. | Feature 16 remains gated per AGENTS.md; the legacy generate path stays design-only until then. |
| D-7 | Redis db 9 comes into use: callback stream `merch:callbacks` (consumer group `partner-cb`), dedupe keys `merch:cb:{eventId}` (24 h TTL), mint single-flight `digital:mint:{entitlementId}`. | Feature 22 reserved the database; this feature is its first consumer. |
| D-8 | Purge: seller-owned rows + files delete by `user_id` (feature 02 roster scope: fulfillment rows + files); buyer-side account deletion scrubs `ship_to_json` + `buyer_user_id` on that buyer's job rows (indexed) and deletes the buyer's entitlements. | Buyer PII never outlives the buyer's account. |
| D-9 | Version model: `digital_files` rows per product version; publishing flips `current` and supersedes the prior; entitlements pin `file_version`; `role IN ('file','body')` separates downloadable artifacts from paywall markdown bodies (bodies never mint URLs). | One versioning mechanic for downloads and locked posts (BR-11, BR-10). |

## Component map

| Piece | Home | Note |
|---|---|---|
| Design upload + versions | `shop-service` | 20 MB, PNG/JPEG/WebP/PDF magic-byte, random `shop-designs` keys |
| Dispatch worker | `shop-service` | consumes `order.granted.v1`; adapter `createJob` |
| Callback intake + worker | `shop-service` | signature verify → stream → compare-and-set transitions |
| Entitlements + minting | `shop-service` | mint under buyer context; file row read under service context |
| Publish precondition | `shop-service` | feature 22 `draft→live` gains the kind-aware check (cross-edit) |
| Product/paywall page | `public-site-service` | preview-only SSR row + cache key (feature 10 cross-edit) |

## Boundaries

Owns: designs, print jobs, the status trail, digital files + bodies, entitlements, minting, extensions, anomaly flagging, the partner adapter, the purge extension. Does not own: products/listing/pricing (22), AI generation (16, gated), profit math (26), orders/refunds/ledger (32-40), course progress (24), tips/consultations (25), SSR rendering (10).

## Consumers

| Consumer | Contract |
|---|---|
| order page (34) | `GET /internal/v1/merch/jobs/by-order/{orderId}` (status fields only; PII never crosses) |
| checkout (33/34) | ship-to collected at checkout (forward; minimized at the edge) |
| creator dashboard | owner list endpoints |
| public-site | product page preview uses feature 22 reads only; locked body never leaves shop-service |
