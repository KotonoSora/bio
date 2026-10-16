# Short-Link Analytics - Step-by-Step Implementation Plan (feature 31)

Phase 2 TL · Dependency-ordered; acceptance goals cite `docs/01-requirements-ba/31-short-link-analytics/06-acceptance-criteria.md`.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `V3__link_hits.sql`: three tables, owner + ops RLS policies, grants, indexes | Feature 12 module | TL 02 drills green |
| T-02 | BE | Consumer skeleton: stream `links.hits`, group `linkhits`, owner resolve + 300 s cache, batch flush ≤ 60 s | T-01, feature 29 T-12 | AC-01 (intake half) |
| T-03 | BE | Filters in fixed order (bot → prefetch → dedupe → velocity) + exclusion counters + dedupe keys | T-02 | AC-01, AC-03 (filter half) |
| T-04 | BE | Aggregates/segment upserts on natural keys + scan-split classification with `estimated` flags | T-03 | AC-01, AC-07 |
| T-05 | BE | Quarantine branch + ops alert + release/drop replay path + audit | T-03 | AC-03, AC-08 |
| T-06 | BE | Completeness markers + day-rebuild repair + `rawgap` metric + scheduled sweep | T-04 | AC-06, AC-02 (repair half) |
| T-07 | BE | Readouts: stats + CSV + exclusion panel + quarantine banner + freshness fields (`generated_at`, lag) | T-04 | AC-05, AC-07, AC-09 |
| T-08 | BE | Ops routes: replay evidence view + decide; role re-verify + audit | T-05, T-07 | AC-05, AC-08, AC-10 |
| T-09 | FE | Stats dashboard + ops dispute view per BA 04 (estimated badges, equation panel, matched-window explainer) | T-07, T-08 | BA 04 states render |
| T-10 | DevOps/QA | Drills: hot-path bench (counting on/off ≤ 5 ms p95 delta), flood, separation under load, replay, repair rerun (idempotent), container drill (Docker + Podman; V2→V3 replay; rollback); log greps | T-02..T-09 | G4 + G5 + G7; AC-01..AC-10 |

## Notes

- The hot-path bench is the gate that protects feature 29's redirect SLA: the consumer never writes before a redirect completes; the producer-side emission delta is benchmarked with counting enabled vs disabled.
- Repair drills run twice and assert byte-identical aggregates for the rebuilt day (convergent rebuild, no doubles).
- The `estimated` flag is data-level (`estimated: true` in the API), never UI copy applied by hand.
- No new infrastructure: stream, cache, blob store, and scheduler already exist (feature 12's substrate); this feature adds one consumer group, one bucket prefix, and scheduled jobs inside the existing deployable.
- Producer-side cross-edits land with this batch per TL 03 (event `purposeHint` field, internal owner read on feature 29, raw-archive annotation).
- Affiliate/ad scoped readouts are forward seams (their majors): nothing is stubbed; the aggregates they will read already carry the exclusion transparency.
