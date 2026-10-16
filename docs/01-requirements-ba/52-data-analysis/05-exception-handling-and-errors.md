# Data Analysis (Pipeline Standards, Experiments, Metrics) - Exception Handling and Errors (feature 52) - GATED

Phase 1 BA · GATED: at amendment; problems follow RFC 9457 (`code` field). No buyer PII, experiment internals, or cross-role data leaks beyond the caller's scope.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Unregistered event at the edge | Ingest | Rejected — loud in TEST; sampled + alerted in production | Implementer: "Register the event first." |
| Malformed registered payload | Ingest | Quarantined; owner notified; counted on the quality dashboard | Owner notice with schema diff |
| Event_id replay storm | Consumer | Idempotency keys hold; zero aggregate drift | None |
| Direct DB write attempted from a pipeline | Review gate | Blocked by design (queue-only); review failure | Internal: "Pipelines never write PG directly." |
| TTL sweep miss | Storage audit | Flagged on the quality dashboard; deletion re-run | Privacy reviewer alert |
| Deletion request arrives | Pipeline | Raw buyer data cleared within the documented window; aggregates (anonymous) stand | None (buyer sees normal deletion flow) |
| Rebuild mismatch | Ops | Blocked from promotion; re-run required; runbook cited | Ops: "Rebuild is not byte-identical — investigate." |
| Unregistered pricing change | Review gate | Blocked from experiment surfaces | Designer: "Register the experiment before touching pricing." |
| Underpowered test proposed | Registry | Declared underpowered instead of run (config floor) | "Traffic below the floor — test not launched." |
| Guardrail breach | System | Auto-pause + page with the offending slice; closed books + grandfathered terms untouched | Ops channel |
| Mid-experiment re-bucketing attempt | System | Forbidden; assignment sticky by construction | Internal only |
| Early stop by peeking | Review gate | Refused — only the pre-registered rule stops a test | "Early stopping requires the registered rule." |
| Promo campaign without holdout | Review gate | Blocked for major campaigns (power-rule holdout required) | Ops: "Attach a holdout before launch." |
| Holdout degraded UX detected | Review | Release blocked; holdout must be invisible | Internal finding |
| Causal claim without experiment | Review checklist | Causal language blocked; correlation labeled | Analyst: "Label as correlation or link the experiment." |
| Dual-report missing on redefinition | Review gate | Change blocked until overlap view exists | "Publish the dual report first." |
| Copy-pasted KPI SQL in a dashboard | Review gate | Review failure (single-library rule) | "Use the metrics library." |
| Non-ops role on KPI endpoints | API | 403; RLS denies | "Not available." |
| Earner probes business KPIs | API | 403 — scoped dashboards only | "Not found." |
| AI cost shown as metered when modeled | Review gate | Label enforced ("modeled") before publish | Internal finding |
| Closed-period restatement attempted | System | Refused; adjustments net in the posting period with a note | Finance: "Closed snapshots are immutable." |
