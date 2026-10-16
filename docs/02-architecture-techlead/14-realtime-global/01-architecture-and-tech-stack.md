# Realtime Global - Architecture and Tech Stack (feature 14)

Phase 2 TL · BA sources: `docs/01-requirements-ba/14-realtime-global/` (7 files). Base pipeline: `docs/02-architecture-techlead/12-profile-analytics/`; segment extension: `docs/02-architecture-techlead/13-audience-insights/`. Stack pins per `docs/02-architecture-techlead/00-platform-baseline/`.

## Service boundary: in-service ephemeral extension

No new service, container, queue, dependency, or Flyway migration. Feature 14 extends `analytics-service` (12) inside the same compose slice; all state is cache-only.

| Component | Change | Notes |
|---|---|---|
| Intake handler (12) | Calls `MinuteBucketWriter` after the accept decision (post-dedupe, post-bot, post-resolve) | One pipelined Redis op family; failure never blocks the 202 |
| `MinuteBucketWriter` (new class) | `HINCRBY rt:{profileId}:{minute}` (`v`, `c`, `l:{blockId}`, `g:{CC}`) + `EXPIRE` + trim-on-new-field | Package `analytics.realtime`; caps 16 links / 10 geo (D-3) |
| `RealtimeMergeService` (new class) | Pipelined `HGETALL` of the last 30 buckets; sums + rankings | Bounded O(30); PG never touched |
| `RealtimeController` (new class) | `GET /api/v1/analytics/realtime?profile=` | Pro gate, owner check, throttle, degraded shape |
| `RtTokenBucket` (new class) | `rtpoll:{profileId}:{userId}`, 1 req/10 s -> `429 rate_limited` | Server backstop for dashboard-tab farms |
| `SalesTickReader` (new class, seam) | `has_orders(profileId)` capability check; v1 always absent | Forward contract (D-6) |
| `analytics_db` | Unchanged (V1-V2; zero realtime DDL) | Asserted in `02-database-schema-design.md` |
| Compose / `build.gradle.kts` | Unchanged - web, validation, security, cache, actuator, micrometer already present (12) | No websocket/SSE starter in v1 |

## Key decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | Short-poll only; no SSE/websocket service | 15-30 s cadence edge-caches acceptably; stateful sockets buy nothing at this cadence |
| D-2 | Bucket writes ride the intake handler, not the rollup consumer | 12 D-3 moved raw flush to drain; intake is the only per-accepted-hit hook. Realtime therefore never lags with the rollup consumer; consumer lag degrades hourly aggregates only (12's stale chip) |
| D-3 | Top-N at write implemented as trim-on-new-field: caps 16 link / 10 geo fields per bucket; victim = lowest count, ties lexicographic | Bounds bucket size without write-time sorting; reads rank top-8/top-5 from survivors |
| D-4 | Degraded = merge failure only; quiet profile stays `ok` with zeros | "Consumer lag" cannot gate this path (D-2); missing buckets alone would mislabel quiet profiles as degraded |
| D-5 | `rtgood:{profileId}` last-successful-merge instant (SET per merge, TTL 24 h) drives `lastGoodAt`; null when never good | Truthful last-good instant; no fabricated freshness |
| D-6 | Sales seam: response-level `sales?` + `has_orders` capability forward contract (shop 22-26 / monetize 32-40); zero bucket sales fields in v1 | No shop stub is built; absence is the design |
| D-7 | Entitlement per request via the platform snapshot (13 D-7 pattern); demotion swaps panel to teaser on next poll; teaser issues zero fetches | Plan changes apply without session churn |
| D-8 | Owner read cache header `private, max-age=10` (deliberate vs 12's `no-store`) | Minute-grain numbers tolerate 10 s staleness; per-user private only; PWA worker stays network-only (feature 11 BR-07) |

## Reuse map

- Accepted-hit decision (dedupe, bot filter, resolve, block check): 12 part 1 - buckets mirror it exactly; no second filter exists.
- Entitlement snapshot (<= 30 s staleness): 13 D-7; plan claim also drives teaser eligibility without a call.
- Block titles + `Deleted link` labeling: 12 part 2 `LinkRow` semantics.
- PWA same-numbers + network-only `/api/*`: feature 11 (`docs/02-architecture-techlead/11-deep-links-and-pwa/03-api-contracts-and-payloads.md`).
- Sales/order rollups: forward seam owned by shop 22-26 / monetize 32-40; earnings unification is feature 15.
