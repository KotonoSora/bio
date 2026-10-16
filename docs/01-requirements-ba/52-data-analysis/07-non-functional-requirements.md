# Data Analysis (Pipeline Standards, Experiments, Metrics) - Non-Functional Requirements (feature 52) - GATED

Phase 1 BA · GATED: NFR-01/NFR-02 are live now (gate enforcement); the rest bind the future build at the amendment. Alert thresholds, traffic floors, and TTLs are config-owned (`analytics.*`) with the aligned precedents from Q-01 — never invented here.

## Gate enforcement (live now)

| ID | Requirement | Threshold |
|---|---|---|
| NFR-01 | Absence verification | Every release: schema diff + OpenAPI diff + source/bundle grep return zero data-analysis surface; violation blocks promotion by tooling (AC-01) |
| NFR-02 | No-stub discipline | No registry table, experiment route, KPI library, or placeholder UI may exist in any other feature or release before the amendment |

## Future build (at amendment)

| ID | Requirement | Threshold |
|---|---|---|
| NFR-03 | Ingest latency | Edge validate + enqueue p95 ≤ 50 ms; consumer lag on the quality dashboard; backpressure never drops silently |
| NFR-04 | Exactly-once effects | Replay storms produce zero aggregate drift; idempotency keys on every consumer; at-least-once assumed end to end |
| NFR-05 | Quality alerting | Dupe/late/loss rate and queue-lag breaches alert on the same ops channel as money alerts ≤ 5 min after detection; thresholds config-owned |
| NFR-06 | Rebuild determinism | Rebuild key = grain + window; re-runs byte-identical; dual-report check enforced during definition migrations |
| NFR-07 | Retention | Per-class TTLs per Q-01 (aligned precedents); storage audit evidence retained; deletion propagation to raw within the documented window |
| NFR-08 | Assignment latency | Server-side assignment p95 ≤ 10 ms (hash + registry lookup, cached); never client-trusted |
| NFR-09 | Guardrail latency | Breach detection → auto-pause + page ≤ 5 min after aggregate refresh; offending slice attached |
| NFR-10 | KPI performance | Standard KPI read (any v1 metric, standard grain) ≤ 1 s p95 from snapshots; decomposition links to ledger lines resolve ≤ 500 ms |
| NFR-11 | Change control | Dual reporting covers ≥ one full overlap period before the old version retires; finance close names the version; history never rewritten |
| NFR-12 | Privacy | No raw IPs/UAs in PG aggregates (hashes only); KPI aggregates never leak to earner scopes; RLS operator/finance only; zero PII in quality dashboards |
| NFR-13 | Rate limits | Registry/definition writes 60/h/operator; experiment launches 30/h; readout reads 300/h; 429 with `Retry-After` |
| NFR-14 | Observability | Metrics: per-pipeline dupe/late/loss, queue lag, rebuild outcomes, experiment states, guardrail events, KPI version usage — no buyer labels |
