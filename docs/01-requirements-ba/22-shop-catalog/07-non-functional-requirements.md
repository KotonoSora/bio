# Shop Catalog - Non-Functional Requirements (feature 22)

Phase 1 BA · Every threshold numeric.

## Performance

| # | Requirement |
|---|---|
| NFR-01 | Shop page SSR: p95 TTFB ≤ 300 ms cache-hit at the edge; ≤ 800 ms cold render; internal `by-profile` read p95 ≤ 200 ms. |
| NFR-02 | Creator CRUD p95 ≤ 400 ms; cover upload p95 ≤ 1.5 s for 2 MB. |
| NFR-03 | Purge after a catalog change: ≤ 60 s service key delete; TTL-only heal ≤ 2 min. |
| NFR-04 | Batch block availability read (`products:public`) p95 ≤ 250 ms; ≤ 50 ids per call. |

## Security and data

| # | Requirement |
|---|---|
| NFR-05 | Deny-by-default; owner scope by server-set `user_id` + per-database RLS; zero cross-user paths; foreign ids → 403 and RLS denial. |
| NFR-06 | Uploads: 2 MB cap + magic-byte sniff; random blob keys; no client-chosen paths; no executable serve path; no SVG. |
| NFR-07 | Sanitization on write + escape on render; CSP at the edge; stored payloads never echoed raw. |
| NFR-08 | Audit: create/transition/price/cover/delete rows append-only, 24-month retention; ids only, never content. |
| NFR-09 | Purge participant: products + price history + cover objects, counts returned; no cross-user deletes. |
| NFR-10 | Export deferral (BA 01 Q3) leaves no orphan: account deletion removes everything; no export endpoint exists in v1. |

## Scale and quotas

| # | Requirement |
|---|---|
| NFR-11 | ≤ 200 products per creator (422 `product_limit_reached` beyond); sort order dense-rebuilt on reorder like blocks. |
| NFR-12 | Owner CRUD 120/h; publish/archive/store transitions 60/h; cover uploads 30/h; beacon throttle unchanged (feature 12). |
| NFR-13 | Price-history growth bound: O(price edits); shop-page read = one indexed query over live products; no N+1. |

## Sessions, localization, accessibility

| # | Requirement |
|---|---|
| NFR-14 | Sessions cookie-based at the edge (SSO-only); 8 h idle / 30 d absolute; step-up required for destructive delete. |
| NFR-15 | vi/en copy; VND integer formatting only (`1.000.000 ₫` vi / `1,000,000 VND` en); no currency conversion. |
| NFR-16 | Shop page + editor keyboard reachable; product cards focusable; disclosure text contrast ≥ 4.5:1. |
