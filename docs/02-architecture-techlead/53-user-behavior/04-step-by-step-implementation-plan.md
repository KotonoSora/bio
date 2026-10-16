# User Behavior (Journeys, Conversion, Retention) - Step-by-Step Implementation Plan (feature 53) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/53-user-behavior/`. Contracts: `docs/02-architecture-techlead/53-user-behavior/03-api-contracts-and-payloads.md`. GATED: T-01/T-02 run now; every `AT AMENDMENT` task is blocked until the spec amendment (BR-01) and must not ship in any current batch.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | DevOps | LIVE: absence-drill tooling — CI step asserting zero behavior surface (schema diff, OpenAPI diff, source + bundle grep: journey tables, replay UI, friction backlog, risk-band code) | - | AC-01 green; violation blocks promotion by tooling |
| T-02 | DevOps | LIVE: release-checklist entry — user-behavior remains gated; an amendment record is required before any build task starts | T-01 | Gate cannot be lifted silently |
| T-03 | BE | AT AMENDMENT: `V6__behavior.sql` (six tables + RLS; zero earner/buyer grants) | amendment; 01 D-1 | Schema drills green |
| T-04 | BE | AT AMENDMENT: journey/marker events registered in 52's taxonomy; emitters wired across quote/checkout/pending/renewal surfaces | T-03; 52 registry | AC-02, AC-05 |
| T-05 | BE | AT AMENDMENT: `JourneyAssembler` (batch grouping, stitching rules, closure sweeps with inferred marking) | T-04 | AC-03, AC-05 |
| T-06 | BE | AT AMENDMENT: `JourneyAggregates` + k-floor enforcement (single readout layer, no export path) | T-05 | AC-04, AC-08 |
| T-07 | BE | AT AMENDMENT: `FrictionAnalyzer` (ranking, baselines, segment cuts, playbook links, correlation labels) | T-06 | AC-07, AC-08, AC-09, AC-10 |
| T-08 | BE | AT AMENDMENT: `ExitReasons` (36 cancel-flow capture seam; skip-rate measured) + save-offer experiment links | T-06; 36 seam | AC-14 |
| T-09 | BE | AT AMENDMENT: `RetentionScorer` (adoption flags, explainable bands, versioned score logic) | T-06; config checklists | AC-12, AC-13 |
| T-10 | BE | AT AMENDMENT: `WinbackResults` + 48-path send integration (consent arbitration, stacking rules) | T-09; 48 seam | AC-15 |
| T-11 | BE | AT AMENDMENT: risk-band internal read (36 only) + CI dependency gate (zero gating/pricing/fraud references) | T-09 | AC-13; NFR-07 |
| T-12 | BE | AT AMENDMENT: price-sensitivity aggregates (read-only inputs; no treatment path) + trial engagement bands | T-07 | AC-11, AC-16 |
| T-13 | FE | AT AMENDMENT: ops readouts (journeys, backlog, segments, retention section) + internal band view | T-06..T-12 | UI flows complete |
| T-14 | DevOps | AT AMENDMENT: drill battery (trail, stitching, privacy, closure, auth, ranking, segment, playbook, correlation, price-sensitivity, adoption, lapse, cancel, win-back, trial) | T-03..T-13 | AC-02..AC-16 green |
| T-15 | DevOps | AT AMENDMENT: release gates + metrics; absence-drill tooling flips to surface-drift checking | T-14 | NFR-14; digest pair boots (docker + podman) |

## Sequencing notes

1. T-01/T-02 are the live tasks: the gate is tool-enforced; no behavior surface may leak into any current image.
2. T-04 first: events must be registered taxonomy before emitters ship (52's front door); existing pipelines are untouched.
3. T-11's dependency gate ships with the scorer — a single unauthorized consumer blocks promotion.
4. T-08/T-10 are cross-feature seams reviewed with the 36/48 owners; no new mail infrastructure and no buyer-facing surface may appear in the diff.
5. Feature gate: E4 scope audit — zero behavior surface in all pre-amendment images; the amendment record is the only way to open the gate.

## Definition of done (at amendment)

AC-01 stays green through the flip; AC-02..AC-16 green; k-floor + no-fingerprinting + dependency-gate greps green in CI; full-tree validation green; `MIGRATION.md` feature 53 row notes the amendment when it lands.
