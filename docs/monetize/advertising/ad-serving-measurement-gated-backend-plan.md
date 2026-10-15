# Ad Serving Measurement Gated Backend Plan (decisioning + pipeline — buildable at plan update)

PLAN ref: none — P4-growth, GATED. BA source: `docs/monetize/advertising/ad-serving-measurement.md`. Storage contract in the serving-measurement database plan above (aggregates + quarantine + evidence; nothing created now). Report screens in the frontend plan (splits next to this file).

## Purpose

Hold the buildable server design for ad decisioning and honest counting: cached eligibility decisioning with collapse discipline, the async fraud-filtered pipeline, scan-grade evidence bundles, and outage recovery. Until the plan update, this file's only executable content is the absence gate.

## Users

- Backend developer (future): ships decide API + consumer + click redirect + ops reports in package `com.app.ads.serving` (GATED).
- QA (now): asserts absence; (future) decision/counting/separation/dispute/outage drills.
- Advertiser/dispute reviewer (future): trusts and replays billed numbers.

## Functional requirements

1. Stack and topology (when built): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — monolith decisioning + in-process consumer (package `com.app.ads.serving`, GATED). Monolith pros: eligibility joins (window + placement + opt-in + category + caps) evaluate in one request scope; pipeline shares the links/analytics consumer patterns. Microservices cons avoided: a decisioning service adds a network hop inside the SSR render path (the 50ms budget forbids it). Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; unevaluated until the plan update. Starters (when built): web, validation, data-jpa, security, scheduling, cache, actuator, micrometer. Load posture: decision cached per (placement, segment) short TTL; pings async fire-and-forget; zero per-hit PG writes.
2. Step 1 — decide API (GATED, server-to-server): eligible flights (live window + placement + opt-in + bidirectional category + cap headroom) → weighted selection (priority/caps, deterministic tie-breaks, no RTB v1) → cached short TTL → empty collapses the slot. Timeout/error → collapse (page never waits on ads — collapse is the SLA, not degradation).
3. Step 2 — measurement pipeline (GATED): ad-view pings + ad-click redirects (`GET /api/a/:ref` public, throttled, signed creative reference — tampered refs rejected) → queue (never direct PG) → 10-min dedupe (visitor/slot/creative) → fraud filters (bot exclusion per posture, viewport-verified impressions only, click anti-double + landing validation, velocity quarantine) → aggregate upserts by natural keys → raw NDJSON evidence.
4. Step 3 — evidence bundles (GATED): per billed event (creative, flight, placement, segment, timestamps, dedupe outcome); aggregates recompute only from filtered events; filter counts reported with every billed figure (exclusion math travels, not footnotes).
5. Step 4 — failure behavior (GATED): measurement outage → bounded-retry queue then sweep-and-repair (no silent loss, no doubles via dedupe keys — same discipline as money paths).
6. Step 5 — abuse: Turnstile + WAF/rate on click-redirect + report endpoints; velocity alerts; quarantine review queue (ops decide release/drop with audit).
7. Step 6 — gate enforcement NOW: zero ad-serving tables/endpoints/consumers/jobs in current images (grep + migration audit fails the tag on any `ad_impressions|ad_clicks|ad_quarantine|/api/a/` surface).

## Data / API touches

- Schema: database plan above (none now). Endpoints (GATED, absent from current OpenAPI): internal decide API; public `GET /api/a/:ref` click redirect; ops `GET /api/v1/ops/ad-reports/:flight`; advertiser report reads (own flights only).

## Acceptance criteria

- Absence drill (now): zero ad-serving surface pre-update.
- Decision drill (at build): matrix per combination; empty collapses, zero layout shift.
- Counting drill (at build): dedupe/bot/visibility/flood rules verified with billing clean.
- Separation drill (at build): zero cross-rows under load.
- Dispute drill (at build): replay + exact exclusion math.
- Outage drill (at build): queue + sweep recovery lossless.

## Out of scope

- Aggregate/evidence contracts (database plan above); report/dispute screens (frontend plan); inventory/safety/earnings; creator analytics; affiliate chains; third-party ad-tech.

## Open questions

- Q1: Bot posture (see database plan Q1)?
- Q2: 50ms SSR budget (see database plan Q2)?
