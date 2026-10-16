# Sales (Ledger, Analytics, Order Console) - Non-Functional Requirements (feature 51) - GATED

Phase 1 BA · GATED: NFR-01/NFR-02 are live now (gate enforcement); the rest bind the future build at the amendment. Thresholds, floors, and scorecard values are config-owned (`sales.*`) — never invented here.

## Gate enforcement (live now)

| ID | Requirement | Threshold |
|---|---|---|
| NFR-01 | Absence verification | Every release: schema diff + OpenAPI diff + source/bundle grep return zero sales surface; violation blocks promotion by tooling (AC-01) |
| NFR-02 | No-stub discipline | No sales table, route, job, chunk, or placeholder UI may exist in any other feature or release before the amendment |

## Future build (at amendment)

| ID | Requirement | Threshold |
|---|---|---|
| NFR-03 | Grant projection | Same-transaction sales line; zero grants without lines; replay emits zero lines; UNIQUE guard on `order_id` |
| NFR-04 | Ledger immutability | INSERT + SELECT only on facts; no UPDATE/DELETE path in code or migrations (grep + migration-review gate); originals byte-identical under all later adjustments |
| NFR-05 | Net-query performance | Net revenue for any standard slice ≤ 1 s p95 at expected volume; one query, no pre-aggregation required for correctness |
| NFR-06 | Trail latency | Assembled console trail p95 ≤ 500 ms; indexed lookups only; heavy work stays in analytics endpoints |
| NFR-07 | Action safety | Every action re-validates state at execution; stale views fail closed; dry-run preview matches execution |
| NFR-08 | Dual control | Above-floor actions require a different approver; approvals expire at 24 h; expired cannot execute; approvals are first-class audited records |
| NFR-09 | Dashboard freshness | Hourly-or-better aggregates (≤ 1 h lag); finance close snapshots byte-identical on re-run |
| NFR-10 | Alerting latency | Scorecard breaches + anomaly spikes page ops ≤ 5 min after aggregate refresh; slice attached; rules versioned |
| NFR-11 | Privacy | No buyer PII in analytics beyond existing ops visibility; affiliate/creator rollups never leak into business cuts; financial reads audited |
| NFR-12 | Rate limits | Console searches 600/h/agent; ops actions 120/h/operator; analytics reads 300/h/finance; exports 10/h; 429 with `Retry-After` |
| NFR-13 | Audit retention | Actions, approvals, cases, corrections, close snapshots: 24 months minimum with actor + reason |
| NFR-14 | Observability | Metrics: grant-to-line counts, net revenue by slice, funnel stages, provider scorecards, dispute rates, alert lead times, approval outcomes — no buyer labels |
