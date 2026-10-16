# Data Analysis (Pipeline Standards, Experiments, Metrics) - Step-by-Step Implementation Plan (feature 52) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/52-data-analysis/`. Contracts: `docs/02-architecture-techlead/52-data-analysis/03-api-contracts-and-payloads.md`. GATED: T-01/T-02 run now; every `AT AMENDMENT` task is blocked until the spec amendment (BR-01) and must not ship in any current batch.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | DevOps | LIVE: absence-drill tooling — CI step asserting zero data-analysis surface (schema diff, OpenAPI diff, source + bundle grep: registry tables, experiment UI, KPI library, quality dashboards) | - | AC-01 green; violation blocks promotion by tooling |
| T-02 | DevOps | LIVE: release-checklist entry — data-analysis remains gated; an amendment record is required before any build task starts | T-01 | Gate cannot be lifted silently |
| T-03 | BE | AT AMENDMENT: `V5__analytics_standards.sql` (five tables + RLS; assignments service-only grant) | amendment; 01 D-1 | Schema drills green |
| T-04 | BE | AT AMENDMENT: `EventRegistry` + edge validation spec + quarantine flow + `QualityMonitor` | T-03 | AC-02; NFR-05 |
| T-05 | BE | AT AMENDMENT: dedupe consumer hardening (idempotency keys, replay-storm drill) across pipelines | T-04 | AC-03 |
| T-06 | BE | AT AMENDMENT: retention sweeps per class + storage audit evidence + deletion propagation | T-03 | AC-04 |
| T-07 | BE | AT AMENDMENT: rebuild tooling + runbooks (grain + window keys; byte-identical checks) | T-03 | AC-05, AC-06 |
| T-08 | BE | AT AMENDMENT: `ExperimentRegistry` + `AssignmentService` + checkout stamp seam (`orders.extras`) | T-03; 33 seam | AC-07, AC-08, AC-17 |
| T-09 | BE | AT AMENDMENT: `GuardrailWatcher` (funnel + refund/dispute scoped reads; auto-pause + paging) | T-08; 51 aggregates | AC-09 |
| T-10 | BE | AT AMENDMENT: `MetricRegistry` + `KpiLibrary` + `KpiSnapshots` + dual-report + scoped sales-facts read | T-03; 51 seam | AC-12, AC-13, AC-15 |
| T-11 | BE | AT AMENDMENT: holdout support for promo campaigns (sizing by power rule; true-lift readout) | T-08; 39/48 seams | AC-10 |
| T-12 | FE | AT AMENDMENT: quality dashboard, registries browser, experiment UI + readouts, KPI views + dual report | T-04, T-08, T-10 | UI flows complete |
| T-13 | DevOps | AT AMENDMENT: drill battery (taxonomy, exactly-once, retention, rebuild, adoption, registration, stability, guardrail, holdout, decision, fixture, dual-report, single-library, closed-period, auth, fairness) | T-03..T-12 | AC-02..AC-17 green |
| T-14 | DevOps | AT AMENDMENT: release gates + metrics; absence-drill tooling flips to surface-drift checking | T-13 | NFR-14; digest pair boots (docker + podman) |

## Sequencing notes

1. T-01/T-02 are the live tasks: the gate is tool-enforced; no standards surface may leak into any current image.
2. T-04 before any new pipeline emits: registration + quarantine is the front door; existing pipelines adopt at their next scheduled change (BR-07), never in this batch.
3. T-08/T-09 are cross-feature seams reviewed with the 33/51 owners; the stamp is additive and the watcher reads aggregates only.
4. T-10's library is the only KPI implementation — the single-library grep gate ships with it.
5. Feature gate: E4 scope audit — zero data-analysis surface in all pre-amendment images; the amendment record is the only way to open the gate.

## Definition of done (at amendment)

AC-01 stays green through the flip; AC-02..AC-17 green; single-library + append-only-registry + service-only-assignment greps green in CI; full-tree validation green; `MIGRATION.md` feature 52 row notes the amendment when it lands.
