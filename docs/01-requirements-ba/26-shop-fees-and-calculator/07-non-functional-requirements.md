# Sales Fees and Profit Calculator - Non-Functional Requirements (feature 26)

Phase 1 BA · Every threshold numeric.

## Performance

| # | Requirement |
|---|---|
| NFR-01 | Quote endpoints are pure and synchronous: p95 ≤ 300 ms, p99 ≤ 600 ms; no queue, no writes. |
| NFR-02 | Schedule read p95 ≤ 150 ms (published version cached service-side ≤ 60 s). |
| NFR-03 | Scenario save/publish-price p95 ≤ 400 ms; rate publish p95 ≤ 500 ms. |
| NFR-04 | Calculator UI round-trip on the quote endpoint ≤ 1 s typical (network included); calculations render without page reload. |

## Correctness and money

| # | Requirement |
|---|---|
| NFR-05 | Integer-only math: basis points and VND integers; zero floats in the engine; overflow-guarded (long arithmetic, gross ≤ 99 999 999 VND per item as feature 22 bounds). |
| NFR-06 | Identity exactness: quote = charged = ledger to the VND across fixtures spanning tiers × kinds × versions × rounding boundaries. |
| NFR-07 | Immutability: published versions and historical order snapshots are never updated; corrections ship as new versions only. |
| NFR-08 | No-invention: zero literal rate values in this tree; CI grep + pre-publication refusal (`schedule_not_published`) enforce it in every environment. |

## Audit and access

| # | Requirement |
|---|---|
| NFR-09 | Rate drafts/approvals/publishes, cost updates, publish-price, and scenario writes append-only, 24-month retention; ids and amounts-of-change only, never copy. |
| NFR-10 | Dual control: requested_by ≠ approved_by enforced at the DB boundary (CHECK) and the route; any single-operator publish attempt is refused and alerted. |
| NFR-11 | Scenarios owner-scoped + RLS; rate/cost tables are platform config: app paths read-only, writes only via dual-control routes. |

## Localization and accessibility

| # | Requirement |
|---|---|
| NFR-12 | vi/en copy; VND integer formatting only (`1.000.000 ₫` / `1,000,000 VND`); margin shown as percentages derived from basis points, display only. |
| NFR-13 | Calculator keyboard-operable; decomposition lists screen-reader labeled; contractual-vs-estimate labels conveyed with text, not color alone. |
