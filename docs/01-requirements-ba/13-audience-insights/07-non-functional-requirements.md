# Audience Insights - Non-Functional Requirements (feature 13)

Phase 1 BA · Read-only extension: numbers below add the audience-depth contract without re-opening the intake budget.

| Category | Requirement | Threshold |
|---|---|---|
| Read latency | Audience endpoint | p95 <= 400 ms at 90d window, warm cache, after server-side top-25 trimming |
| Read latency | Performance endpoint | p95 <= 400 ms at 90d window with <= 250 blocks |
| Rollup cost | Segment projection step | Added wall-time per hourly rollup <= +20% vs feature 12 baseline; batch window absorbs spikes |
| Cardinality | UTM keyspace | Per profile/day: alert when distinct UTM keys > 500 (`segments.cardinality{dimension}`); display bounded to top-25 + `Other (n)` |
| Cardinality | Returning memory | Cache keys bounded per profile (30d TTL); gauge `returning.keys` watched against Redis memory budget |
| CTR floor | Low-traffic honesty | < 30 views in-window renders `not enough data`; no statistical claim is made below it |
| Compare | Overlay availability | Overlays render only when the needed span is retained (`dataStart`); year-ago ships disabled under 90d/365d retention (BR-06) |
| Retention | Nightly job | Dry-run `retention.would_delete` then delete in bounded batches; full run <= 5 min at 50k profiles; failure alerts and retries next night |
| Retention | Downgrade semantics | Re-clamp applies on the next request; aged rows never resurrect |
| Privacy | Data ceiling | No IP/visitor-id columns; country-grain geo; monthly returning salt + daily dedupe salt in the secret store; compile-time whitelist DTO |
| No-regression | Intake budget | Beacon payload size and queue throughput unchanged vs feature 12 (measured <= 1% delta); zero per-hit PG writes holds |
| Availability | Failure domain | No new failure domain: read failure degrades to inherited stale/labelled states; intake untouched by this slice |
| Observability | Metrics + alerts | `rollup.segments_written`, `retention.deleted`, `segments.cardinality{dimension}`, `returning.keys`; alerts: UTM cardinality spike, retention run overrun, segment-write failure |
| Localization | Copy and buckets | VN default EN fallback; definition copy for returning (30d) and alignment badges; day buckets in viewer timezone, labeled |
