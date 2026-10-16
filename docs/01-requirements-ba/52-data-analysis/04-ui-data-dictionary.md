# Data Analysis (Pipeline Standards, Experiments, Metrics) - UI Data Dictionary (feature 52) - GATED

Phase 1 BA · GATED: no surface below may be built before the amendment. Surfaces: pipeline quality dashboard, event registry browser, experiment registry + readout, metric registry + KPI views, dual-report view. No buyer-facing surface exists.

## Pipeline quality dashboard

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `pipelineHealthTable` | Table | On load | Per pipeline: dupe/late/loss rates, queue lag, last rebuild | Shares the money-alert ops channel |
| `qualityTrends` | Charts | On load | Rates vs config-owned alert thresholds | Thresholds never hardcoded |
| `quarantineQueue` | Table | On events | Malformed registered payloads with owner | Owner notified automatically |
| `rebuildHistory` | Timeline | On load | Rebuild runs, keys (grain + window), outcomes | Byte-identical check shown |
| `storageAuditLink` | Link | On load | Raw-class TTL deletion evidence | Privacy reviewer reads this |

## Event + metric registries

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `eventRegistry` | Table + form | Yes to add | `{name, version, owner, retention_class, properties_schema}` | Registered before emission; unregistered rejected at edge |
| `metricRegistry` | Table + version form | Yes to change | `{name, formula, inputs, grain, edge rules, version, effective_from}` | Append-only history; unused metrics archived, never deleted |
| `dualReportView` | Side-by-side table | During overlap | Old vs new definition for one overlap period | Finance close names its version |
| `fixtureTestPanel` | CI report | On build | Hand-computed fixtures incl. trials/coupons/partials/disputes/grace | Library must match exactly |

## Experiment registry + readout

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `experimentForm` | Form | Yes to launch | Hypothesis, variants, unit = user_id, primary metric + MDE, guardrails, duration/sample, ship rule | Nothing launches without the full record |
| `assignmentDebug` | Read-only view | On demand | Sticky variant + hash inputs (server-side) | Variant never trusted from client |
| `guardrailPanel` | Stat row | On load | Conversion floor, refund/dispute ceilings, support-contact rate vs thresholds | Breach auto-pauses + pages |
| `readoutView` | Report | At window close | Framework KPIs only; decision + evidence attached | No post-hoc metric shopping |
| `holdoutPanel` | Stat | On load | Holdout size (power rule), true lift vs redemptions | Holdout UX never degraded |

## Validation and behavior notes

- Underpowered tests render as `underpowered — not run` (config floor), never as silent launches.
- Fairness-floor disclosure state is a launch precondition for any differing-price treatment; legal sign-off recorded.
- Empty states: no experiments → registry guidance; no dual report → current version only, with effective date.
- Every KPI tile links to its definition version and decomposition path — no orphan numbers.
