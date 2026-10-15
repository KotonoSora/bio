# Earnings Dashboard Backend Plan (read-only aggregator — server build steps)

PLAN ref: P1d-2 + P2-2/P2-3/P3 phase-gated revenue (no tables built here) + S-1/S-6/S-7. BA source: `docs/link-to-bio/analytics/earnings-dashboard.md`. Phasing per `docs/platform/delivery/execution-overview.md` (B2). Contracts in `docs/link-to-bio/analytics/earnings-dashboard-database-plan.md` (flags, funnel formulas, labeling — never redefined here). Screens in the earnings frontend plan (splits next to this file).

## Purpose

Give the backend developer an ordered procedure for the unification layer: capability-gated fan-out, funnel assembly, CSV streaming, caching, and the proof that money is never restated. Follow steps 1–7 in order. This layer merges at most a handful of sources per view — bounded, timed, and Partial-failure-tolerant by construction (high-traffic safety for a dashboard endpoint).

## Users

- Backend developer: executes steps 1–7 in package `com.app.bio.analytics.unified`.
- QA: runs gating, consistency, attribution, honesty, and abuse drills.
- Support: traces any figure one hop to its origin.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — monolith read aggregator. Monolith pros: fan-out over aggregates + rollups in one request scope with one owner check; one short-TTL cache; one CSV codec. Microservices cons avoided: a dashboard BFF adds a second aggregation hop (double clamping risk, double-lag labels) for a read-only merge. Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; none fire. Starters: web, validation, data-jpa, security, cache, actuator, micrometer. Load posture: coroutine fan-out with 2s per-source timeout (slow source degrades to `source:"unavailable"` cell, never stalls the view); `private, max-age=60` response cache; CSV streams (never materializes full history in memory).
2. Step 1 — `service/CapabilityReader.kt#flags(profileId)`: read the flag set from the database plan (storage owned by revenue pipelines); unknown/absent flag → strip omitted. Pre-revenue → engagement-only payload, zero money keys (the frontend cannot render what the payload lacks — enforcement by absence, not by UI hiding).
3. Step 2 — `service/UnifiedAggregator.kt#unified(userId, profileId, range)`: owner check + plan-range clamping inherited from the audience rules → parallel fetch (analytics aggregates + flagged revenue rollups, 2s timeout each, partial failure → per-cell `unavailable`) → assemble funnel rows per the database plan's formulas (same-link same-window division; purchase-day attribution) → attach `{source, lag, generatedAt}` per cell → gross-vs-net labels verbatim from sources.
4. Step 3 — `api/UnifiedController.kt`: `GET /api/v1/analytics/unified?range=` → `200 {generatedAt, rows, totals}` (`private, max-age=60`); `GET /api/v1/analytics/unified.csv?range=` → streamed CSV with `source,generated_at` columns (chunked encoding, backpressure-safe, owner-scoped, rate-limited against export abuse). Standardized API: additive-only `/api/v1`, RFC 9457 problems, stable codes, cross-user → 403, out-of-plan range → `422 range_clamped`.
5. Step 4 — abuse hardening: unified + CSV endpoints share the owner throttle family (per-user token bucket; CSV stricter — large exports are the abuse vector); repeated `422` probing counted, not punished (clamping is information, not a secret).
6. Step 5 — realtime interplay: live ticks bypass this cache entirely (served per the realtime plans); the unified view embeds the ticks client-side, never mixing grains server-side (hourly aggregates + live ticks in one server number would corrupt both — separation asserted in review).
7. Step 6 — observability: `unified.fanout_ms{source}`, `unified.source_unavailable{source}`, `unified.csv_bytes`; per-source timeout tuning reads these dashboards, not guesses.
8. Step 7 — tests + container: gating test (pre-shop → engagement-only payload, zero money keys); consistency test (unified == origins at same `generatedAt`; CSV cell-for-cell); attribution test (mismatched-window fixture fails); honesty test (lag labels + gross/net present); IDOR test (cross-user 403 on both endpoints); abuse test (CSV flood throttled, streaming memory flat). Same image via `docker run` + `podman run` per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`.

## Data / API touches

- Schema: database plan above (zero new tables; flags + rollups owned elsewhere). Endpoints: `GET /api/v1/analytics/unified?range=`, `GET /api/v1/analytics/unified.csv?range=` (owner-scoped, plan-clamped, throttled, short-TTL private cache). Metrics: `unified.fanout_ms`, `unified.source_unavailable`, `unified.csv_bytes`.

## Acceptance criteria

- Gating drill: pre-revenue payload has zero money keys; shipped sources light strips within pipeline lag.
- Consistency drill: figures match origins at same `generatedAt`; CSV reconciles cell-for-cell.
- Attribution drill: same-window division verified; mismatched fixture fails.
- Honesty drill: lag + gross/net labels everywhere; no restatement.
- Abuse drill: CSV flood throttled with flat memory (streaming proven).
- Container drill: digest pair boots, compose green, rollback green, contract diff clean.

## Out of scope

- Flag/formula contracts (database plan above); unified screens (frontend plan); analytics aggregates (their database plans); order/billing storage (owning majors); campaign/ad accounting; agency reporting (P4).

## Open questions

- Q1: Per-source timeout 2s — hold under P5 replica lag, or adaptive?
- Q2: CSV retention of `generated_at` — keep per-row, or per-file header suffices?
