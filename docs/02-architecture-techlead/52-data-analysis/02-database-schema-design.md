# Data Analysis (Pipeline Standards, Experiments, Metrics) - Database Schema Design (feature 52) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/52-data-analysis/`. GATED: present state is **zero** — no standards table exists in any database, and none may be added before the amendment (BR-01, NFR-02). This file designs the future `analytics-service` migration `V5__analytics_standards.sql`; the amendment record starts it. Analytics chain currently V1–V4 (47).

## Present state: zero

| Probe | Assertion |
|---|---|
| Schema diff | No `analytics_events`, `experiments`, `experiment_assignments`, `metric_definitions`, or `kpi_daily` table in any database |
| Migration chain | `analytics_db` stops at V4 (47); no `V5__analytics_standards.sql` exists |
| Source grep | `KpiLibrary`, `AssignmentService`, `GuardrailWatcher`, `QualityMonitor` absent from all source and bundles |
| Routes | No registry, experiment, or KPI endpoint exists in the gateway |

## Future Flyway V5 (`V5__analytics_standards.sql`, at amendment)

```sql
CREATE TABLE analytics_events (
  name TEXT NOT NULL,                -- {namespace}.{object}.{verb}, snake_case
  version INT NOT NULL,
  owner TEXT NOT NULL,
  retention_class TEXT NOT NULL CHECK (retention_class IN ('behavioral','money_adjacent','advertising')),
  properties_schema JSONB NOT NULL,  -- flat, typed, allowlisted
  effective_from TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (name, version)
);

CREATE TABLE experiments (
  id TEXT PRIMARY KEY,
  hypothesis TEXT NOT NULL,
  variants_json JSONB NOT NULL,
  randomization_unit TEXT NOT NULL DEFAULT 'user_id' CHECK (randomization_unit = 'user_id'),
  primary_metric TEXT NOT NULL,
  mde DOUBLE PRECISION NOT NULL,
  guardrails_json JSONB NOT NULL,    -- conversion floor, refund/dispute ceilings, support-contact rate
  planned_duration_days INT NOT NULL,
  planned_sample BIGINT NOT NULL,
  ship_rule TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('draft','running','paused','decided')),
  decision_json JSONB,               -- ship/keep/iterate + evidence refs
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE experiment_assignments (
  experiment_id TEXT NOT NULL REFERENCES experiments(id),
  user_id TEXT NOT NULL,
  variant TEXT NOT NULL,
  assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (experiment_id, user_id)   -- sticky, written once
);

CREATE TABLE metric_definitions (
  name TEXT NOT NULL,
  version INT NOT NULL,
  formula TEXT NOT NULL,
  input_tables TEXT[] NOT NULL,
  grain TEXT NOT NULL,
  edge_rules JSONB NOT NULL,         -- BR-15 edge cases as structured rules
  effective_from TIMESTAMPTZ NOT NULL,
  retired_at TIMESTAMPTZ,            -- archive, never delete
  PRIMARY KEY (name, version)
);

CREATE TABLE kpi_daily (
  day DATE NOT NULL,
  metric TEXT NOT NULL,
  version INT NOT NULL,
  dimensions JSONB NOT NULL DEFAULT '{}',
  value NUMERIC NOT NULL,
  rebuilt_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (day, metric, version, dimensions)
);
CREATE INDEX idx_kpi_metric ON kpi_daily (metric, day DESC);
```

## RLS and grants (at amendment)

```sql
-- analytics_events / metric_definitions: ops writes (reviewed like code); service read
-- experiments: operator/analyst roles; assignments: SERVICE-ONLY (no client role may read; server-side reads only)
-- kpi_daily: finance/operator roles; service upsert (idempotent rebuilds)
GRANT SELECT, INSERT ON analytics_events, metric_definitions, experiments TO app_role;
GRANT SELECT, INSERT, UPDATE ON experiment_assignments TO service_role;   -- no app_role grant
GRANT SELECT, INSERT, UPDATE ON kpi_daily TO app_role;
```

## Semantics

- Taxonomy (BR-02): edge collectors validate against `analytics_events`; unregistered rejected (loud TEST, sampled + alerted prod); malformed registered payloads quarantined + owner-notified; quality counts feed the monitor.
- Transport (BR-03/D-2): validate → queue → dedupe (`event_id` KV pre-check + UNIQUE persist) → aggregates; raw NDJSON to R2 with per-class TTL (Q-01: behavioral 90 d, money-adjacent per 40, advertising per 50); `occurred_at` vs `received_at` lateness measured.
- Rebuilds (BR-06): every aggregate rebuilt by grain + window key, byte-identical (drill-verified); runbooks live with dashboards.
- Experiments (BR-08..BR-13): registry rows complete before launch; assignment = deterministic hash(`user_id`, experiment key) → sticky row, written once; billing stamps `experiment_id + variant` on orders (additive `extras`); guardrail breach → `status='paused'` + page; readout uses framework KPIs only; decision logged; shipped variants ride catalog versioning.
- Metrics (BR-14..BR-18): definitions append-only with `effective_from`; dual reporting computed from both versions during the overlap; `kpi_daily` values computed by the single library (snapshot carries `version`); closed snapshots immutable — adjustments net in the posting period.
- Access (BR-16/NFR-12): earner scopes cannot read `kpi_daily` (RLS); assignments have no client grant; quality dashboards carry no PII.

## Drills (at amendment)

| Drill | Asserts |
|---|---|
| Fresh apply | V5 creates five tables + indexes + RLS; assignments grant is service-only |
| Idempotency | Replay storms → zero drift; duplicate `event_id` persists once |
| Rebuild | Destroyed aggregate rebuilt byte-identical; dual-report check green |
| Assignment stickiness | Same hash → same variant across sessions/devices; no UPDATE path re-buckets |
| Dual report | Old vs new versions both computable during overlap; close names one version |
| Absence (live) | Until amendment: zero standards tables in every schema diff (AC-01) |
