# Shop Catalog - Acceptance Criteria (feature 22)

Phase 1 BA · BDD; all rows executable.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Kind drill | an unknown kind, a negative price, a free merch, a free course, a custom tip | validation runs | unknown/negative → 422; free merch → 422; free course and 0-price tip accepted |
| AC-02 | Link-out drill | a linkout product with an `https:` URL | created and clicked on the shop page | renders disclosure + domain; beacon click counted with `productId`; zero order rows |
| AC-03 | Price-freeze seam | an order placed at price A, then price → B (seam implemented 32-40) | settlement runs | settles at A; ledger matches the quote snapshot; `product_price_history` shows both edits |
| AC-04 | Archive drill | a product with orders; a product without | archive / delete flows run | with orders: archives, blocks flip to unavailable, no 500s or dead checkouts; without: audited hard-delete, cover file removed |
| AC-05 | IDOR drill | user B's `profile_id`, user A's session | A attempts B's product CRUD | 403 + RLS denies; zero rows read or written |
| AC-06 | XSS drill | script/event-handler payloads in title/description | saved and rendered on shop + block | stored sanitized; CSP holds; no execution |
| AC-07 | Upload drill | 3 MB file; renamed `.exe` with PNG magic bytes; valid WebP | cover upload | 422 `cover_too_large`; 422 `cover_bad_type`; valid stores under a random key, zero client paths |
| AC-08 | Shop privacy drill | private, missing, retired, and public profiles | `/{username}/shop` fetched | first three: 404 parity (status/headers/timing class); public: live products only, sorted |
| AC-09 | Purge drill | an account with products + price history + covers | purge runs | all rows + files deleted with counts; no cross-user deletes |
| AC-10 | Block binding drill | `product` block → live product; then archived; then foreign `productId` | bio renders each time | live: buy button deep-link; archived/foreign: unavailable state, page 200 |
| AC-11 | Publish purge drill | live product, cached shop + bio pages | price/status change | both purge ≤ 60 s; cache re-renders; TTL-only path heals ≤ 2 min |
| AC-12 | Transition/audit drill | draft → live → archived; attempted archived → live; delete with orders | transitions run | valid transitions audited; invalid → 409; delete-with-orders → 409 + Archive CTA; price change appends a history row |
