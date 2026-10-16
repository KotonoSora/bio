# Shop Catalog - Context and Scope (feature 22)

Phase 1 BA · Feature 22 of 53 — shop 1/5, opening the shop major. Legacy sources: `shop/products/product-catalog.md` and the S0 shop execution plan (folded here; deleted with this batch). Realized by `docs/02-architecture-techlead/22-shop-catalog/`.

## Purpose

One uniform catalog for everything a creator sells: merch, a download, a paywalled post, a course, a tip jar, a consultation (`service`), or a link-out to an external checkout. One product row shape keeps the shop page, product blocks, the earnings dashboard, and the fee engine reading the same truth.

## Objectives

| # | Objective |
|---|---|
| O-1 | Creators list, price (VND integers), and publish products to `/{username}/shop` and product blocks. |
| O-2 | A closed seven-kind set with per-kind price and payload rules; unknown kinds never enter storage. |
| O-3 | Link-outs stay transparent: https-only, destination domain disclosed, beacon-counted clicks, zero order rows. |
| O-4 | Consumers (checkout 32-40, dashboard 15, fees 26) quote and settle from one shape, with price-at-purchase freezing. |
| O-5 | Archive, never break: ordered products archive; blocks pointing at unavailable products degrade gracefully. |

## Personas / RBAC

| Role | CRUD Permissions | Description |
|---|---|---|
| Creator (owner) | C/R/U/D on own products only | lists, prices, publishes, archives; uploads covers |
| Visitor / buyer | R live products on public shop pages only | browses, buys on-platform (33-34), or follows a disclosed link-out |
| Ops | R (+ audit reconstruction) | support; no cross-user edit path exists |
| Consumer services | R product context via internal APIs | checkout 32-40, dashboard 15, fee engine 26 |
| Anonymous attacker | none | deny-by-default; throttled public surfaces |

## In scope

Product rows + closed-kind validation; creator CRUD + `draft|live|archived` transitions + audit; cover upload (2 MB, magic-byte); shop-page data + SSR binding (public-site); `product|course|tip` block `{productId}` binding + unavailable rendering; link-out disclosure + beacon click counting; append-only price history; account-purge participant.

## Out of scope

Kind fulfillment (23-25: POD, digital delivery, courses, tips, consultations); fee values and fee-inclusive display (26); orders, refunds, ledger, receipts (32-40); block authoring internals (08); reviews/ratings (do not ship).

## Open questions (resolved)

| Q | Resolution |
|---|---|
| Q1 `service` kind | Confirmed as the seventh kind — DEVIATION FLAGGED (legacy flag carried): a generic product + booking payload would fork kind semantics in every consumer; consultations fit the uniform catalog. |
| Q2 multi-currency | VND-only v1 (VND integers are the platform money rule); indicative display is a later amendment. |
| Q3 listings export | Deferred — DEVIATION FLAGGED vs the legacy P2-5 linkage: the portability major (21) scoped export to resume only; a shop export rides a future profile-backup amendment. Deletion is covered by the purge participant. |
| Q4 linkout price | `price_vnd` is display-only for link-out products; 0 renders `See partner site` with no price. |
| Q5 fee display | Raw `price_vnd` now; fee-inclusive display lands with feature 26 (values pending finance — never invented). |
| Q6 shop SSR home | public-site-service renders `/{username}/shop` (feature 10), same cache and purge posture as the bio page. |
