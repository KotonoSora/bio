# Shop Commerce (execution plan S0 — catalog, fulfillment, fees)

PLAN ref: P2-2 (shop page + `products{profile_id, kind, title, price_vnd, file_r2_key}`) + P2-3 (courses, tips) + S-1/S-2/S-4/S-5. BA source: `docs/shop/products/product-catalog.md`, `docs/shop/merch/pod-fulfillment.md`, `docs/shop/merch/profit-calculator.md`, `docs/shop/digital/digital-delivery.md`, `docs/shop/courses/course-builder.md`, `docs/shop/tips/tip-jar.md`, `docs/shop/services/paid-consultations.md`, `docs/shop/pricing/sales-fees.md`. Builds on B0 authoring + M0–M2 money; no shop stubs before this plan. DEVIATION CARRIED: Spring Boot service + containerized PG replace Worker/R2 defaults; checkout always via the explicit-`provider` dispatcher (no PayOS-default reading); contract frozen.

## Purpose

Ship sellable profiles: one uniform catalog across seven kinds, kind-owned fulfillment with no inventory, price-at-purchase freezing, fee-inclusive display with quote = charged = ledger identity, and graceful archive semantics. S0 exits with paid-download, resume-last-lesson, and fee-identity drills green in one container image.

## Users

- Creator/seller: lists products, sets VND prices, publishes to `/:username/shop` and product blocks, reads fee quotes.
- Buyer: browses the shop page, buys on-platform or follows disclosed link-outs, downloads/consumes purchases.
- Finance/support: traces quote → order → ledger → receipt per product with one price story.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6`, Boot `4.1.1`, Kotlin `2.4.21`, Gradle `9.8.1`. Architecture verdict for S0 — monolith `shop` module, not per-kind services. Monolith pros: catalog + orders + claims share one transaction (paid grant → claim row atomically), one RLS set, product blocks reuse the `bio` renderer in-process, one audit log. Microservices cons avoided: splitting merch/digital/courses would distribute the paid-grant boundary (the exact distributed-transaction hazard the architecture decision forbids without triggers) and multiply IDOR surfaces across seven small domains with no ≥10x asymmetry between kinds. Split trigger recorded: if a kind's fulfillment workload (e.g. course video egress jobs) fires ≥2 framework triggers at P4/P5, that kind's async worker extracts — catalog stays.
2. Catalog kernel: Flyway `V2x__shop.sql` creates `products{product_id, profile_id, user_id TEXT NOT NULL + index + RLS, kind merch|digital|paywall|course|tip|service|linkout, title, description, price_vnd INT, cover_key?, payload_json JSONB per-kind validated, status draft|live|archived, sort}`. Closed kind set (`service` addition flagged beyond PLAN five); unknown kinds/negative prices 422; price 0 allowed only for courses (free) and tips (custom amount with floor + confirmation); merch/digital/paywall/service require price > 0. Price changes versioned — orders settle at purchase-time price. Products with order history archive (never hard-delete); bio blocks on archived products render unavailable. Ownership exports with P2-5 profile JSON; account deletion purges per retention.
3. Storefront: `/:username/shop` SSR lists live products (cover, title, price, kind badge, fee-inclusive display per fee schedule) honoring `is_public` (private profile = no shop), same SSR + cache-first + purge-on-publish posture as the bio page. `product|course|tip` bio blocks reference `product_id` with buy buttons deep-linking to shop or straight to checkout per kind. Link-out products (`{external_url https:, disclosure}`) label `Sold elsewhere — buy on partner site` with domain named; clicks ride the beacon pipeline; no order rows, no implicit affiliate fees.
4. Fulfillment per kind: merch = POD listing only (no inventory; partner adapter with API + webhooks; AI-design provenance flagged where used); profit calculator quotes creator margin pre-publish (fee values pending finance — calculator shows formula + pending-rate disclaimer, never invented percentages). Digital/paywall = one-time R2-compatible signed URL after paid order (single-use/expiring, owner-scoped grant check). Courses = sections/lessons with video keys + materials, buyer-email claim, progress persistence, resume-last-lesson. Tips = fixed/custom-amount product honoring the fee floor. Consultations (`service`) = booking payload with the 24h reschedule/cancel policy; no calendar-sync in v1.
5. Money binding: buyable kinds create orders through the M0 dispatcher (explicit `provider`, VND integers, numeric `orderCode`, idempotent grant); fee engine quotes from kind + price + tier with quote = charged = ledger identity; earnings dashboard reads order rollups via the capability registry (no stubs pre-shop). Claim invalidation on refund follows the refund router (re-grant refused; repurchase = new order).
6. Security baseline: owner-scoped CRUD (S-1/S-6 IDOR 403 + RLS deny); title/description XSS strip + CSP (S-2); cover uploads 2MB + magic-byte + random keys (S-4); anonymous shop reads throttled (S-5); audit on status/price/archive transitions; secrets via environment only.
7. Container per release: S0 tag runs via `docker run` and `podman run` against empty PG `18.6` (Flyway from V1) per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`.

## Data / API touches

- Tables: `products` (+ `course_sections/lessons`, `buyer_claims{order_id UNIQUE, user_id, product_id, progress_json, status}`, `pod_orders{…partner_ref}` as needed) — all `user_id` + index + RLS; least-privilege app vs migration roles; 5s statement timeout.
- Endpoints: `GET/POST /api/v1/products`, `PATCH/DELETE /api/v1/products/:id` (owner-scoped), public cacheable `GET /api/v1/shop/:username` (live only), claim/progress endpoints (grant-checked), fee-quote endpoint (server-priced). Checkout/grant/refund endpoints owned by M0–M2 (consumed, not redefined).
- Blobs: covers, digital files, course videos/materials under random keys; signed-URL issuance server-side with expiry; no client-chosen paths.

## Acceptance criteria

- Kind drill: unknown kind/negative price 422; free merch/digital rejected; free course + custom tip accepted.
- Price drill: pre-change order settles at purchase price; fee quote = charged = ledger to the VND.
- Fulfillment drill: paid digital buyer gets one-time expiring link (replay refused); student resumes last lesson; POD order carries partner ref with webhook reconciliation; link-out renders disclosure + domain with beacon-counted clicks and zero order rows.
- Archive drill: product with orders archives; blocks show unavailable (no 500s/dead checkouts).
- Security drill: cross-user CRUD 403 + RLS deny; XSS/CSP; upload rejections; throttle on anonymous shop paths; secrets grep clean.
- Container drill: digest pair boots, compose green, rollback green, OpenAPI diff clean; P3 gate mix includes shop purchase paths.

## Out of scope

- Order/refund/receipt mechanics (M0–M2); block rendering internals (B0); lead capture on shop pages (see `docs/platform/delivery/leads-capture.md`); broadcasts/retargeting (P4); replicas/ISR (P5); finance-ratified fee percentages (pending input — never invented here).

## Open questions

- Q1: `service` kind vs `product`-with-booking-payload — confirm enum addition?
- Q2: Fee-schedule values + yearly-discount ratification — blocking S0 fee copy or ship with formula + disclaimer?
