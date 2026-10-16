# Earnings Dashboard - Step-by-Step Implementation Plan (feature 15)

Phase 2 TL · BA sources: `docs/01-requirements-ba/15-earnings-dashboard/` (ACs in `06-acceptance-criteria.md`). Contracts: `docs/02-architecture-techlead/15-earnings-dashboard/03-api-contracts-and-payloads.md`. Execute in order; T-IDs are stable.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `CapabilityReader` + forward interfaces (`RevenueRollupPort`): four closed flags; union reconciliation with 14's `has_orders`; absent/unknown -> omitted | 13 aggregates | Gating fixtures (AC-01); no inferred panels |
| T-02 | BE | `UnifiedAggregator` engagement side: owner check, inherited clamps, rows from 12-13 (views/clicks/`ctr_x1000` consumed, null < 30 views), 25-row server pagination | T-01 | Single-formula (AC-10); same-numbers (AC-13) |
| T-03 | BE | Money side: flagged fan-out via `RevenueRollupPort`, 2 s timeout per source, `unavailableCells`, units/revenue/gross-net verbatim, `rpc` formula, totals rule (`moneyComplete`) | T-01, T-02 | Attribution (AC-03); honesty (AC-04); totals rule |
| T-04 | BE | `UnifiedController` JSON: `private, max-age=60`, `foreign_profile` + `range_clamped` + ProblemDetail shapes | T-02, T-03 | IDOR (AC-05); consistency (AC-02) |
| T-05 | BE | `CsvStreamer`: chunked pull-based export, per-row source columns, cancel aborts, stricter throttle bucket | T-04 | CSV consistency (AC-02); abuse (AC-06) |
| T-06 | BE | Metrics + alerts: `unified.fanout_ms{source}`, `unified.source_unavailable{source}`, `unified.csv_bytes`; alert > 1% unavailable over 15 min | T-03, T-05 | Ops drill (AC-06/noc) |
| T-07 | FE | `EarningsScreen`: route, shared range picker + `FetchState`, focus/manual refresh only (zero idle requests) | T-04 | Speed (AC-07) |
| T-08 | FE | Funnel table: engagement columns, money-cell states (`not_yet_available` em-dash vs `unavailableCells` retry), per-group tooltips, totals row | T-04 | Gating + honesty (AC-01, AC-04) |
| T-09 | FE | Revenue series + units table (conditional), trace links carrying the range param, 25-row pagination | T-03, T-08 | Traceability (AC-08); speed (AC-07) |
| T-10 | FE | `ExportCsvButton`: streamed download, progress, cancel | T-05 | Abuse/UX (AC-06) |
| T-11 | FE | `LiveTicksEmbed` (pro only) reusing 14's component and poll discipline; grain-separation asserts | T-08 | Grain separation (AC-12) |
| T-12 | DevOps | Drill battery: gating, consistency, attribution, honesty, IDOR, abuse, speed, traceability, parity, single-formula, violation, grain separation, same-numbers; same image via `docker run` + `podman run`; grep gates (no net math, no money table, no grain mixing) | T-04..T-11 | AC-01..AC-13 green |

## Notes

- All BE work lands in the existing `analytics-service` image (12-14); no new container, dependency, or migration. Compose/Gradle unchanged.
- Feature 14's sales seam (`has_orders`) now resolves to the union of T-01's flags; 14's `SalesTickReader` consumes the same `CapabilityReader`.
- Shop 22-26 / monetize 32-40 later supply flag storage + rollup reads; until then every money key is absent by construction (BO-3) and no shop scope is stubbed here.
- Feature 13's AC-08 single-formula gate extends to this feature: CTR is consumed, never recomputed; T-12 greps for a second formula.
- Release gates (digest pair, rollback, contract diff) run per feature; platform pins land with `docs/02-architecture-techlead/00-platform-baseline/`.
