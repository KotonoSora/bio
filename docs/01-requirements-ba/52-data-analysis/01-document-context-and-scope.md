# Data Analysis (Pipeline Standards, Experiments, Metrics) - Document Context and Scope (feature 52) - GATED

Phase 1 BA · Feature 52 of 53 — gated backlog 5/6. GATED: design-complete only — no table, route, job, or UI string ships until a spec amendment approves it (BR-01, AC-01). Sources: `data-analysis/analytics-pipeline.md`, `data-analysis/experimentation.md`, `data-analysis/metrics-framework.md`. Implemented (future) by `docs/02-architecture-techlead/52-data-analysis/`.

## Context

Three coupled scopes: the house rules every analytics event obeys (closed taxonomy, transport discipline, quality bars, retention, rebuilds — three existing pipelines already differ slightly and the fourth would reinvent dedupe); how the business learns what works (registered pricing/packaging/checkout experiments with pre-committed ship rules, guardrails, and promo holdouts); and the one true definition of each monetization KPI (registry, edge-case rules, single library, versioned change control) so MRR is not computed three ways. The future home is `analytics-service` (extension `V5__analytics_standards.sql`); sales facts arrive via a scoped internal billing read, and dashboards consume the library rather than re-implementing it. Nothing here is built while gated.

## Objectives

| ID | Objective |
|---|---|
| OBJ-01 | One closed, versioned event taxonomy; unregistered events rejected at the edge, malformed payloads quarantined |
| OBJ-02 | Proven transport discipline everywhere: validate → queue → dedupe → aggregates; exactly-once effects via idempotency keys |
| OBJ-03 | Quality bars per class (money exact, funnel bounded, ad evidence-bundled) with quality metrics alerted like money alerts |
| OBJ-04 | Uniform retention/minimization; deletion requests propagate to raw stores; aggregates stay anonymous |
| OBJ-05 | Every aggregate idempotently rebuildable; rebuilds are routine with runbooks |
| OBJ-06 | Experiments registered before launch with guardrails that auto-pause; no unregistered change touches pricing |
| OBJ-07 | One versioned definition per KPI, one library implementation, dual reporting on any change |

## Scope

In scope (at amendment, SC-01..SC-05): taxonomy registry + edge validation spec; quality monitoring; retention classes; rebuild runbooks; experiment registry + assignment + guardrails + holdouts; KPI registry + library + snapshots + dual reporting.

Out of scope: KPI dashboard presentation (51's sales analytics owns surfaces; this feature owns definitions); page analytics content (12/31); money grant logic (33–40); grant/entitlement/security experiments (never); experiment execution inside checkout beyond assignment reads; P5-class BI tooling.

## Personas and permissions

| Role | CRUD Permissions | Description |
|---|---|---|
| Implementer (new event source) | R registry; C event registrations | Has the checklist that makes data trustworthy on day one |
| Analyst/operator | R any aggregate with known semantics; C experiments/readouts | Reads dedupe/filter/freshness semantics with every number |
| Finance | R versioned definitions + KPI snapshots; C close-version naming | Closes books off versioned definitions with drill-down to ledger lines |
| Privacy reviewer | R retention + minimization claims against one standard | Verifies claims centrally, not per pipeline |
| Experiment designer | C experiments per registry discipline | Proposes tests; gets answers with dates, not debates |
| Buyer (indirect) | none | Never sees broken or unfair treatment; variants stable and disclosed where material |

## Open-question resolutions

| # | Question (source) | Resolution |
|---|---|---|
| Q-01 | Raw TTL values per class (pipeline Q1) | Differentiated now, aligned not invented: behavioral raw = the 90-day beacon precedent (one number); money-adjacent raw follows 40's settlement retention; advertising raw per 50's TTL precedent; aggregates kept per finance/audit needs |
| Q-02 | Shared edge middleware vs per-pipeline endpoints (pipeline Q2) | Per-pipeline endpoints today under one shared validation spec; middleware convergence at the next runtime upgrade — no flag-day rewrite |
| Q-03 | Fairness floor (experimentation Q1) | Any treatment showing different prices to different users for the same SKU requires explicit disclosure + legal sign-off; ops-internal copy/timing tests don't; disclosure copy approved per legal threshold at the amendment |
| Q-04 | Minimum experiment scale (experimentation Q2) | Config-owned (`analytics.experimentFloor`, power-rule based); tests below the floor are declared underpowered instead of run |
| Q-05 | CAC inputs (metrics Q1) | Finance-owned: the fully-loaded cost-line list is finance tooling's, referenced (never duplicated); monthly certification by finance |
| Q-06 | AI cost attribution (metrics Q2) | Metered where the platform exposes metering; modeled allocation (explicitly labeled "modeled") until metering exists — never presented as metered |

## Constraints carried

Assignment is server-side only (variant never trusted from the client); no variant alters closed-period figures or grandfathered terms; KPI aggregates never leak into earner-scoped dashboards (49/50 keep their own ledgers); no dashboard ships KPI SQL outside the shared library; metric history is never rewritten; quality metrics and money alerts share one ops channel.
