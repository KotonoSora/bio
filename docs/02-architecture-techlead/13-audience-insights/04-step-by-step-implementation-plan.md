# Audience Insights - Step-by-Step Implementation Plan (feature 13)

Phase 2 TL · BA sources: `docs/01-requirements-ba/13-audience-insights/` (ACs in `06-acceptance-criteria.md`). Contracts: `docs/02-architecture-techlead/13-audience-insights/03-api-contracts-and-payloads.md`. Execute in order; T-IDs are stable.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | Intake extension: seen-check (`seen:{profileId}:{visitorKey}`, TTL 30d, `RETURNING_SALT_MONTHLY`) + emit additive queue fields `utm`, `browser`, `returning` | 12 intake | Returning drill dataset green; one extra cache lookup only |
| T-02 | BE | Flyway V2 (`traffic_segments`, `link_perf`) + indexes + RLS + grants | 12 T-02 | Fresh-apply drill; `\d` contract |
| T-03 | BE | `SegmentRollup` projections (device/geo/referrer/utm/returning) inside `RollupConsumer` transaction; bot decision inherited | T-02, T-01 | Parity drill (AC-01); replay collapse |
| T-04 | BE | `link_perf` writes + canonical CTR (`ctr_x1000` per-mille, window sums at read) + rank/deltaRank + Movers composition | T-02 | Single-formula drill (AC-08); rank ties |
| T-05 | BE | `AudienceController` + `LinksPerformanceController`: clamp, `dimension_unknown`, `dataStart`, `aligned`, `prevPeriod`, nullable deltas | T-03, T-04 | Gating (AC-02) + compare drills (AC-07) |
| T-06 | BE | `AggregateRetentionJob` (02:30): dry-run `retention.would_delete`, bounded batched deletes across V1+V2 tables, alert on failure | T-02 | Retention drill (AC-06) |
| T-07 | BE | UTM hygiene (charset/length normalization caps) + `segments.cardinality{dimension}` gauge + top-25/`Other` trim helper | T-03 | Cardinality drill (AC-09); no-regression (AC-03) |
| T-08 | BE | Overview wiring: 12's `sources[]`/`countries[]` now read segment tables | T-03 | 12's contract populated; same-numbers (AC-13) |
| T-09 | FE | Audience tab: dimension tabs, bar rows, returning donut + definition copy, referrer icons, UTM drill with 25-row pagination + virtualization | T-05 | Tab parity (AC-01/AC-09) |
| T-10 | FE | Perf table + Movers sparklines + `not enough data` state | T-04 | Honesty drill (AC-10) |
| T-11 | FE | Overlay toggles + `aligned` badge + disabled-with-reason states (prev span, year-ago) + clamp-back shared with overview | T-05 | Compare (AC-07) + gating UX |
| T-12 | DevOps | Drill battery: parity, gating, no-regression, privacy, compare, container/podman parity, rollback | T-05..T-11 | AC-01..AC-13 green |
| T-13 | DevOps | Observability: `rollup.segments_written`, `retention.deleted`, `returning.keys`, cardinality alert, retention overrun alert | T-06, T-07 | Ops dashboards green |

## Notes

- No new container, dependency, or scheduler flag: all BE work lands in the existing `analytics-service` image; job tasks ride the `ROLLUP_ENABLED=true` replica (12 D-7).
- Feature 11's mobile arrangement tasks (its T-07..T-10) consume `dimension=device` once T-05 lands; its beacon contract was already fixed.
- Feature 15 consumes `ctr_x1000` + window sums from T-04; no second formula may be introduced there.
- Year-ago overlay stays disabled (BR-06); anything re-enabling it requires a retention-policy change in `00-platform-baseline` first.
