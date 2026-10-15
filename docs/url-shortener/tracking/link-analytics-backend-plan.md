# Link Analytics Backend Plan (hit pipeline + fraud filters — server build steps)

PLAN ref: P2 delivery (honest numbers per `docs/PLAN.md`) + S-1/S-5/S-6/S-7. BA source: `docs/url-shortener/tracking/link-analytics.md`. Phasing per `docs/platform/delivery/execution-overview.md` (E2). Schema in `docs/url-shortener/tracking/link-analytics-database-plan.md` (aggregates + quarantine, written once). Mint/resolve owned by the short-links backend plan (this pipeline never touches redirect latency). Dashboards in the analytics frontend plan (splits next to this file).

## Purpose

Give the backend developer an ordered procedure for trustworthy short-link counts: the async hit pipeline that never slows redirects, v1 fraud filters, scan-vs-click estimation, owner/affiliate readouts, and dispute replay with exact exclusion math. Follow steps 1–8 in order. Advertisers and affiliates spend on these numbers — approximate is not a setting here.

## Users

- Backend developer: executes steps 1–8 in package `com.app.links.analytics`.
- QA: runs counting, hot-path, flood, separation, and replay drills.
- Dispute reviewer: replays any figure from evidence within TTL.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — modular monolith (`links` module, in-process consumer). Monolith pros: resolve → enqueue → consumer shares the links transaction baseline; natural-key upserts; one lag metric; no distributed exactly-once machinery. Microservices cons avoided: extracting counting adds queue ops + cross-service auth on the millisecond hot path for volumes batching already absorbs. Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; none fire. Starters: web, validation, data-jpa, security, scheduling, cache, actuator, micrometer. Load posture: hot path = validate → redirect → enqueue (benchmarked with counting on vs off, delta ≈ 0 as a gate); PG writes happen in scheduled batches only; queue outage degrades to sweep-repair (raw logs replayed, no loss, no doubles — outage mode specified, not improvised).
2. Step 1 — hit enqueue (resolve side, owned jointly with the short-links plan): hit `{short_code, at, visitorKey(HMAC salted, 10-min dedupe family), referrer, hints, purpose headers, scan signals}` appended to raw NDJSON + queued. No PG write here (asserted in query logs, not code-reviewed).
3. Step 2 — `LinkHitConsumer` (scheduled batch): dedupe (visitor/link 10-min window, same discipline as profile beacons) → fraud filters in fixed order: bot/crawler exclusion (shared posture decision with creator analytics; money-tied traffic defaults to the stricter setting — see database plan Q1) → prefetch/prerender suppression (purpose headers where present; speculative loads never count) → double-open collapse → velocity check: single-source floods branch to quarantine (NOT aggregates) with an ops case opened automatically.
4. Step 3 — scan-vs-click split: absent-referrer + mobile + camera-class UA → scan-leaning (heuristic, stored in the separate `scans` counter, labeled `estimated` at every readout — never asserted); event-pack per-location codes give exact splits regardless (location code IS the attribution, no heuristic needed).
5. Step 4 — readouts (standardized API): owner `GET /api/v1/links/:code/stats` (opens/uniques curve, top referrers, device split, scan share, geo-country, exclusion counts per reason) + `GET /:code/stats.csv` (streamed, throttled); affiliate `/r/` and ad `/a/` consumers read through their own scoped endpoints against the same aggregates (same numbers, no cross-link visibility — scope enforced per caller, not per table). Aggregates only for creators; raw-hit browsing restricted to the ops dispute lookup.
6. Step 5 — evidence and transparency: every readout carries filter-exclusion counts (what was dropped and why, per reason); any reported figure replays from retained evidence within TTL (`raw − excluded = reported` holds exactly — the replay test asserts the equation, not just the totals). No metric exists that cannot be decomposed to underlying hits.
7. Step 6 — separation (structural): short-link hits never enter `profile_views`/`link_clicks` and vice versa (no cross-write path exists in either consumer — asserted by the under-load separation test, not by convention). The bio-block dual case is documented in-UI: short-link open + block click are two true events (different systems, both correct — double-billing confusion prevented by labeling, not by suppression).
8. Step 7 — consent posture: first-party only, no cross-link identity resolution (same structural privacy as creator analytics — no visitor-id columns exist); declined-consent traffic contributes aggregate-safe counts only.
9. Step 8 — tests + container + abuse: counting matrix (genuine/prefetch/bot/double/flood × counted-or-excluded); hot-path benchmark gate; flood test (legitimate same-window traffic unaffected); separation test under load; replay test (equation holds); `@WebMvcTest` (403 cross-link, 429 on floods); Testcontainers IT (RLS deny, upsert idempotency, quarantine branch exclusivity). Same image via `docker run` + `podman run` per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`.

## Data / API touches

- Schema: database plan above (upserts by natural keys; quarantine branch). Endpoints: owner `GET /api/v1/links/:code/stats` + `GET /:code/stats.csv` (scoped, throttled); ops dispute replay lookup; affiliate/ad scoped stats (their minors, same aggregates). Ingestion has no public write API beyond the resolve path. Metrics: `linkhits.lag`, `linkhits.excluded{reason}`, `linkhits.quarantined`, consumer batch sizes.

## Acceptance criteria

- Counting drill: scripted opens reconcile hit-for-hit; prefetch/bot excluded per posture.
- Hot-path drill: resolve latency delta counting on/off ≈ 0; queue outage → sweep-repair with no loss/doubles.
- Flood drill: quarantined with ops case; legitimate traffic unaffected; release-once/drop-with-evidence.
- Separation drill: zero cross-rows under load; dual case labeled, not suppressed.
- Replay drill: equation holds exactly within TTL.
- Container drill: digest pair boots, compose green, rollback green, contract diff clean.

## Out of scope

- Aggregate/quarantine DDL (database plan above); dashboards/dispute screens (frontend plan); mint/resolve/redirect (short-links plans); QR rendering (qr plans); creator analytics (link-to-bio plans); affiliate/ad measurement (consumers); custom short domains; P4/P5.

## Open questions

- Q1: Shared vs strictest-wins bot posture (see database plan Q1 — decide once)?
- Q2: Sweep-repair window for queue outages — hours or days of raw retention headroom?
