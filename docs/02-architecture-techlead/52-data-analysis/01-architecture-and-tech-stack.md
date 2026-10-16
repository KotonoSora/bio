# Data Analysis (Pipeline Standards, Experiments, Metrics) - Architecture and Tech Stack (feature 52) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/52-data-analysis/` (7 files, gated). GATED: design-complete only — no component below exists in any image, bundle, or migration until a spec amendment approves it (BR-01, AC-01). Stack pins per `docs/02-architecture-techlead/00-platform-baseline/`. Seams consumed: billing's sales facts (51) via scoped internal read, checkout stamping (33), funnel + refund/dispute aggregates for guardrails.

## Boundary (at amendment): `analytics-service` extension

When built: `analytics-service` (existing, Redis db 6) gains `V5__analytics_standards.sql` — the taxonomy registry, experiment registry + assignments, metric definitions, and KPI snapshots. Dashboards and alerts consume the single metrics library; no second KPI implementation may exist.

| Component (at amendment) | Change | Notes |
|---|---|---|
| `EventRegistry` (V5) | Versioned `analytics_events` + edge validation spec | Registered before emission; unregistered rejected at edge (loud TEST, sampled prod) |
| `EdgeCollectors` | Per-pipeline endpoints under one shared validation spec | Middleware convergence at next runtime upgrade (Q-02); queue-only |
| `QualityMonitor` | Dupe/late/loss + queue lag + rebuild history | Shares the money-alert ops channel |
| `ExperimentRegistry` (V5) | `experiments` + pre-registration discipline | Unregistered changes never touch pricing surfaces |
| `AssignmentService` (V5) | Internal assign endpoint: deterministic hash, sticky `experiment_assignments` | Server-side only; billing stamps variant on orders |
| `GuardrailWatcher` | Watches funnel + refund/dispute aggregates; auto-pause + page | Kill-switch discipline as provider incidents |
| `MetricRegistry` (V5) | Versioned `metric_definitions` + dual-report mechanism | Append-only history; close names the version |
| `KpiLibrary` | One function per KPI; unit-tested against hand-computed fixtures | Copy-pasted dashboard SQL is a review failure |
| `KpiSnapshots` (V5) | `kpi_daily` rebuilt idempotently from facts + aggregates | Sales facts via scoped internal billing read |

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | Extend `analytics-service` (V5); registries + KPI snapshots in `analytics_db` | Standards + experiments + metrics are analytics' own data; no new service |
| D-2 | Queue-only transport; `event_id` idempotency keys end to end; at-least-once with exactly-once effects | BR-03; proven beacon pattern standardized |
| D-3 | Differentiated raw TTLs aligned to existing precedents (behavioral 90 d, money-adjacent per 40, advertising per 50) | Q-01; no invented retention values |
| D-4 | Assignment server-side via internal endpoint; billing stamps `experiment_id + variant` in `orders.extras` (additive) | BR-10; variant never client-trusted; pinned at order creation |
| D-5 | Guardrails watch scoped aggregate reads (funnel, refund/dispute) and auto-pause + page | BR-11; no variant touches closed books or grandfathered terms |
| D-6 | Single `KpiLibrary` implementation; snapshots rebuilt idempotently; decomposition links to ledger lines | BR-18; one definition per KPI, versioned |
| D-7 | Metric redefinitions publish new versions with one overlap period of dual reporting | BR-17; history never rewritten |
| D-8 | Existing pipelines adopt taxonomy incrementally as conformance notes at their next scheduled change | BR-07; no flag-day rewrite |

## Cross-edits (all at amendment, not now)

| Target | Edit (at amendment) |
|---|---|
| `docs/02-architecture-techlead/00-platform-baseline/01-architecture-and-service-topology.md` | analytics-service row note: standards + experiments + KPI extensions (V5) |
| `docs/02-architecture-techlead/32-monetize-overview/03-api-contracts-and-payloads.md` | registry rows: `POST /internal/v1/analytics/assign`, `GET /internal/v1/billing/sales-facts` (billing registers) |
| `docs/02-architecture-techlead/33-checkout-session/` | checkout assignment read + `orders.extras` stamp seam |
| `docs/02-architecture-techlead/12-profile-analytics/` | beacon conformance note (taxonomy alias, no behavior change) |
| `docs/02-architecture-techlead/51-sales/` | close snapshots name the KPI definition version; dual-report link |
| `MIGRATION.md` | feature 52 row (doc migration landed with this batch; build at amendment) |
