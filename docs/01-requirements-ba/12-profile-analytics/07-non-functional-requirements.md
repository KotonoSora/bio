# Profile Analytics - Non-Functional Requirements (feature 12)

Phase 1 BA · Every threshold is quantified; owners in the TL files. This pipeline is the platform's highest-traffic write path — the numbers below are its contract.

| Category | Requirement | Threshold |
|---|---|---|
| Intake latency | Beacon response time under soak | `202` p95 <= 200 ms at 500 rps sustained; zero per-hit PG writes (query-log asserted) |
| Rollup | Hourly cadence and lag | Consumer runs :05 hourly; lag < 1 h at gate; alert at > 90 min; replay increments exactly once |
| Read latency | Overview / links | p95 <= 400 ms at 90d range with <= 250 blocks, warm cache |
| Series | Downsampling contract | `?points=` cap 120; totals always exact regardless of points |
| Consistency | Aggregates vs raw archive | Match within dedupe tolerance (difference fully explained by dedupe + bot rejects) |
| Retention | Aggregates and raw | Free 90d daily; pro 365d daily; raw NDJSON 90d TTL; prune job owned by feature 13 |
| Privacy | Visitor data ceiling | Country grain only; no raw IP at rest; visitor key HMAC with daily-rotated salt; no cross-profile stitching; raw logs never joined into dashboards |
| Rate limits | Beacon abuse controls | Per-IP bucket 120 req/min (burst 240); tighten 50% when 429 ratio > 5% over 5 min; recover on 15-min decay; reads use standard gateway limits |
| Availability | Degrade contract | Analytics outage never blocks the public page (fire-and-forget; even queue-down returns `202` + alert); reads degrade to stale banner, never to fabricated data |
| Security | Access + oracle | Intake leaks nothing (uniform `202` on accepted paths); owner scoping by server-set `user_id` + RLS; raw bucket private, ops-only |
| Quotas | Event hygiene | <= 1 queued message per accepted deduped event; <= 1 raw object per drain hour per shard |
| Localization | Copy and buckets | Dashboard copy VN default EN fallback; day buckets rendered in the viewer's timezone and labelled; server `day` is the contract |
| Observability | Metrics + alerts | `consumer_lag_seconds`, `rollup.rows_written`, `beacon.rejected{reason}`, `beacon.abuse`, `analytics.query.latency`; alerts: lag > 90 min, abuse burst, rejection-rate spike |
