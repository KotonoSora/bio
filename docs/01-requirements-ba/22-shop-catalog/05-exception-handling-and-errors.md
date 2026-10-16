# Shop Catalog - Exception Handling and Errors (feature 22)

Phase 1 BA · Codes are stable; UI copy localizes vi/en.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Unknown kind on create | API | 422 `unknown_kind`; zero writes | "This product type isn't supported." |
| Negative price | API | 422 `price_invalid` | "Price can't be negative." |
| 0 price on merch/digital/paywall/service | API | 422 `price_invalid` | "This product type needs a price above 0." |
| Kind change after first publish | API | 422 `kind_immutable` | "A published product keeps its type." |
| linkout URL not https | API | 422 `bad_url_scheme` | "Use a secure https:// link." |
| Invalid status transition (e.g. archived → live) | API | 409 `transition_conflict` | "This product can't return to that state." |
| Concurrent edit loses the compare-and-set | API | 409 `transition_conflict`; client refetches | "Someone changed this product — refresh and retry." |
| Delete a product with order history | API | 409 `product_has_orders`; UI offers Archive | "This product has sales — archive it instead." |
| Order check unreachable on delete | API | 503 `orders_check_unavailable`; nothing deleted | "Deletion is temporarily unavailable — try again." |
| Cover over 2 MB | API | 422 `cover_too_large`; no file write | "Cover images must be 2 MB or smaller." |
| Cover fails magic-byte sniff | API | 422 `cover_bad_type` | "Upload a JPEG, PNG, WebP, or AVIF image." |
| Foreign product id on CRUD | API | 403; RLS denies at DB | "You don't have access to this product." |
| Block points at archived/deleted/foreign product | Render | unavailable state; page stays 200 | "This item is no longer available." |
| Private/missing/retired profile on shop URL | Render | 404 parity (identical status/headers/timing) | branded not-found page |
| Reorder payload mismatch | API | 422 `reorder_mismatch`; zero writes | "Ordering failed — refresh and try again." |
| Product limit reached (> 200) | API | 422 `product_limit_reached` | "You've reached the 200-product limit." |
| Rate limit hit | Gateway | 429 + `Retry-After` | "Too many requests — try again shortly." |
