# Advertising (Inventory, Serving, Creator Share, Retargeting) - Step-by-Step Implementation Plan (feature 50) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/50-advertising/`. Contracts: `docs/02-architecture-techlead/50-advertising/03-api-contracts-and-payloads.md`. GATED: T-01/T-02 run now; every `AT AMENDMENT` task is blocked until the spec amendment (BR-01) and must not ship in any current batch.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | DevOps | LIVE: absence-drill tooling — CI step asserting zero ad surface (schema diff, OpenAPI diff, source + bundle grep: `ad_*`, `/api/a/`, decide route, segment builder, earnings UI) | - | AC-01 green; violation blocks promotion by tooling |
| T-02 | DevOps | LIVE: release-checklist entry — advertising remains gated; an amendment record is required before any build task starts | T-01 | Gate cannot be lifted silently |
| T-03 | BE | AT AMENDMENT: `ads_db` bootstrap + `V1__ads.sql` (nine tables + RLS + aggregate-only upserts) | amendment; 01 D-1 | Schema + separation probes green |
| T-04 | BE | AT AMENDMENT: `PlacementCatalog` + `FlightService` (creatives, pre-approval, launch discipline, takedown ≤ 5 min) | T-03 | AC-02, AC-03 |
| T-05 | BE | AT AMENDMENT: `DecisionService` (eligibility matrix, cache, deterministic ties, 50 ms cap, collapse) | T-03 | AC-07; NFR-03 |
| T-06 | BE | AT AMENDMENT: `MeasurementPipeline` (queue, dedupe, aggregates, raw NDJSON, evidence bundles, filter counts) | T-03 | AC-08, AC-09, AC-10 |
| T-07 | BE | AT AMENDMENT: `FraudFilters` (bots, viewport, click anti-double + landing validation, velocity quarantine) | T-06 | AC-08; billing aggregates clean |
| T-08 | BE | AT AMENDMENT: `EarningsLedger` (estimates, monthly close, rate snapshots, clawbacks with evidence) | T-06 | AC-11, AC-12, AC-13 |
| T-09 | BE | AT AMENDMENT: payout handoff to 49's runner (earner-type `creator_ads`; dual control inherited) | T-08; 49 contract | AC-14 |
| T-10 | BE | AT AMENDMENT: `SegmentEvaluator` (minutes cadence, TTL membership, opaque refs) + `RetargetService` (onsite dimension, email via 48, holdouts) | T-03; 48 contract | AC-15, AC-16, AC-18, AC-19 |
| T-11 | FE | AT AMENDMENT: creator surfaces (opt-in, category declaration, earnings, statements) + visitor unit + disclosure | T-04, T-08 | UI flows complete |
| T-12 | FE | AT AMENDMENT: ops console (flights, creatives, reports) + advertiser reports + retarget builder + lift | T-05, T-10 | UI flows complete |
| T-13 | DevOps | AT AMENDMENT: drill battery (catalog, safety, opt-in, separation under load, auth, decision, counting, dispute, outage, earnings, clawback, self-farming, shared-runner, segment, consent, export-refusal, fatigue, lift) | T-03..T-12 | AC-02..AC-19 green |
| T-14 | DevOps | AT AMENDMENT: release gates + metrics; absence-drill tooling flips to surface-drift checking | T-13 | NFR-14; digest pair boots (docker + podman) |

## Sequencing notes

1. T-01/T-02 are the live tasks: the gate is tool-enforced; no ad surface may leak into any current image.
2. T-03's separation probes are a promotion gate: zero cross-references between ads and analytics code paths.
3. T-05/T-06 before anything serves: decision parity + counting honesty fixtures gate the earnings work.
4. T-09 is a cross-service edit reviewed with the 49 owner; no second payout system may appear in the diff.
5. T-10's email path is 48's campaign path — eligibility arbitrates per recipient; no parallel send path exists.
6. Feature gate: E4 scope audit — zero ad surface in all pre-amendment images; the amendment record is the only way to open the gate.

## Definition of done (at amendment)

AC-01 stays green through the flip; AC-02..AC-19 green; separation + no-hardcoded-config + collapse-parity greps green in CI; full-tree validation green; `MIGRATION.md` feature 50 row notes the amendment when it lands.
