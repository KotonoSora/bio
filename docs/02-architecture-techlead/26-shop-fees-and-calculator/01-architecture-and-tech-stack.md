# Sales Fees and Profit Calculator - Architecture and Tech Stack (feature 26)

Phase 2 TL · Feature 26 of 53 — shop 5/5; extends `shop-service` (port 8087, `shop_db`) — no new service. Implements `docs/01-requirements-ba/26-shop-fees-and-calculator/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`; prior shop files: `docs/02-architecture-techlead/22-shop-catalog/` through `docs/02-architecture-techlead/25-shop-consultations-and-tips/`.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | The fee engine (schedule + pure quote) and the calculator are modules of `shop-service` (register 22-26); checkout (33) calls the internal quote seam synchronously; ledger/receipts/disputes/reconciliation execution stay with 32-40. The legacy monolith `fee_schedule` config maps to `shop_db` config tables + dual-control ops routes. | The register fixed fees inside the shop major; money movement stays with the money services. |
| D-2 | Values are finance-owned and absent by construction: rate versions publish only via dual control (DB CHECK `requested_by <> approved_by`); with none published, quotes refuse 409 `schedule_not_published` and no fee copy renders; this tree contains zero literal rates (grep gate); tests seed labeled fixture versions in **test databases only**. Release gate: checkout fee-snapshot enablement requires a published version. | The legacy placeholder bands are unratified — the mechanism ships, the numbers never get invented. |
| D-3 | One pure quote function serves both surfaces (`POST /api/v1/fees/quote` for sellers; `GET /internal/v1/fees/quote` for checkout); integer basis points + long arithmetic, zero floats; rounding from the version's `rounding_rule` (`nearest_1000|exact`); the quote = charged = ledger identity is a 38-executed invariant, drilled here against fixtures. | One function, three call sites (legacy identity demand) with no drift surface. |
| D-4 | Config tables are platform config (no `user_id` dimension unlike business rows): app paths read-only; writes only through the dual-control ops routes under the `svc` context + role checks. Scenarios are the user-scoped business rows here. | Config has no owner user; dual control + audit replaces row scoping. |
| D-5 | Scenarios are immutable versioned rows (edits append `version + 1`); publish-price writes `price_vnd` + the additive `pinned_rate_version` column on the product; rate publishes never touch saved scenarios or live prices. | Explicit republish only (BR-11/12); historical figures stay reproducible. |
| D-6 | `partner_cost_table` is ops-entered and audited; merch quotes require an entry for template+variant (`cost_unknown` otherwise); no cost is guessed or defaulted. | The calculator's math is only as honest as its cost input. |
| D-7 | Display rules: buyer-facing one payable total; drift test in CI across the surfaces shipping now (schedule page, checkout lines, dashboard summary); the marketing pricing page is gated (48-53) and will consume the same endpoint. | One config version everywhere; gated scope stays untouched. |
| D-8 | Tier source is the subscription projection (36, forward): until it lands only `free` is assignable; break-even renders "pending" when plan prices are unavailable — never a stale copy. | No fabricated plan prices or pro rows. |
| D-9 | Purge: scenarios delete with the seller; rate/cost config survives (no personal data); historical order snapshots are 32-40's immutability domain. | Config has nothing to purge; user rows follow the standard purge posture. |

## Component map

| Piece | Home | Note |
|---|---|---|
| Rate versions + dual-control routes | `shop-service` | draft → approve (different operator) → published; append-only |
| Schedule read + cache | `shop-service` | public; 60 s service-side cache |
| Pure quote function | `shop-service` | fees/quote + internal seam; integer bp math |
| Calculator + scenarios | `shop-service` | forward/reverse, tier comparison, immutable rows |
| Cost table ops route | `shop-service` | audited; feeds merch quotes |
| Publish-price | `shop-service` | pins `rate_version` on the product |
| Checkout seam | 33/34 (forward) | synchronous quote → order snapshot |
| Reconciliation + plan prices | 38 / 36 (forward) | identity execution / break-even source |

## Boundaries

Owns: rate config + publish lifecycle, quote math, display-rule enforcement, the calculator, the cost table, scenarios, pinned versions, audit. Does not own: plan prices/subscriptions (36), checkout/refunds/receipts/ledger/disputes (32-40), partner cost sourcing (23 ops domain — entry only), marketing copy surfaces (48-53 gated), product CRUD (22).

## Consumers

| Consumer | Contract |
|---|---|
| checkout (33/34) | `GET /internal/v1/fees/quote` → snapshot `{gross, feeLines[], net, rateVersion}` |
| ledger (38) | pinned `rate_version` on orders; mismatch pages finance instead of settling |
| earnings dashboard (15) | fee-line labels pass through verbatim (its zero-math posture holds); rollups forward 32-40 |
| sellers | schedule, calculator, scenarios, publish-price |
| public | schedule endpoint |
