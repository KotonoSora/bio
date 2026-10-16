# Earnings Dashboard - Architecture and Tech Stack (feature 15)

Phase 2 TL · BA sources: `docs/01-requirements-ba/15-earnings-dashboard/` (7 files). Base pipeline: `docs/02-architecture-techlead/12-profile-analytics/`; segments + CTR: `docs/02-architecture-techlead/13-audience-insights/`; live contracts: `docs/02-architecture-techlead/14-realtime-global/`. Stack pins per `docs/02-architecture-techlead/00-platform-baseline/`.

## Service boundary: in-service read aggregator

No new service, container, dependency, or migration. Feature 15 extends `analytics-service` (12-14) as a read-only unification layer inside the same compose slice.

| Component | Change | Notes |
|---|---|---|
| `CapabilityReader` (new class) | Reads the four capability flags per profile | Storage owned by revenue pipelines (22-26, 32-40); fail-closed omit; unknown flags ignored |
| `UnifiedAggregator` (new class) | Owner check + clamp -> local analytics read (12-13) + flagged remote rollup reads | Coroutine fan-out; 2 s timeout per source; partial -> `unavailableCells` |
| `RevenueRollupPort` (new interface) | Forward contract: flagged units/revenue rollup read per link + window | Provider owned by shop/monetize; v1 returns absent (no flags exist yet) |
| `UnifiedController` (new class) | `GET /api/v1/analytics/unified` + `GET /api/v1/analytics/unified.csv` | `private, max-age=60`; owner throttle family; RFC 9457 codes |
| `CsvStreamer` (new class) | Chunked, pull-based CSV with per-row source columns | Memory bounded; cancel stops the stream |
| `analytics_db` | Unchanged (V1-V2; zero new DDL) | Asserted in `02-database-schema-design.md` |
| Compose / `build.gradle.kts` | Unchanged - web, validation, data-jpa, security, cache, actuator, micrometer already present (12) | No new starter |

## Key decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | In-service aggregator, not a dashboard BFF | A BFF adds a second aggregation hop (double clamping, double lag labels) for a read-only merge; legacy monolith verdict holds |
| D-2 | Capability flags are a closed set: `has_tip_orders`, `has_shop_orders`, `has_course_orders`, `has_service_orders`; feature 14's `has_orders` resolves to their union | One registry, no drift; a new revenue stripe requires a spec amendment, never an inferred panel |
| D-3 | CTR consumed from 13's `ctr_x1000` unchanged; `rpc` is defined here as the only new derived figure (`revenue / clicks`, same window, round-half-up, null when clicks = 0) | One formula platform-wide (13 BR-15); no second CTR exists |
| D-4 | Purchase-day attribution, documented as a rule line | A finance change amends the spec line, never branches code |
| D-5 | Fixed 2 s per-source timeout; timeout -> `unavailableCells` on that group only | Partial failure never stalls the view; adaptive timeouts wait for metrics evidence |
| D-6 | Server-side pagination at 25 rows; CSV streams the full set | Bounded payloads; export never materializes history in memory |
| D-7 | Grain separation is structural: live ticks render client-side via 14's components; the unified payload never carries live counters | One number never mixes hourly aggregates with minute buckets |
| D-8 | Fail-closed reads: flag absent -> cell absent; unknown flag -> ignored; entitlement snapshot miss -> free clamp (13 pattern) | Absence is the design; no panel is ever inferred |

## Reuse map

- Engagement aggregates + ranges/clamps: 12 (`docs/02-architecture-techlead/12-profile-analytics/04-api-contracts-and-payloads-part-2.md`); segments + `ctr_x1000`: 13 (T-04).
- Live ticks + sales seam posture: 14 (`01-architecture-and-tech-stack.md`, D-6); `has_orders` union resolution above.
- Trace destinations: audience tab (13) and order lists (22-26, 32-40, forward); PWA shell + same-numbers: feature 11.
- Entitlement snapshot pattern: 13 D-7; owner-scoping + RLS baseline: 12 S-5.
