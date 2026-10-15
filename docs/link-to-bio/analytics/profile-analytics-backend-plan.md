# Profile Analytics Backend Plan (beacon + hourly aggregates — server build steps)

PLAN ref: P1d-1 (beacon, 10min dedupe, hourly consumer, R2 raw 90d, zero per-hit PG writes) + P1d-2 (overview/compare/per-link/source-city, QR-as-URL) + S-1/S-5/S-6/S-7. BA source: `docs/link-to-bio/analytics/profile-analytics.md`. Phasing per `docs/platform/delivery/execution-overview.md` (B2). Schema in `docs/link-to-bio/analytics/profile-analytics-database-plan.md` (aggregates, written once). Dashboard screens in the profile-analytics frontend plan (splits next to this file).

## Purpose

Give the backend developer an ordered procedure for the base analytics pipeline: fast beacon intake, dedupe, bot filtering, the scheduled consumer, plan-clamped reads, and the load/abuse proof. Follow steps 1–9 in order. This pipeline is the system's highest-traffic write path — every step below exists to keep PG write volume independent of visitor spikes.

## Users

- Backend developer: executes steps 1–9 in package `com.app.bio.analytics.base`.
- QA: runs zero-write, idempotency, gating, abuse, and load drills.
- Ops: watches `consumer_lag_seconds` and `beacon.rejected{reason}` dashboards.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — monolith with async in-process pipeline. Monolith pros: intake, consumer, and reads share the `bio` transaction + RLS baseline; idempotent natural-key upserts; one lag metric. Microservices cons avoided: extracting ingest adds queue ops, cross-service beacon auth, and dual-lag monitoring for volumes the P1 gate absorbs. Split trigger from `docs/platform/architecture/architecture-decision.md`: beacon ingest extracts first at P4 iff sustained backlog fires ≥2 triggers — intake hides behind `BeaconIngestPort`, the consumer behind `RollupConsumerPort`, so the move is wiring-only. Starters: web, validation, data-jpa, security, scheduling, cache, actuator, micrometer. High-load posture: stateless intake (horizontally replicable with no session affinity), Hikari sized for batch upserts (not per-hit connections), 5s statement timeout.
2. Step 1 — `api/BeaconController.kt`: `POST /api/t/:username` body `{kind: view|click, blockId?, referrer?, utm_*, hints{deviceClass?, browser?}, bannerEvent?}`. Order inside the handler: Turnstile verify → per-IP token-bucket throttle (`429` + `Retry-After`, counted as `beacon.rejected{throttled}`) → crawler-UA denylist check (configurable list; denied counted as `beacon.rejected{bot}`, NOT stored — default excludes known crawlers, see Q1) → username/block existence check (unknown → drop, `202` anyway: no oracle, no write) → enqueue to `BeaconIngestPort` + append raw NDJSON via `RawLogPort`. Respond `202 {ok:true}` fast on every accepted path. Zero PG writes in this handler — asserted by test, not convention.
3. Step 2 — `ingest/DedupeFilter.kt`: 10-minute per (visitorKey, page/block) dedupe in the cache layer before enqueue (visitorKey = HMAC(salted ip+ua, dailySalt) — raw IPs never reach PG). Refreshes and double-taps collapse here; the dedupe key family also feeds new-vs-returning derivation in the audience slice.
4. Step 3 — `ingest/RollupConsumer.kt` `@Scheduled(cron="0 5 * * * *")`: hourly batch → idempotent upserts on the database plan's natural keys (replay-safe) → emit `consumer_lag_seconds` (target <1h at gate) and `rollup.rows_written`. Partial batch failure retries the hour (upsert math makes retries additive-correct).
5. Step 4 — `api/AnalyticsController.kt` (standardized API surface): `GET /api/v1/analytics/overview?range=` and `GET /api/v1/analytics/links?range=` — owner scope (`WHERE user_id`), server-side range clamping (out-of-range → `422 {code:range_clamped, allowed}`), totals + period-over-period + per-link + engagement (clicks/views) + referrer groups + city (from `cf-ipcountry` header captured at intake, city-grain only) + daily series. Versioning: additive-only under `/api/v1` (OpenAPI diff gate); errors are RFC 9457 `ProblemDetail` with stable `code` strings; no breaking renames without a major note.
6. Step 5 — bot/abuse hardening (defense in depth, all counted): Turnstile + WAF/rate rules on `/api/t/*` (S-5); burst mode (sustained 429 ratio) raises a `beacon.abuse` alert and tightens the bucket automatically, recovering on decay; tampered usernames/block ids are dropped post-`202` (attacker learns nothing, costs us nothing).
7. Step 6 — QR rule: QR encodes `/:username` — scans arrive as ordinary deduped views. No separate instrumentation, no QR table.
8. Step 7 — privacy ceiling: city-level geo only, no raw IPs in PG, no cross-profile visitor stitching; raw logs age out on blob TTL. Owner reads 403 on foreign profiles (S-1/S-6).
9. Step 8 — tests + container + load proof: beacon matrix (valid/duplicate/tampered/bot/burst × expected 202/drop/429 + zero PG writes verified in query logs); replay test (same batch twice → single increment); Testcontainers IT (RLS deny, upsert semantics); k6 beacon-soak (sustained hits hold 202 latency p95 with zero per-hit PG writes, consumer lag <1h). Same image with scheduler enabled via `docker run` + `podman run` per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`.

## Data / API touches

- Schema: database plan above (upserts only, natural keys). Endpoints: public throttled `POST /api/t/:username` → `202` / `429`; owner `GET /api/v1/analytics/overview|links?range=` → `200` / `403` / `422 range_clamped`. Metrics: `consumer_lag_seconds`, `rollup.rows_written`, `beacon.rejected{reason}`, `beacon.abuse`.

## Acceptance criteria

- Zero-write drill: sustained beacon load → zero per-hit PG writes (query-log verified); lag <1h; aggregates match raw counts within dedupe tolerance; replay increments once.
- Bot drill: crawler UAs rejected + counted, never stored; burst throttled with `Retry-After`; tampered ids dropped silently.
- Dashboard drill: seeded traffic reproduces views/clicks/engagement/compare/source/city; QR scan = one deduped view.
- IDOR drill: cross-user reads 403 + RLS deny.
- Load drill: k6 soak green; PG write rate flat while beacon rate 10x.
- Container drill: digest pair boots with scheduler green, compose green, rollback green, contract diff clean.

## Out of scope

- Table DDL (database plan above); dashboard screens (frontend plan); audience/UTM depth (audience plans); live feed (realtime plans); earnings unification (read-only); PWA shell; leads/billing/agency; P5 replicas.

## Open questions

- Q1: Crawler denylist default-on with creator opt-out, or count-all with dashboard-side bot toggle?
- Q2: Attribution depth — UTM passthrough stored per click in v1, or source/city only?
