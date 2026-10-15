# Realtime Global Backend Plan (30-min live window — server build steps)

PLAN ref: P1d-2 (pro-gated realtime-30min; sales conditional on P2–P3 order data, no shop stubs) + S-1/S-5/S-6/S-7. BA source: `docs/link-to-bio/analytics/realtime-global.md`. Phasing per `docs/platform/delivery/execution-overview.md` (B2). Ephemeral state in `docs/link-to-bio/analytics/realtime-global-database-plan.md` (bucket contract, no PG). Historical reads in the profile/audience backend plans. Panel in the realtime frontend plan (splits next to this file).

## Purpose

Give the backend developer an ordered procedure for the live window: bucket writes, the throttled short-poll endpoint, pro gating, degraded mode, and the conditional sales strip. Follow steps 1–8 in order. Realtime is an operational view, never the billed truth — aggregates win every dispute, and the panel says so.

## Users

- Backend developer: executes steps 1–8 in package `com.app.bio.analytics.realtime`.
- QA: runs liveness, gating, load, sales-conditional, and authority drills.
- Pro creator (via panel): watches launches minute-by-minute; free sees a locked teaser with zero polling.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — monolith ephemeral-counter path. Monolith pros: increments ride the existing consumer path; 30-bucket merges happen in-request with no PG touched; one throttle/audit surface. Microservices cons avoided: a websocket/SSE service adds stateful connections for a 15–30s poll that edge-caches acceptably, plus a second counter store. Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; none fire. Starters: web, validation, security, cache, actuator, micrometer. No websocket in v1 — short-poll only (revisit SSE only on polling-cost evidence). High-load posture: polls are `private, max-age=10` (edge-mergeable per user), merges are bounded `O(30)` cache reads, and per-profile throttling caps dashboard-tab farms.
2. Step 1 — `ingest/MinuteBucketWriter.kt`: on each accepted beacon hit, `CachePort.incr("rt:{profileId}:{minute}", {views, clicks, link:blockId, geo})` with ~1h TTL, top-N truncation at write. Failures degrade (log + `rt.write_error` counter, never block intake). Bot-filtered/throttled hits never arrive here.
3. Step 2 — `api/RealtimeController.kt`: `GET /api/v1/analytics/realtime?profile=` — pro-gated (`PlanGate.isPro`, else `403 {code:realtime_forbidden}` + locked-panel UX, no fetch retried), owner check (foreign profile → 403), per-profile-per-user token bucket (1 req/10s; excess → `429` + backoff, counted). Merges last 30 buckets → `{generatedAt, windowS:1800, visitors, cpmSeries[30], topLinks, geo, sales?}` with `Cache-Control: private, max-age=10`.
4. Step 3 — degraded mode: cache outage or consumer lag → `200 {status:"degraded", lastGoodAt}` (dashboard stays up, panel shows the banner). Never fall back to PG reads (cascade rule from the database plan).
5. Step 4 — `service/SalesTickReader.kt` (conditional): if capability flag `has_orders(profileId)` (owned by shop/monetize pipelines), attach revenue now-line + units-per-link with pipeline-lag labels; else omit `sales` entirely (absent key, never zero-filled). Reads existing rollups (referenced, never redefined).
6. Step 5 — bot/abuse posture: dashboard-tab farms are the threat model (many tabs × fast polling = self-DDoS via KV). Defenses: per-profile throttle (step 2) + global poll-rate alert + free-tier no-poll rule (locked panel issues zero requests). Global platform-wide rollups are ops-only, never creator-visible.
7. Step 6 — authority rule: panel footnote + support runbook cite hourly aggregates as truth over the now-line (screenshot disputes resolve in one sentence). Privacy: country/city only, no IPs, no cross-profile stitching. Push/alerts: none in v1 (named v2).
8. Step 7 — standardized API: additive-only `/api/v1`, RFC 9457 problems with stable codes (`realtime_forbidden`, `rate_limited`), `Retry-After` on 429.
9. Step 8 — tests + container + load proof: liveness test (scripted visits surface within two poll cycles; quiet → zero); gating test (free → 403; global rollup refuses creator creds); burst test (10x poll burst throttled, counter error rate flat, PG writes unchanged); sales-conditional test (no-order → no key; with-order → ticks within stated lag); authority fixture (live/aggregate divergence → footnote cited). Same image via `docker run` + `podman run` per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`.

## Data / API touches

- State: database plan above (ephemeral buckets only; sales reads owned elsewhere). Endpoint: `GET /api/v1/analytics/realtime?profile=` → `200` snapshot (`private, max-age=10`) / `200 degraded` / `403` / `429`. Metrics: `rt.write_pressure`, `rt.write_error`, `realtime.poll_throttled`, `consumer_lag_seconds` (shared).

## Acceptance criteria

- Liveness drill: visits surface within two poll cycles; quiet reads zero.
- Gating drill: free direct call 403 with zero polling from the locked panel; global endpoint refuses creators.
- Load drill: 10x burst throttled; PG writes unchanged realtime-on vs off.
- Sales drill: conditional key presence/absence exact; ticks match rollups within lag.
- Authority drill: divergence fixture → footnote + runbook cite aggregates.
- Container drill: digest pair boots, compose green, rollback green, contract diff clean.

## Out of scope

- Bucket contract (database plan above); panel UI (frontend plan); historical/audience reads; earnings unification; PWA plumbing; order/billing tables; native apps/push (v2); agency/global leaderboards (P4).

## Open questions

- Q1: Alert thresholds ("notify at 1k live") — v2 realtime or P4 automation?
- Q2: Poll vs SSE — revisit only on polling-cost evidence?
