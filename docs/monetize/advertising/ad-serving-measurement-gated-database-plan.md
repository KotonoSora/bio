# Ad Serving Measurement Gated Database Plan (aggregates + evidence — designed, not built)

PLAN ref: none — P4-growth, GATED. BA source: `docs/monetize/advertising/ad-serving-measurement.md`. Consumed by the serving-measurement backend plan (decisioning, pipeline, filters; splits next to this file). Inventory rows live in the ad-inventory database plan (eligibility inputs, never redefined here). No migration — nothing is created until the plan update.

## Purpose

Hold the buildable measurement schema: fraud-filtered aggregates with idempotent rebuild keys, raw evidence logs, visibility/dedupe/quarantine semantics, and the separation guarantee made structural (no write path into creator analytics). Exactly the future `CREATE TABLE`s, written once at plan update; zero tables now.

## Users

- Backend developer (future): runs these scripts once at plan update; consumer upserts by natural keys.
- QA (now): absence drill; (future) counting/separation/dispute/outage drills.
- Dispute reviewer (future): replays any billed event from evidence within TTL.

## Functional requirements

1. Present state: zero ad-serving tables, zero ad endpoints, zero ad consumers. Absence verified per release.
2. Table `ads.ad_impressions` (future) — one row per flight per placement per day per segment: `day`, `flight_id` (FK → flights, cascade? No — tombstone discipline like links: logical reference, history survives flight deletion), `placement_id`, `segment` (coarse targeting bucket), `count`. PK `(day, flight_id, placement_id, segment)` (idempotent rebuild key — replays converge).
3. Table `ads.ad_clicks` (future) — same grain for clicks (redirect-validated clicks only — dedupe window + landing validation passed). Same PK shape. Click floods quarantined from billing aggregates pending review (quarantine rows in a `ads.ad_quarantine` table mirroring the link-hit quarantine pattern: `id, day, flight_id, reason, held_count, status pending|released|dropped`).
4. Raw evidence (future, not PG): impression/click events to blob NDJSON (TTL discipline shared with analytics pipelines) — evidence bundles per billed event (creative, flight, placement, segment, timestamps, dedupe outcome). Aggregates recompute only from filtered events; filter counts reported alongside billed figures (transparency renews contracts — exclusion math travels with every report).
5. Fraud-filter semantics (stored, not prose): bot/crawler exclusion (at minimum the analytics posture; billed traffic defaults stricter — decided at plan update per Q1); impression visibility minimum (viewport-verified via IntersectionObserver signal — unverified views never count, enforced at ingest, not averaged later); click anti-double (dedupe window + landing validation); velocity anomaly quarantine (rule 3 table).
6. Decision inputs (cached, not tables): eligible flights (live window + placement + opt-in + bidirectional category + cap headroom) cached per (placement, coarse segment) short TTL; selection weighted by priority/caps with deterministic tie-breaks (no RTB v1). Empty result collapses the slot (no blank boxes, no layout shift — collapse behavior tested, not hoped).
7. Separation structural: ad pipeline writes ONLY ad tables + blob logs (no code path reaches `profile_views`/`link_clicks` — asserted by the under-load separation test, and by the absence of any cross-write import in the consumer package, ArchUnit-enforced at plan update).
8. Consent posture: first-party only, no cross-site identity; declined-consent visitors get contextual-only ads or collapsed slots per config, never blocked pages (blocking pages over consent would be the dark pattern this rule forbids).

## Data / API touches

- DDL now: none — assertion: pre-update tags show zero ad-serving tables/endpoints/consumers (schema + OpenAPI + source grep). Future: three tables + raw NDJSON per rules 2–4 (full `CREATE TABLE`s written at plan update from this design).
- Performance budget (future): ad decision + render adds a fixed millisecond SSR budget (50ms with collapse-on-timeout per Q2 default); measurement pings are fire-and-forget beacons, never render-blocking.

## Acceptance criteria

- Absence drill (now): zero ad-serving surface every release.
- Decision drill (at build): eligibility matrix per combination; empty collapses with zero layout shift.
- Counting drill (at build): storms dedupe to one; bots excluded per posture; below-visibility uncounted; floods quarantined with billing clean.
- Separation drill (at build): zero cross-rows under load, both directions.
- Dispute drill (at build): any billed event replayed; exclusion math exact.
- Outage drill (at build): queue + sweep recovery with no loss/doubles.

## Out of scope

- Decision/count/filter implementation (backend plan); report/dispute screens (frontend plan); inventory/safety/earnings (sibling plans); creator analytics; affiliate evidence chains; third-party ad-tech; P5.

## Open questions

- Q1: Bot posture shared vs stricter for billed traffic (BA Q1 — decide at plan update; stricter default)?
- Q2: SSR decision budget 50ms collapse-on-timeout (BA Q2 — confirm)?
