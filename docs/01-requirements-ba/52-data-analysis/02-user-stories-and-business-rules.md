# Data Analysis (Pipeline Standards, Experiments, Metrics) - User Stories and Business Rules (feature 52) - GATED

Phase 1 BA · GATED: design-only; AC-01 (absence) runs every release, all other drills run at/after the amendment. Scope: `docs/01-requirements-ba/52-data-analysis/01-document-context-and-scope.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Implementer | a checklist that makes my pipeline's data trustworthy on day one | I don't reinvent dedupe or leak PII | High |
| US-02 | Analyst/operator | to read any aggregate knowing its dedupe, filter, and freshness semantics | numbers come with their meaning | High |
| US-03 | Operator/founder | to propose a pricing test and get an answer with a date | decisions become measured bets with rollback plans | High |
| US-04 | Finance | to trust no experiment corrupts closed books or grandfathering | revenue truth survives every test | High |
| US-05 | Privacy reviewer | to verify retention and minimization against one standard | claims are provable centrally | High |
| US-06 | Operator/founder | to quote MRR, NRR, conversion, churn knowing finance sees the identical figure | one number ends every argument | High |
| US-07 | Buyer | stable, honest variant treatment — disclosed where material | never broken or unfair treatment | Medium |

## Business rules

- **BR-01 Gate (LIVE).** Design-complete only: no standards surface (table, route, job, chunk, UI string) may exist in any image or bundle until a spec amendment; absence drills run every release; a hit blocks promotion.
- **BR-02 Taxonomy (closed, versioned).** `{namespace}.{object}.{verb}` in `snake_case`, each event carrying `{event_id UNIQUE, occurred_at, actor_key?, properties (flat, typed, allowlisted per event)}`; new events registered with owner + retention class before emission; unregistered rejected at the edge (loud in TEST, sample-and-alert in production); malformed registered payloads quarantined with owner notification.
- **BR-03 Transport discipline (mandatory).** Validate → queue (never direct DB writes) → deduped consumer (key = `event_id`; KV pre-check + UNIQUE persist) → aggregates; raw payloads to R2 NDJSON with per-class TTL; at-least-once assumed; exactly-once effects via keys; `occurred_at` at source, `received_at` at edge — lateness measured, never hidden.
- **BR-04 Quality bars per class.** Money events: exactness — ledger reconciliation with zero unexplained delta; funnel events: documented completeness bounds, monitored; advertising billed events: evidence bundles (50); quality metrics (dupe/late/loss, queue lag) are emitted and alerted on the money-alert ops channel.
- **BR-05 Retention and privacy (uniform).** Raw TTLs by class (Q-01, aligned precedents); no raw IPs/UAs in PG aggregates (hashes only); buyer PII never in analytics tables beyond owner-scoped keys; deletion requests propagate to raw stores within the documented window; aggregates (anonymous) stand.
- **BR-06 Rebuild discipline.** Every aggregate idempotently rebuildable from raw + ledger sources (key = grain + window); rebuilds are routine (outage backfill, definition migrations with dual reporting); a runbook per pipeline lives with its dashboards.
- **BR-07 Adoption path.** Existing pipelines adopt taxonomy + standards incrementally at their next scheduled change as conformance notes in their own docs — never a flag-day rewrite; new pipelines conform from birth (review gate).
- **BR-08 Experiment registry (audited).** Each test records `{hypothesis, variants, randomization unit = user_id (never session/device), primary metric + MDE, guardrails (conversion floor, refund/dispute ceilings, support-contact rate), planned duration/sample, ship rule}` before launch; unregistered changes are not experiments and don't touch pricing surfaces.
- **BR-09 Eligible surfaces + fairness floor.** Eligible: catalog prices/terms presentation, provider ordering, trial length/scope, reminder/dunning copy and timing, promo variants. Excluded forever: grant logic, entitlement, security controls. Fairness floor: no undisclosed per-user price differences for the same SKU (Q-03).
- **BR-10 Assignment and stability.** Deterministic hash of `user_id` + experiment key → sticky variant; pinned to the order at creation; mid-experiment re-bucketing forbidden; early stopping only by the pre-registered rule — no peeking.
- **BR-11 Guardrails and kill conditions.** Breach auto-pauses + pages ops (same kill-switch discipline as provider incidents); no variant alters closed-period figures or grandfathered terms; catalog versioning carries variant stamps so renewals stay consistent post-ship.
- **BR-12 Promo holdouts.** Every major promo campaign carries a holdout (eligible non-recipients vs recipients) sized by the power rule; holdout buyers never see degraded UX; results feed campaign readouts (true lift, not redemptions).
- **BR-13 Readout and decision.** Fixed window; framework-KPI metrics only (no post-hoc metric shopping); decision logged (ship/keep/iterate) with evidence; shipped variants roll out via the normal catalog/config path.
- **BR-14 Metric registry (versioned, append-only).** Each KPI carries `{name, formula, input tables, grain, edge-case rules, version, effective_from}`; v1 set: MRR/ARR, NRR/GRR, ARPU, conversion, refund rate, dispute rate, CAC payback, AI cost per generation.
- **BR-15 Edge-case rules (decided once, applied everywhere).** Trials never enter MRR until first paid grant; grace counts as retained until expiry; coupon-discounted terms enter at net collected; partial refunds net the period they post in — closed snapshots immutable; disputed-then-won revenue re-enters in the win period with a visible restatement note.
- **BR-16 Base and cohort discipline.** Billable base, acquisition cohorts (month × channel), and plan-version cohorts defined once; "churned" = ledger `expired` without renewal in the cohort window; "active" = ledger `active|grace|trial` current row; no dashboard may redefine either.
- **BR-17 Change control.** A redefinition publishes a new version with dual reporting over one overlap period; finance close names the definition version used; breaking changes announced a cycle ahead; dashboards migrate, history is never rewritten.
- **BR-18 One-definition enforcement.** A single metrics library (one function per KPI) consumed by dashboards, exports, and alerts; copy-pasted SQL is a review failure; unit tests assert each KPI against hand-computed fixtures including every BR-15 edge case.
- **BR-19 Review cadence.** Definitions reviewed quarterly with finance (or on any pricing/packaging change); unused metrics retire to an archive section, never deleted.

## Acceptance criteria pointers

Drills: `docs/01-requirements-ba/52-data-analysis/06-acceptance-criteria.md` (AC-01 absence live; AC-02..AC-17 at amendment).
