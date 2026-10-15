# Audience Insights Backend Plan (segment rollup + clamped reads — server build steps)

PLAN ref: P1d-2 (same beacon/queue as the base pipeline, deeper reads) + S-1/S-6/S-7. BA source: `docs/link-to-bio/analytics/audience-insights.md`. Phasing per `docs/platform/delivery/execution-overview.md` (B2). Schema in `docs/link-to-bio/analytics/audience-insights-database-plan.md` (segments + link_perf, written once). Ingestion/dedupe owned by the profile-analytics backend plan — no beacon or queue changes here. Screens in the audience frontend plan (splits next to this file).

## Purpose

Give the backend developer an ordered procedure for audience depth: extending the hourly consumer with segment projections, the canonical CTR computation, plan-clamped reads with weekday-aligned compare, and the retention job. Follow steps 1–7 in order. No new tracking endpoints, no per-hit PG writes — virality still lands in blob logs.

## Users

- Backend developer: executes steps 1–7 in package `com.app.bio.analytics.audience`.
- QA: runs parity, gating, no-regression, privacy, and compare drills.
- Privacy reviewer: signs off the no-new-PII claim.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — monolith read-side extension. Monolith pros: segment rollups compute inside the existing hourly consumer from the same messages; CTR joins local aggregates; same RLS/clamping path as base reads. Microservices cons avoided: an insights service would re-consume the beacon stream (dual-lag, dual-replay semantics) for a read-only projection. Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; none fire. Starters: web, validation, data-jpa, security, scheduling, actuator, micrometer. Load posture: consumer batch window absorbs spikes; reads are owner-scoped range scans on `(user_id, day)` indexes; no new hot path is introduced by this slice.
2. Step 1 — `service/SegmentRollup.kt` (called by the existing `RollupConsumer`, same transaction): per deduped hit, increment `traffic_segments` rows for dimensions device|geo|referrer|utm|returning with the derivation rules in the database plan; upsert `link_perf` (clicks + `ctr_x1000` recomputed per (profile, block, day) from that day's aggregates). Bot-filtered hits never reach this stage (excluded at intake — segments inherit the bot decision, no second filter to drift).
3. Step 2 — `api/AudienceController.kt` (standardized API surface): `GET /api/v1/analytics/audience?range=&dimension=` + `GET /api/v1/analytics/links/performance?range=` — owner scope, server-side range clamping (free 90d daily; pro 365d + hourly last-7d; out-of-range → `422 {code:range_clamped, allowed}`), every metric with previous-period + year-ago overlays and absolute + percentage deltas; compare windows align on weekday where day counts differ (documented response field `aligned:true` so Monday spikes are not misread). Additive-only `/api/v1`, RFC 9457 problems, stable codes.
4. Step 3 — `job/AggregateRetentionJob.kt` `@Scheduled(cron="0 30 2 * * *")`: delete aggregates (base + audience tables) older than each profile's plan window; downgrade re-clamps reads immediately (read path checks current plan per request, never caches entitlement); aged rows never resurrect. Dry-run count metric first (`retention.would_delete`), then delete.
5. Step 4 — bot/abuse inheritance: this slice adds no intake, so no new abuse surface; burst-mode tightening at intake automatically protects segment computation (fewer accepted hits → smaller batches). Abuse counters shared with the base pipeline.
6. Step 5 — privacy enforcement in code: projection functions accept only the whitelisted beacon fields (DTO without IP/visitor-id fields exists at compile time — a new PII field cannot flow here without a type change, which review catches); UTM values length-capped (anti-cardinality: over-long values truncated + counted, see database plan Q2).
7. Step 6 — observability: `rollup.segments_written`, `retention.deleted`, per-dimension cardinality gauge (UTM key-count alert threshold — early warning for typo-split explosions).
8. Step 7 — tests + container: parity test (seeded traffic → hand-computed splits/UTM/CTR); gating test (free 365d/hourly → 422 with allowed range); no-regression test (payload size + throughput unchanged vs base; zero per-hit writes holds); privacy test (column-list assertion on both tables); compare test (weekday-aligned fixture with known spikes). Same image via `docker run` + `podman run` per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`.

## Data / API touches

- Schema: database plan above (consumer writes via natural-key upserts; no beacon/queue changes). Endpoints: `GET /api/v1/analytics/audience`, `GET /api/v1/analytics/links/performance` (owner-scoped, plan-clamped). Metrics: `rollup.segments_written`, `retention.deleted`, `segments.cardinality{dimension}`.

## Acceptance criteria

- Parity drill: seeded traffic reproduces splits/UTM/CTR exactly within dedupe tolerance.
- Gating drill: out-of-plan reads clamp with the allowed range stated; direct API cannot bypass.
- No-regression drill: beacon throughput/payload unchanged; zero per-hit writes holds.
- Privacy drill: no IP/visitor-id columns; stitching returns nothing.
- Compare drill: weekday-aligned overlays verified on spike fixture.
- Container drill: digest pair boots, compose green, rollback green, contract diff clean.

## Out of scope

- Table DDL (database plan above); audience screens (frontend plan); beacon/dedupe/queue (base analytics plans); live feed; earnings unification; PWA shell; leads/campaigns; agency leaderboards.

## Open questions

- Q1: Free hourly teaser (last 24h) — conversion lever, or pro-only entirely?
- Q2: UTM registry vs freeform (see database plan Q2 — decide once, both files agree)?
