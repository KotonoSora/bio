# User Behavior (Journeys, Conversion, Retention) - Document Context and Scope (feature 53) - GATED

Phase 1 BA · Feature 53 of 53 — gated backlog 6/6. GATED: design-complete only — no table, route, job, or UI string ships until a spec amendment approves it (BR-01, AC-01). Sources: `user-behavior/buyer-journey-tracking.md`, `user-behavior/conversion-behavior.md`, `user-behavior/retention-behavior.md`. Implemented (future) by `docs/02-architecture-techlead/53-user-behavior/`.

## Context

Three coupled scopes: the complete buyer lifecycle as one analyzable trail (from first plan view through checkout attempts, provider switches, grant, renewal, cancel or win-back — so any drop-off can be traced step by step instead of guessed from aggregates); turning those trails into conversion decisions (which frictions actually cost paid orders, ranked by revenue impact, with baselines and playbooks); and what happens after the grant (adoption, quiet lapse, cancel reasons, win-back effectiveness). Funnel dashboards say WHERE buyers leave; journeys say WHAT they did before leaving — both are needed, neither substitutes. The future home is `analytics-service` (extension `V6__behavior.sql`); journey events ride 52's pipeline taxonomy, and the lifecycle job (36) consumes risk bands via a scoped internal read. Nothing here is built while gated.

## Objectives

| ID | Objective |
|---|---|
| OBJ-01 | One journey model: closed stage vocabulary, per-intent `journey_id` rotated per order, first-party stitching only |
| OBJ-02 | Friction markers first-class and counted — never real-time interventions, never dynamic pricing off behavior |
| OBJ-03 | Privacy bounds normative: k-anonymized readouts, raw TTLs, deletion honored, behavioral data never feeds gates/prices/fraud auto-actions |
| OBJ-04 | Ranked friction backlog (leak × reach × confidence) with baselines; causation only via 52's experiment registry |
| OBJ-05 | Retention loop closed: adoption → lapse signals → cancel reasons → win-back effectiveness, all explainable |
| OBJ-06 | Risk bands drive messaging intensity and support readiness only — transparent, bounded, never buyer-punitive |

## Scope

In scope (at amendment, SC-01..SC-05): journey stage vocabulary + stitching + closure sweeps; friction markers + ranking; segment cuts (closed set, k-anonymized); hesitation patterns + playbook links; error-class analysis; adoption/lapse/cancel/win-back/trial analyses.

Out of scope: funnel KPI dashboards (51); experiment execution and ship decisions (52); pipeline mechanics and taxonomy registry (52); lifecycle execution, grace, downgrade (36); reminder/send mechanics (36/48); support case handling (51); page-view analytics (12/31); any buyer-facing surface (none exists here).

## Personas and permissions

| Role | CRUD Permissions | Description |
|---|---|---|
| Operator/analyst | R journey aggregates, friction backlog, retention sections | Replays anonymized trails to diagnose friction; reads ranked backlogs |
| Experiment designer | R backlogs + baselines; C experiment proposals (via 52) | Sources hypotheses with evidential trails, not opinions |
| Lifecycle marketer | R response data (reminder/win-back effectiveness) | Tunes copy and timing from data, not taste |
| Support lead | R error-class + cancel-reason rankings | Fixes the causes in priority order |
| Privacy reviewer | R retention/minimization evidence; gate audits | Verifies bounds against one standard |
| Buyer (indirect) | none | Tracked only within strict bounds; treatment never changes from behavior data |

## Open-question resolutions

| # | Question (source) | Resolution |
|---|---|---|
| Q-01 | K-anonymity floor (journey Q1) | Default 10 journeys, config-owned (`analytics.kFloor`); sub-floor segments collapse into "other" automatically in every readout |
| Q-02 | Journey window (journey Q2) | Closure sweep finalizes stale trails after 14 days of inactivity as marked-inferred; an open proforma keeps its journey alive until 33's own expiry — no split mid-purchase |
| Q-03 | Review cadence (conversion Q1) | Monthly friction review confirmed; anomaly deep dives fire off 51's shared alerting in between |
| Q-04 | Revenue-at-stake ranges (conversion Q2) | Ranges with labeled uncertainty (confidence intervals), never false-precision point estimates |
| Q-05 | Activation definitions (retention Q1) | Product-owned checklists in versioned config (`retention.activation`); v1 seeds (pro: first publish or first integration; shop: first product live) finalized with product at the amendment |
| Q-06 | Risk-band visibility (retention Q2) | Internal-only; any buyer-visible "account health" is explicitly deferred to a future amendment |

## Constraints carried

Raw trails TTL-bound per 52's retention classes; journeys never form for declined-consent traffic (only unattributed aggregate-safe counts); no fingerprinting, third-party cookies, or device graphs anywhere; no creep-popups or real-time behavioral interventions in v1; price-sensitivity findings feed only aggregate experiments — personalized pricing is forbidden by 52's fairness floor; win-back never targets consent-declined buyers (48's classes apply); score logic published internally — no black boxes anywhere near buyers.
