# Data Analysis (Pipeline Standards, Experiments, Metrics) - Contracts and Payloads (feature 52) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/52-data-analysis/`. GATED: none of these routes may appear in OpenAPI, the gateway, or any image until the spec amendment (AC-01). Reuse contracts: `docs/02-architecture-techlead/32-monetize-overview/03-api-contracts-and-payloads.md` (registry + events), `docs/02-architecture-techlead/51-sales/` (sales facts + close snapshots), `docs/02-architecture-techlead/33-checkout-session/` (order stamping).

## Endpoints (reserved, absent until the amendment)

| Endpoint | Auth | Notes |
|---|---|---|
| `POST /internal/v1/analytics/assign` | service JWT | Deterministic sticky assignment: `{experimentKey, userId}` → `{variant}`; p95 ≤ 10 ms; variant never client-trusted |
| `GET /internal/v1/billing/sales-facts` | service JWT | **Billing registers at amendment** (51 seam): scoped sales lines + adjustments read for KPI rebuilds and decomposition links |
| `GET /api/v1/ops/analytics/events`, `POST` | operator role | Registry browser + registration (`{name, version, owner, retention_class, properties_schema}`) |
| `GET /api/v1/ops/analytics/quality` | operator role | Per-pipeline dupe/late/loss, queue lag, quarantine queue, rebuild history |
| `POST /api/v1/ops/analytics/experiments`, `PATCH /{id}`, `POST /{id}/pause` | operator/analyst role | Full pre-registration record required to launch; pause is instant |
| `GET /api/v1/ops/analytics/experiments/{id}/readout` | operator/analyst role | Framework KPIs only; decision + evidence; holdout lift for promos |
| `GET /api/v1/ops/analytics/kpis?metric=&grain=&version=` | finance/operator role | Snapshot reads; decomposition links resolve to ledger lines |
| `GET /api/v1/ops/analytics/kpis/dual-report` | finance/operator role | Old vs new definition during the overlap period |
| `GET/POST /api/v1/ops/analytics/metric-definitions` | finance/operator role | Append-only versions; archive instead of delete |

```kotlin
data class AssignRequest(val experimentKey: String, val userId: String)
data class AssignView(val variant: String, val experimentId: String)
data class ExperimentRecord(val hypothesis: String, val variants: List<String>, val primaryMetric: String,
  val mde: Double, val guardrails: JsonNode, val plannedDurationDays: Int, val plannedSample: Long, val shipRule: String)
data class QualitySnapshot(val pipeline: String, val dupeRate: Double, val lateRate: Double, val lossRate: Double,
  val queueLagSeconds: Long, val lastRebuildAt: String?)
data class KpiValue(val metric: String, val version: Int, val day: String, val dimensions: Map<String, String>, val value: Double)
data class MetricDefinitionView(val name: String, val version: Int, val formula: String, val edgeRules: JsonNode, val effectiveFrom: String)
```

## Rules

- Assignment is server-side and sticky by construction; checkout reads it and stamps `orders.extras` (additive) — the client never sends or reads a variant.
- Registries are append-only/versioned; unregistered events are rejected at the edge; malformed registered payloads quarantine with owner notification.
- Experiments cannot launch without the full record; guardrail breach auto-pauses + pages; no variant alters closed-period figures or grandfathered terms.
- KPI reads are snapshot-based and carry `version`; dual-report reads require an overlap window; decomposition links are server-resolved.
- No KPI SQL outside the library; no earner-scope access to business KPIs; assignments have no client-facing route.
- Rate limits + audit per NFR-13 and the security file; RFC 9457 errors below.

## Error catalog (reserved)

| Code | Status | UI Behavior |
|---|---|---|
| `event_unregistered` | 422 | "Register the event first." |
| `payload_malformed` | - | Quarantined; owner notice with schema diff |
| `experiment_incomplete` | 422 | Registry form shows missing fields |
| `experiment_underpowered` | 409 | "Traffic below the floor — test not launched." |
| `guardrail_breached` | - | Auto-pause + page with slice |
| `holdout_required` | 422 | "Attach a holdout before launch." |
| `fairness_disclosure_required` | 422 | Legal sign-off + disclosure copy required |
| `dual_report_missing` | 409 | "Publish the dual report first." |
| `snapshot_immutable` | 409 | Closed snapshots cannot change; posting-period note added |
| `metric_version_unknown` | 404 | "Unknown metric version." |
| `role_required` | 403 | "Not available." |
