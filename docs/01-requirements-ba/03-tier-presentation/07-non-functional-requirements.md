# Tier Presentation - Non-Functional Requirements (feature 03)

Phase 1 BA · Quantified thresholds enforced in `docs/02-architecture-techlead/03-tier-presentation/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

| NFR ID | Area | Requirement |
|---|---|---|
| NFR-01 | Pricing page latency | Public, cache-first: served p95 `<= 300 ms` TTFB from the Nginx edge cache; origin fallback `<= 500 ms`; no per-request catalog call on cache hit |
| NFR-02 | Claim freshness | A config/catalog change is visible on the pricing page within `<= 5 min` (cache TTL); drift test runs in CI `<= 1 min` |
| NFR-03 | Plan read latency | `GET /api/v1/plan` p95 `<= 100 ms` (snapshot + optional projection, cached) |
| NFR-04 | Money exactness | All amounts VND integers end-to-end; yearly math exact to the VND; display never rounds away a dong |
| NFR-05 | Config validation | `yearlyDiscount <= 44%` and integral VND prices enforced at load; invalid config refuses boot |
| NFR-06 | Snapshot bound | Capability flips (`profiles.limit`) honored within `<= 30 s` of a plan change (feature 01 contract) |
| NFR-07 | Rates | `GET /api/v1/plans` public cache-first class (120 req/min/IP baseline); `GET /api/v1/plan` 60 req/min/user; `429` + `Retry-After` beyond |
| NFR-08 | Drift artifacts | Registry export + rendered-copy snapshot archived per release tag (G7); diff vs previous tag checked |
| NFR-09 | Accessibility/localization | Claims and dialog copy in vi + en; amounts format per locale; no claim text is locale-dependent in meaning |
| NFR-10 | Degrade honesty | Catalog/projection outages render absence, never stale prices beyond the 300 s cache bound nor invented amounts (BR-03) |
