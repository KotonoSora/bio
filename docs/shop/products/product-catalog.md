# Product Catalog (listing, kinds, link-out sales)

PLAN ref: P2-2 (shop scope: `products{profile_id, kind, title, price_vnd, file_r2_key}`, shop page `/:username/shop`). First doc of the new `shop` major. Kind enum extended with `service` for paid consultations (see `services/paid-consultations.md`) — flagged addition beyond the PLAN-listed five kinds.

## Purpose

Define the single catalog every sellable thing lives in: one product row whether it is merch, a download, a paywalled post, a course, a tip jar, a consultation, or a link-out to an external checkout. Listing, pricing, and shop-page presentation are uniform; fulfillment differs per kind and is owned by the kind-specific docs. Uniform catalog means the shop page, product blocks, earnings dashboard, and fee engine all read one shape.

## Users

- Creator/seller: *As a Creator/seller, I want to create listings, set prices in VND, and publish to the shop page and product blocks so that every sellable thing lives in one catalog.* Creates listings, sets prices in VND, publishes to the shop page and product blocks.
- Buyer: *As a Buyer, I want to browse `/:username/shop` and buy on-platform or follow a disclosed link-out so that purchases stay transparent.* Browses `/:username/shop`, buys on-platform or follows a disclosed link-out to an external checkout.
- Fee engine and analytics (consumers): *As a Fee engine and analytics consumer, I want to read kind + price + tier so that fee quotes and revenue attribution stay uniform.* Read kind + price + tier to quote fees and attribute revenue.

## Functional requirements

1. Product row (closed kind set): `merch|digital|paywall|course|tip|service|linkout`. Common fields: title, description, price_vnd (0 = free, allowed for course/tip-custom only per kind rules), cover image (R2, same 2 MB + magic-byte pipeline as avatars), status (`draft|live|archived`), sort order on the shop page. Kind-specific payloads live in `payload_json` validated per kind; unknown kinds rejected 422.
2. Link-out products (external checkout): `{external_url https:, disclosure shown}` — buyer clicks through to Shopee/another store; the shop page labels these `Sold elsewhere — buy on partner site` with the domain named. Clicks ride the existing beacon pipeline (block/link click events), so the earnings dashboard shows interest even though money moves off-platform. No affiliate-fee implication by default (affiliate attribution only where a tracked partnership exists per `monetize/affiliate/`).
3. Shop page: `/:username/shop` lists live products (cover, title, price, kind badge, fee-inclusive display price per `pricing/sales-fees.md`); honors `is_public` (private profile = no public shop); SSR + cache-first posture identical to the bio page (same Worker, same purge-on-publish).
4. Product blocks: `product|course|tip` block types reference `product_id` (see `link-to-bio/blocks/link-blocks.md`) — the bio page shows buy buttons that deep-link to the shop page or straight to checkout per kind rules. Deleting a product with order history archives (never hard-deletes); blocks pointing at archived products render a graceful unavailable state.
5. Free products: price 0 permitted for courses (free courses) and tips (custom-amount entry, floor 0 with confirmation); merch/digital/paywall/service require price > 0.
6. Ownership: creator exports listings with the P2-5 profile JSON export; product data deletes with the account per retention policy.

## Data / API touches

- New table `products{product_id, profile_id, kind, title, description, price_vnd INT, cover_r2_key?, payload_json, status, sort}` plus standard `user_id TEXT NOT NULL` + index + RLS. Status transitions audited; price changes versioned (orders always reference the price-at-purchase, never the live row).
- Endpoints: `GET/POST /api/v1/products`, `PATCH/DELETE /api/v1/products/:id` (owner-scoped); public `GET /api/v1/shop/:username` (live products only, cacheable) backing the SSR shop page. All PG via `worker/db/*`.
- Checkout handoff: buyable kinds create PayOS-first orders through `monetize/payment/checkout-orchestration.md` (VND integers, numeric orderCode, idempotent grant); link-out kinds never touch checkout.

## Acceptance criteria

- Kind drill: unknown kind or negative price rejected 422; free merch/digital rejected; free course and custom-amount tip accepted. *GWT: Given an unknown kind, negative price, or free-price listing; When validation runs; Then unknown kinds and negative prices are rejected 422, free merch/digital are rejected, and free courses plus custom-amount tips are accepted.*
- Price-freeze drill: order placed before a price change settles at the purchase-time price (ledger matches quote snapshot). *GWT: Given an order placed before a price change; When settlement runs; Then it settles at the purchase-time price with ledger matching the quote snapshot.*
- Archive drill: product with orders archives cleanly; bio blocks show unavailable (no 500s, no dead checkouts); product without orders hard-deletes only via audited path. *GWT: Given a product with or without orders; When archived or deleted; Then the product with orders archives cleanly with bio blocks showing unavailable (no 500s, no dead checkouts), and the product without orders hard-deletes only via the audited path.*
- Link-out drill: external URL requires `https:`, renders with disclosure + domain; clicks counted in beacon aggregates; no order row created. *GWT: Given a link-out product; When created and clicked; Then the external URL requires `https:`, renders with disclosure + domain, clicks are counted in beacon aggregates, and no order row is created.*
- IDOR drill: user A product CRUD against user B `profile_id` returns 403; RLS denies at DB. XSS drill on title/description: stripped, CSP holds. *GWT: Given user B `profile_id` and injection input in title/description; When user A attempts product CRUD or the fields render; Then CRUD returns 403 with RLS denial at DB, and XSS input is stripped with CSP holding.*

## Out of scope

- Kind fulfillment: merch/POD (see `merch/pod-fulfillment.md`), profit math (see `merch/profit-calculator.md`), digital/paywall delivery (see `digital/digital-delivery.md`), tips UX (see `tips/tip-jar.md`), courses (see `courses/course-builder.md`), consultations (see `services/paid-consultations.md`).
- Fees (see `pricing/sales-fees.md`); order lifecycle, refunds, receipts (`monetize/payment/`, `monetize/billing/`, `monetize/sales/`); block rendering (`docs/link-to-bio/`).

## Open questions

- Q1: `service` kind addition to the PLAN enum — confirm, or model consultations as `product` with a booking payload instead?
- Q2: Multi-currency display (VND truth, indicative USD) — v1 need, or VND-only until cross-border demand?
