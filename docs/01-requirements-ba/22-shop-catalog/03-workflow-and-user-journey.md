# Shop Catalog - Workflow and User Journey (feature 22)

Phase 1 BA · System calls cite owning features.

## Creator: create → publish

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | opens dashboard "Products" | list of own products (all statuses) + `New product` | owner scope only |
| 2 | Creator | picks a kind, fills title/description/price | editor validates live; per-kind price rule inline | BR-02/03 |
| 3 | Creator | uploads a cover (optional) | 2 MB + magic-byte gate; stored under a random key; preview shown | BR-12 |
| 4 | Creator | saves draft | product persists `draft`; payload validated per kind | BR-05 |
| 5 | Creator | presses Publish | transition audited; shop page + affected bio pages purge within 60 s | BR-06/09; `shop.catalog_changed.v1` |
| 6 | System | public-site absorbs the event | `ssr:*:shop` + bio `ssr:*` keys for the username deleted | BR-09 |
| 7 | Creator | changes the price of a live product | history row appended; new price renders after purge | BR-07 |

## Buyer: browse → buy or follow link-out

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Buyer | opens `/{username}/shop` | SSR page: live products, cover/title/price/kind badge, sorted | BR-09; private profile → 404 parity |
| 2 | Buyer | taps Buy (buyable kinds) | deep-link to checkout with `productId`; price re-read server-side at order creation | 32-40 forward seam |
| 3 | Buyer | taps Buy on a link-out | click beacon fired (fire-and-forget), then navigation to the disclosed partner URL | BR-10/14 |

## Blocks: bind → render

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | adds a `product|course|tip` block | picker lists own products (non-live marked); payload `{productId}` stored | BR-11 |
| 2 | Visitor | bio page renders | batch availability read; live + owned renders the buy button; else unavailable state | BR-11 |

## Archive / delete

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Archive on a live product | status → `archived` (audited); pages purge; blocks flip to unavailable | BR-06/08 |
| 2 | Creator | Delete a product with no orders | audited hard-delete; blocks flip to unavailable; cover file removed | BR-08 |
| 3 | Creator | Delete a product with orders | refused 409 `product_has_orders`; UI offers Archive instead | BR-08 |
| 4 | System | delete checks order history | forward seam `has-product-orders` (32-40); unreachable seam → 503, nothing deleted | TL 03 seam |

## Support / purge

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Ops | investigates a sale | reads audit rows + price history; no content edits possible | BR-15 |
| 2 | Purge runner | account deletion | participant deletes products, price history, cover files with counts | BR-13 |
