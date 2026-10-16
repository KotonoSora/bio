# Sales (Ledger, Analytics, Order Console) - Step-by-Step Implementation Plan (feature 51) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/51-sales/`. Contracts: `docs/02-architecture-techlead/51-sales/03-api-contracts-and-payloads.md`. GATED: T-01/T-02 run now; every `AT AMENDMENT` task is blocked until the spec amendment (BR-01) and must not ship in any current batch.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | DevOps | LIVE: absence-drill tooling — CI step asserting zero sales surface (schema diff, OpenAPI diff, source + bundle grep: `sales_*`, console routes, funnel/scorecard UI) | - | AC-01 green; violation blocks promotion by tooling |
| T-02 | DevOps | LIVE: release-checklist entry — sales remains gated; an amendment record is required before any build task starts | T-01 | Gate cannot be lifted silently |
| T-03 | BE | AT AMENDMENT: `V9__sales_ledger.sql` (four tables + RLS + append-only grants) | amendment; 01 D-1 | Schema drills green |
| T-04 | BE | AT AMENDMENT: `SalesProjection` in every grant handler (same-transaction line, UNIQUE guard, replay no-op) | T-03; 34 seam | AC-02, AC-05 |
| T-05 | BE | AT AMENDMENT: `SalesNetting` (38 refund execution + 40 dispute losses append adjustments with references) | T-03; 38/40 seams | AC-03, AC-16 |
| T-06 | BE | AT AMENDMENT: `OrderConsole` (five-kind search, assembled trail ≤ 500 ms, plain-language mapping) | T-03 | AC-07; NFR-06 |
| T-07 | BE | AT AMENDMENT: `ActionGuard` + `DualControl` (role gates, preconditions, stale re-validation, dry-runs, maker-checker + expiry) | T-06 | AC-08, AC-09, AC-11 |
| T-08 | BE | AT AMENDMENT: `CaseService` (lifecycle + buyer mirror) | T-06 | AC-10 |
| T-09 | BE | AT AMENDMENT: `V10__sales_analytics.sql` + `SalesAnalytics` (idempotent hourly aggregates, decomposition links, close snapshots) | T-03 | AC-12, AC-13 |
| T-10 | BE | AT AMENDMENT: `Scorecards` + anomaly watch (config thresholds, versioned rules, paging) | T-09 | AC-14, AC-15 |
| T-11 | BE | AT AMENDMENT: scoped payout reads (49/50 internal rows for the trail) | T-06; 49/50 contracts | Trail completeness without cross-earner leakage |
| T-12 | FE | AT AMENDMENT: ops console + case view + approval UI + finance dashboards + scorecards | T-06..T-10 | UI flows complete |
| T-13 | DevOps | AT AMENDMENT: drill battery (grant, refund/partial/dispute, correction, replay, auth, lookup, action, dual-control, case, dry-run, reconciliation, funnel, health, anomaly, fee, bulk) | T-03..T-12 | AC-02..AC-17 green |
| T-14 | DevOps | AT AMENDMENT: release gates + metrics; absence-drill tooling flips to surface-drift checking | T-13 | NFR-14; digest pair boots (docker + podman) |

## Sequencing notes

1. T-01/T-02 are the live tasks: the gate is tool-enforced; no sales surface may leak into any current image.
2. T-04/T-05 are cross-feature seams reviewed with the 33/34/38/40 owners; the same-transaction rule is a promotion gate (a grant without its line blocks release).
3. T-07 before any action ships: stale-view re-validation and dry-run parity are safety rails, not polish.
4. T-09 consumes facts + existing events only — zero new buyer-facing instrumentation may appear in the diff.
5. Feature gate: E4 scope audit — zero sales surface in all pre-amendment images; the amendment record is the only way to open the gate.

## Definition of done (at amendment)

AC-01 stays green through the flip; AC-02..AC-17 green; append-only + same-transaction + dry-run-parity greps green in CI; full-tree validation green; `MIGRATION.md` feature 51 row notes the amendment when it lands.
