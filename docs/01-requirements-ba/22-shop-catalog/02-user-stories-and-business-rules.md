# Shop Catalog - User Stories and Business Rules (feature 22)

Phase 1 BA · Realized by `docs/02-architecture-techlead/22-shop-catalog/` (BR-01..BR-15).

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Creator | list a product of a chosen kind, price it in VND, and publish it | my shop page and product blocks can sell it | Must |
| US-02 | Buyer | browse `/{username}/shop` and either buy on-platform or follow a disclosed link-out | purchases stay transparent | Must |
| US-03 | Creator | archive a product that has sold and hard-delete one that never did | history stays consistent and pages never break | Must |
| US-04 | Consumer service | read one product shape (kind, price, payload) | quotes, orders, and attribution line up | Must |
| US-05 | Creator | upload a cover image | listings look real on the shop page | Should |
| US-06 | Creator | reorder products | the shop page tells my story | Should |

## Business rules

- BR-01 Closed kind set: `merch|digital|paywall|course|tip|service|linkout`; anything else → 422 `unknown_kind`; the set never widens without an amendment.
- BR-02 Common fields: title ≤ 120 chars; description ≤ 2000 chars (sanitized plain text); cover optional; sort order defines shop-page order.
- BR-03 Price: VND integer ≥ 0; negative → 422 `price_invalid`; 0 allowed only for `course`, `tip`, `linkout`; `merch|digital|paywall|service` require > 0.
- BR-04 Kind changes are draft-only: once a product has been live, `kind` is immutable (422 `kind_immutable`).
- BR-05 Payloads: per-kind closed schemas validated at write; `linkout` requires `{externalUrl}` with an `https:` scheme (422 `bad_url_scheme` otherwise); other kinds' payload schemas land with 23-25 through the same validator registry.
- BR-06 Status lifecycle `draft → live → archived` with audited transitions; invalid transition → 409 `transition_conflict`; `archived` is terminal (no un-archive in v1).
- BR-07 Price changes append a `product_price_history` row (never edit/delete); orders settle at the purchase-time price (ledger binding owned by 32-40).
- BR-08 Archive vs delete: a product with order history archives and can never hard-delete; without orders it hard-deletes only via the audited delete path; blocks pointing at archived/foreign/missing products render a graceful unavailable state — never 404, never 500.
- BR-09 Shop page: `/{username}/shop` lists live products only, ordered by creator sort; honors `is_public` (private/missing/retired → 404 parity, no oracle); cache-first with purge ≤ 60 s after any catalog change.
- BR-10 Link-out transparency: `Sold elsewhere — buy on partner site` with the destination domain named; clicks counted via the analytics beacon; zero order rows; no implicit affiliate fees (affiliation only through a tracked partnership, 32-40 scope).
- BR-11 Product blocks: `product|course|tip` blocks carry `{productId}`; availability is resolved at render; a non-live or foreign target renders the unavailable state.
- BR-12 Covers: ≤ 2 MB upload, magic-byte sniff (JPEG/PNG/WebP/AVIF), random file names; client-chosen paths impossible.
- BR-13 Ownership: every product read/write scopes to the owning `user_id` + RLS; no cross-user path exists in any route; product data deletes with the account (purge participant).
- BR-14 Click counting: shop-page link-out clicks carry `productId` on the existing beacon; bio block clicks keep carrying `blockId` (feature 12 contract extended additively).
- BR-15 Audit: create, status transitions, price changes, cover changes, and hard-deletes write append-only audit rows (actor, action, target, timestamp; never content).
