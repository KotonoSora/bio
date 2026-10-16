# Short-Link Analytics - Architecture and Tech Stack (feature 31)

Phase 2 TL · Feature 31 of 53; extension of `analytics-service` (port 8085, `analytics_db`, Redis db 6). Implements `docs/01-requirements-ba/31-short-link-analytics/`. Producer contract: `docs/02-architecture-techlead/29-short-links/` (`link.hit.v1`, post-response emission, resolve SLA — never redefined here). Separation counterpart: `docs/02-architecture-techlead/12-profile-analytics/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | DEVIATION CARRIED (pilot boundary map): hit aggregates + fraud live in `analytics-service`; `analytics_db` migration `V3__link_hits.sql`. | The producer stays a single redirector; the consumer reuses feature 12's stream/consumer/rollup substrate. |
| D-2 | Intake is the Redis stream `links.hits` (db 6) with consumer group `linkhits`; at-least-once delivery + natural-key upserts; the only payload consumed is `link.hit.v1` — no other write path into aggregates exists. | One intake; replay/repair semantics are the consumer's to own, not the producer's. |
| D-3 | The event carries no user identifiers: the consumer resolves owner + link state via link-service's internal read `GET /internal/v1/links/{code}/owner`, cached 300 s in Redis db 6 (resolver-cache pattern of feature 12 D-6). | RLS needs `user_id`; the stream itself stays identity-free. |
| D-4 | Raw evidence: NDJSON written at resolve (link-service, ~60 s batched flush) to bucket `link-hits`, path `hits/links/<yyyy>/<mm>/<dd>/*.ndjson`, 90-day lifecycle; dashboards never read it — ops dispute + repair only. | Evidence exists before the queue is involved; repair never depends on the thing that broke. |
| D-5 | Filters run in fixed order: bot/crawler exclusion → prefetch/prerender suppression (`purposeHint` where present) → double-open collapse → velocity quarantine (≥ 500 hits/min from one source). Quarantine is human-decided in v1; release replays held hits through the pipeline. | Deterministic pipeline; no automatic surprises for money-tied numbers. |
| D-6 | Exclusion counts are first-class: stored as `link_hit_segments` dimension `excluded` (keys `bot\|prefetch\|dedupe\|quarantine`) — the single addition beyond the legacy sketch, because `raw − excluded = reported` must render from data, not folklore. | Transparency is a readout, not a support ticket. |
| D-7 | Scan split: scans counted separately (`scans` counter + `scan_class` segment); heuristic inputs = absent referer + mobile UA class + camera-class UA; the contract marks heuristic-derived values `estimated: true`; event-pack per-location codes are exact. | One labelled mechanism; exactness only where it genuinely exists. |
| D-8 | Repair: the consumer writes a completeness marker `hits/links/.state/<day>.done` once a day's stream is drained and raw is verified; marker-vs-content mismatch triggers a day rebuild (delete the day's rows for affected codes + recompute from raw — convergent, no doubles); a `rawgap` metric alerts on missing evidence. | Queue outages repair with no loss and no double-counting. |
| D-9 | Retention + ops: aggregates pruned by feature 13's prune job (scope extended); raw 90 days; quarantine rows until decided; purge extends feature 02's `analytics-service` participant; ops routes (`ROLE_OPS` re-verified per request) cover replay + decide, audited. | Same lifecycle rails as the rest of the analytics major. |

## Component map

| Piece | Home | Note |
|---|---|---|
| Hit stream consumer | `analytics-service` | group `linkhits`; batch flush ≤ 60 s |
| Filters (fixed order) + exclusion counters | `analytics-service` | velocity 500/min/source → quarantine |
| Aggregates + segments | `analytics_db` V3 | natural-key upserts; day rebuild = delete+recompute |
| Owner resolution cache | Redis db 6 | `links:owner:{code}` TTL 300 s |
| Repair + completeness markers | `analytics-service` + MinIO | marker `hits/links/.state/<day>.done`; `rawgap` metric |
| Stats readouts + CSV | `analytics-service` | owner-scoped; `estimated` flags data-level |
| Ops replay + decide | `analytics-service` | `ROLE_OPS`; audited |

## Boundaries

Owns: intake, dedupe, filters, quarantine, aggregates/segments/exclusion counts, stats readouts + CSV, replay, repair, retention/purge for its tables. Does not own: resolve/minting/lifecycle (feature 29 — read-only via the internal read), QR rendering (feature 30), profile analytics tables (feature 12 — separation is structural: no cross-write path exists), affiliate/ad scoping (their majors, forward), prune mechanics (feature 13 — scope extended).

## Consumers

| Consumer | Contract |
|---|---|
| owner stats UI | `GET /api/v1/analytics/links/{code}/stats(.csv)` |
| ops dispute console | `/api/v1/ops/analytics/*` |
| affiliate/ad majors (forward) | same aggregates via their own scoped endpoints; posture matrix honored |
| feature 12 dashboards | dual-count explainer shares the matched-window contract (bio-block case) |
