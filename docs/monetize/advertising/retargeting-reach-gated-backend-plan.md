# Retargeting Reach Gated Backend Plan (evaluator + flights + lift — buildable at plan update)

PLAN ref: none — P4-growth, GATED. BA source: `docs/monetize/advertising/retargeting-reach.md`. Storage contract in the retargeting database plan above (segments, TTL'd membership, holdout flights; nothing created now). Screens in the frontend plan (splits next to this file).

## Purpose

Hold the buildable server design for first-party retargeting: minutes-scale evaluation off existing pipelines, consent-gated eligibility, frequency discipline, and holdout-first lift reporting. Until the plan update, this file's only executable content is the absence gate.

## Users

- Backend developer (future): ships evaluator + flights + lift reporting in package `com.app.ads.retargeting` (GATED).
- QA (now): asserts absence; (future) segment/consent/fatigue/lift drills.
- Creator/advertiser/privacy reviewer (future): builds segments, serves them lawfully, verifies boundaries.

## Functional requirements

1. Stack and topology (when built): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — monolith evaluator + flight services (package `com.app.ads.retargeting`, GATED). Monolith pros: evaluator joins beacon/order streams in-process; eligibility checks share consent-model code; one audit surface. Microservices cons avoided: a segment service for minutes-cadence batch evaluation with no ≥10x asymmetry. Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; unevaluated until the plan update. Starters (when built): web, validation, data-jpa, security, scheduling, cache, actuator, micrometer. Load posture: evaluator is a minutes-cadence consumer (bounded batches, dedupe-tolerant counts); serving reads membership short-TTL cached, PG truth.
2. Step 1 — evaluator (GATED): consumer over beacon/order event streams (minutes cadence) writing TTL'd membership per the database plan's closed event set + lookbacks + caps; definition versions pinned per segment (redefinition versions, never mutates running meaning); sizes labeled `as of` (honest lag, not fake realtime).
3. Step 2 — eligibility (GATED): send/serve eligibility arbitrates consent per recipient at decision time (lifecycle reminders pass on transactional consent; promo variants require marketing opt-in — refusal logged with reason); per-creator membership only (cross-creator pooling impossible by query shape, tested); segment-targeting plugs into ad decisioning as one more dimension (owned by serving-measurement slice; this slice supplies membership reads).
4. Step 3 — flights + fatigue (GATED): launch/pause over onsite + owned-email surfaces; per-segment contact caps enforced in send paths (creator-tunable within ceilings); past-buyer exclusion default; cause + one-click stop on every message (template-asserted); control slice reserved per flight (no-contact enforced in eligibility, not just reporting).
5. Step 4 — lift reporting (GATED): size, reach, conversion, suppression counts vs control (never vs zero); reconciliation to underlying events within dedupe tolerance; no lift claim without its control slice (report generator refuses control-less flights — absence of control blocks the report, not just the claim).
6. Step 5 — abuse/privacy: Turnstile + throttles on segment/flight endpoints; export-refusal by absence (no upload surface exists to secure — asserted by route absence); raw-hit browsing ops-only per analytics posture.
7. Step 6 — gate enforcement NOW: zero segment/evaluator/flight/lift surface in current images (grep + migration audit fails the tag on any `segments|segment_membership|retarget_flights|retarget/` surface).

## Data / API touches

- Schema: database plan above (none now). Endpoints (GATED, absent from current OpenAPI): segment CRUD + preview-size, flight launch/pause, lift report (owner-scoped; ops platform aggregates only).

## Acceptance criteria

- Absence drill (now): zero retargeting surface pre-update.
- Segment drill (at build): exact membership/expiry/exclusion from fixtures.
- Consent drill (at build): lifecycle-only for opted-out; promo refused + logged.
- Fatigue drill (at build): caps + exclusions + cause + stop across surfaces.
- Lift drill (at build): holdout reconciliation within tolerance; control-less reports refused.

## Out of scope

- Segment/membership/flight contracts (database plan above); segment/flight screens (frontend plan); inventory/decisioning/counting/earnings; broadcasts/consent classes (marketing backlog); leads; third-party tracking (never v1).

## Open questions

- Q1: Minutes vs hourly evaluation bar (see database plan Q1)?
- Q2: Off-platform export posture (see database plan Q2)?
