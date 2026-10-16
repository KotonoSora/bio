# Shop Catalog - Step-by-Step Implementation Plan (feature 22)

Phase 2 TL · Dependency-ordered chain; acceptance goals cite `docs/01-requirements-ba/22-shop-catalog/06-acceptance-criteria.md`.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | Service scaffold (`shop-service`, 8087) + `V1__shop_catalog.sql` + RLS + grants + baseline registration row (this change) | Baseline scaffold | TL 02 drills 1-4 green |
| T-02 | BE | Product CRUD: create/list/update with compare-and-set transitions, kind immutability, price-history append, audit rows, cursor list | T-01 | AC-01, AC-12 green |
| T-03 | BE | Cover upload: bounded read (2 MB + 1), magic-byte sniff (JPEG/PNG/WebP/AVIF), random key into `shop-covers`, replace/remove cleanup | T-02 | AC-07 green |
| T-04 | BE | Reorder: set-equality + dense rebuild in one transaction + one audit row (feature 08 pattern) | T-02 | TL 02 drill 6 green |
| T-05 | BE | Public reads: `by-profile`, `products:public` batch (≤ 50 ids), `checkout-context`; emit `shop.catalog_changed.v1` | T-02 | AC-08 (data half), NFR-04, AC-11 (emit half) green |
| T-06 | BE | Cross-service rows: public-site route + cache + purge listener; analytics `productId` beacon + V2 migration; blocks payload flip to `{productId}` (edits landed in this change) | T-05; features 08/10/12 codebases | AC-02 (beacon), AC-10, AC-11 green |
| T-07 | BE | Purge participant handler (products + price history + cover objects) + contract test | T-02 | AC-09 green |
| T-08 | FE | Dashboard products list + editor + cover + reorder; per-kind price UX; price-freeze notice; archive/delete flows with 409 → Archive CTA; block picker integration | T-02..T-04 | AC-01, AC-05 (UI half) green |
| T-09 | DevOps/QA | Drill wiring AC-01..AC-12; container drill (Docker + Podman, Flyway replay from V1, rollback); OpenAPI diff; secrets grep; throttle wiring | T-05..T-08 | G4 + G5 + G7 green |

## Notes

- Deploy-order rationale: the delete order check cannot lie — orders only exist once 32-40 ships the seam (the same change that enables checkout). Enable checkout after the seam is live.
- Money rails untouched: this feature invents no fee values (26 owns fee copy; 32-40 own settlement); the grep gates enforce it.
- Forward seams recorded: `checkout-context` (consumed by 33/34), `has-product-orders` (owned by 34), fee reads (26), product click rows (15 via 12).
- Legacy S0 exit-drill mapping: paid-download and resume-last-lesson drills → features 23/24; fee-identity drill → AC-03 seam + feature 26; catalog drills → AC-01..AC-12.
- The S0 execution plan's stack mapping is carried in TL 01 D-1 (service boundary + split trigger); its DEVIATION CARRIED (Boot + PG replace Worker/R2 defaults) rides the baseline deviations already in force.
