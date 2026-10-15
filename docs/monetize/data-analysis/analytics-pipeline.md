# Analytics Pipeline (taxonomy, quality, and retention standards)

PLAN ref: none — new scope beyond PLAN P0–P5, flagged (see `data-analysis/metrics-framework.md` for placement). Dashboards consume these standards; existing pipelines (profile beacons, ad measurement, money webhooks) conform over time, never in a flag-day rewrite.

## Purpose

Set the house rules for analytics data across the monetize domain (adoptable by page analytics later): how events are named, what makes them trustworthy, how long raw data lives, and how aggregates get rebuilt. Three pipelines already exist with three slightly different conventions (profile beacons, ad measurement, money webhooks); without a standard, the fourth reinvents dedupe, the fifth leaks PII into aggregates, and backfills diverge. This doc standardizes the proven pattern once.

## Users

- Implementer (any new event source): *As an Implementer, I want to have the checklist that makes its data trustworthy on day one so that the house rules for analytics data across the monetize domain as defined here.* the checklist that makes its data trustworthy on day one.
- Analyst/operator: *As an Analyst/operator, I want to read any aggregate knowing its dedupe, filter, and freshness semantics so that the house rules for analytics data across the monetize domain as defined here.* reads any aggregate knowing its dedupe, filter, and freshness semantics.
- Privacy reviewer: *As a Privacy reviewer, I want to verify retention and minimization claims against one standard so that the house rules for analytics data across the monetize domain as defined here.* verifies retention and minimization claims against one standard.

## Functional requirements

1. Event taxonomy (closed vocabulary, versioned): `{namespace}.{object}.{verb}` in `snake_case` (e.g. `checkout.session.started`, `billing.grant.confirmed`, `ad.slot.served`), each event carrying `{event_id UNIQUE, occurred_at, actor_key?, properties (flat, typed, allowlisted per event)}`. New events registered with owner + retention class before emission; unregistered events rejected at the edge collector (fail loud in TEST, sample-and-alert in production).
2. Transport discipline (the proven pattern, now mandatory): edge endpoint validates → queue (never direct DB writes) → deduped consumer (idempotency key = `event_id`; KV pre-check + UNIQUE persist) → aggregates; raw payloads to R2 NDJSON with per-class TTL. At-least-once delivery assumed end to end; exactly-once effects via idempotency keys, never via delivery promises.
3. Quality bars per event class: money events (grants, refunds) require exactness (ledger reconciliation to zero unexplained delta); funnel events require completeness bounds (documented allowable loss, monitored); advertising billed events require evidence bundles (per the ad-measurement doc). Quality metrics (dupe rate, late rate, loss rate) are themselves emitted and alerted on.
4. Retention and privacy (uniform): raw NDJSON TTLs by class (money-adjacent raw: shortest viable; behavioral: bounded, then aggregates-only), aggregates kept per finance/audit needs, NO raw IPs/user-agents in PG aggregates (hashes where join keys are needed), buyer PII never in analytics tables beyond owner-scoped keys. Deletion requests propagate to raw stores within the documented window; aggregates (already anonymous) stand.
5. Rebuild discipline: every aggregate rebuildable idempotently from raw + ledger sources (rebuild key = grain + window); rebuilds are routine (backfill after outages, definition migrations with dual-reporting per the metrics doc), never exceptional. A rebuild runbook per pipeline lives with its dashboards.
6. Adoption path: existing pipelines (profile beacons, ad measurement, money webhooks/sweeps) adopt the taxonomy and standards incrementally at their next scheduled change — documented as conformance notes in their own docs, not as a rewrite project. New pipelines conform from birth (review gate).

## Data / API touches

- Registry: `analytics_events{name, version, owner, retention_class, properties_schema}` as versioned config; per-pipeline aggregate tables keep their shapes (unified only where the metrics doc demands shared KPIs). Queues per domain (existing `BEACON_Q` pattern; new queues named, never multiplexed silently). All PG via `worker/db/*`.
- Edge collectors: thin validation + enqueue (shared middleware where runtimes allow); heavy logic lives in consumers. Timestamp discipline: `occurred_at` set at source, `received_at` at edge — lateness measured, never hidden.
- Monitoring: per-pipeline quality dashboards (dupe/late/loss rates, queue lag, rebuild history) feeding the same ops channel as money alerts.

## Acceptance criteria

- Taxonomy drill: unregistered event rejected at edge (TEST loud, production sampled + alerted); registered-but-malformed payload quarantined with owner notification.
  *GWT: Given Taxonomy drill context; When unregistered event rejected at edge (TEST loud, production sampled + alerted); Then registered-but-malformed payload quarantined with owner notification.*
- Exactly-once drill: redelivered event batches produce zero aggregate drift (idempotency keys hold under replay storms).
  *GWT: Given Exactly-once drill context; When the checked action occurs; Then redelivered event batches produce zero aggregate drift (idempotency keys hold under replay storms).*
- Retention drill: expired raw classes deleted on schedule (storage audit proves it); aggregates survive with documented provenance; deletion request clears raw buyer data within window.
  *GWT: Given Retention drill context; When expired raw classes deleted on schedule (storage audit proves it); Then deletion request clears raw buyer data within window.*
- Rebuild drill: destroyed aggregate rebuilt from raw + ledger to byte-identical figures (dual-report check during definition migrations).
  *GWT: Given Rebuild drill context; When the checked action occurs; Then destroyed aggregate rebuilt from raw + ledger to byte-identical figures (dual-report check during definition migrations).*
- Adoption drill: one existing pipeline gains a conformance note + taxonomy alias without behavior change; new pipeline ships conformant from its first review.
  *GWT: Given Adoption drill context; When one existing pipeline gains a conformance note + taxonomy alias without behavior change; Then new pipeline ships conformant from its first review.*

## Out of scope

- KPI definitions (see `data-analysis/metrics-framework.md`); experiment design (see `data-analysis/experimentation.md`); dashboard presentation (`sales/sales-analytics.md`).
- Money grant logic (provider majors + `payment/`); page analytics content (`docs/link-to-bio/`); P4/P5.

## Open questions

- Q1: Raw TTL values per class — align strictly with existing 90-day beacon precedent, or differentiate money/behavioral/advertising now?
- Q2: Shared edge-collector middleware vs per-pipeline endpoints — converge now or at next runtime upgrade?
