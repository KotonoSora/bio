# Advertising (Inventory, Serving, Creator Share, Retargeting) - Architecture and Tech Stack (feature 50) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/50-advertising/` (7 files, gated). GATED: design-complete only — no component below exists in any image, bundle, or migration until a spec amendment approves it (BR-01, AC-01). Stack pins per `docs/02-architecture-techlead/00-platform-baseline/`. Seams consumed: 48's campaign path + eligibility function (email reach), 49's earner-type-partitioned payout runner, 12/31's analytics event streams, 34's order events.

## Service boundary (future): `ads-service`

When built: new service `ads-service` (port 8093, `ads_db`, Redis db 14) — inventory, decisioning, measurement, segments, and creator ad earnings are ads' own data; nothing ad-related enters analytics tables and nothing analytics-related enters ads tables (the separation guarantee is by database boundary plus code paths).

| Component (at amendment) | Change | Notes |
|---|---|---|
| `PlacementCatalog` (new) | Versioned config: platform slots + creator units | Checkout/payment surfaces provably ad-free |
| `FlightService` (new) | Flights + creatives + approval + takedown | Promo-campaign discipline: caps + window to launch; instant pause |
| `DecisionService` (new) | Eligibility matrix + weighted selection + cache | ≤ 50 ms with collapse-on-timeout; deterministic tie-breaks; no RTB |
| `MeasurementPipeline` (new) | Queue → dedupe (10-min) → aggregates; raw NDJSON | Never direct PG writes; evidence bundles per billed event |
| `FraudFilters` (new) | Bots, viewport minimum, click anti-double + landing validation, velocity quarantine | One posture, stricter at the billing boundary |
| `EarningsLedger` (new) | estimated → confirmed (monthly close) → paid / clawed_back | Fraud-filtered events only; quarantined never earns |
| `SegmentEvaluator` (new) | Minutes-scale consumer over analytics/order streams; TTL membership | Per-creator only; opaque refs; lag labeled honestly |
| `RetargetService` (new) | Segment-targeted onsite flights + email via 48; holdout slices | Consent arbitrated by 48's eligibility per recipient |
| Payout handoff | Creator lines → 49's shared runner (earner-type partition) | One finance process; reserved internal contract |

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | New `ads-service`; `ads_db`; Redis db 14 for decision cache + dedupe state | Separation guarantee by database boundary; hot-path isolation from analytics |
| D-2 | Decisioning server-side, cached per (placement, coarse segment), 50 ms cap, collapse-on-timeout | BR-08/Q-04; the page never waits on ads |
| D-3 | Measurement mirrors the beacon discipline: queue, 10-min KV dedupe, aggregate rollups, raw NDJSON | BR-09; zero per-hit PG writes; outage recovery by sweep-and-repair |
| D-4 | Fraud filters run before aggregation; quarantine excludes from billing aggregates; cleared traffic re-enters aggregates only | BR-10/BR-12; payouts stay dispute-proof |
| D-5 | Earnings ride 49's runner via an earner-type-partitioned internal handoff; no second payout system | BR-14; one finance process, no cross-earner visibility |
| D-6 | Email reach = 48's campaign path; consent arbitration = 48's eligibility function per recipient | BR-17; no parallel send path, no consent re-implementation |
| D-7 | Segments are versioned definitions with TTL membership rows; evaluator consumes 12/31 streams + 34 events (minutes cadence) | BR-16; expiry is the privacy mechanism; no per-hit synchronous writes |
| D-8 | Off-platform export surface does not exist in v1 (404/422); lift requires a holdout slice | Q-08/BR-18; first-party-only stands; no claim without control |
| D-9 | Targeting limited to existing signals (category/language, device class, coarse geo) — no new tracking, no behavioral profiling | BR-03; privacy-conservative v1 |

## Cross-edits (all at amendment, not now)

| Target | Edit (at amendment) |
|---|---|
| `docs/02-architecture-techlead/00-platform-baseline/01-architecture-and-service-topology.md` | new `ads-service` row (8093, `ads_db`, Redis 14) |
| `docs/02-architecture-techlead/49-affiliate/03-api-contracts-and-payloads.md` | runner handoff row: `POST /internal/v1/payout-batches/earner-lines` (earner-type partitioned) |
| `docs/02-architecture-techlead/48-marketing/03-api-contracts-and-payloads.md` | reach integration note: retarget email rides the campaign path; eligibility arbitrates per recipient |
| `docs/02-architecture-techlead/32-monetize-overview/03-api-contracts-and-payloads.md` | order-event consumption row (checkout abandoners, past buyers) |
| `MIGRATION.md` | feature 50 row (doc migration landed with this batch; build at amendment) |
