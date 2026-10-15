# Experimentation (pricing tests and measured rollouts)

PLAN ref: none — new scope beyond PLAN P0–P5, flagged (see `data-analysis/metrics-framework.md` for placement). KPIs there; pipeline standards in `data-analysis/analytics-pipeline.md`.

## Purpose

Define how the business learns what works instead of arguing about it: registered pricing/packaging/checkout experiments with fixed hypotheses, guardrail metrics, and pre-committed ship rules. Price changes, provider ordering, trial lengths, and dunning copy all move revenue — shipping them on instinct risks the base, while this discipline makes every change a measured bet with a rollback plan.

## Users

- Operator/founder: proposes "yearly −20% vs −10%" and gets an answer with a date, not a debate.
- Finance: trusts that no experiment silently corrupts closed books or grandfathering promises.
- Buyer: never sees broken or unfair treatment — variant assignment is stable, honest, and disclosed where material.

## Functional requirements

1. Experiment registry (audited): each test records `{hypothesis, variants, randomization unit (user_id — never session or device), primary metric + minimum detectable effect, guardrail metrics (conversion floor, refund/dispute ceilings, support-contact rate), planned duration/sample, ship rule}` BEFORE launch. Unregistered changes are not called experiments and don't touch pricing surfaces.
2. Eligible surfaces in v1: catalog prices/terms presentation, provider ordering in checkout, trial length/scope, reminder/dunning copy and timing, promo campaign variants (holdout groups measuring incrementality — see 5). Excluded: grant logic, entitlement rules, security controls (never experimented on), and any treatment that charges different users different amounts for the same SKU without disclosure (fairness floor — see Q1).
3. Assignment and stability: deterministic hash on `user_id` + experiment key → variant (sticky across sessions/devices); buyers see consistent pricing throughout a session and across the pending window (variant pinned to the order at creation). Mid-experiment re-bucketing is forbidden; early stopping requires the pre-registered rule, not peeking.
4. Guardrails and kill conditions: guardrail breach (conversion floor, dispute/refund ceiling, checkout error spike) auto-pauses the experiment and pages ops — the same kill-switch discipline as provider incidents. Finance guardrail: no variant may alter closed-period figures or grandfathered terms; catalog versioning (`subscription/plan-catalog.md`) carries variant stamps so renewals stay consistent post-ship.
5. Incrementality for promos: every major promo campaign carries a holdout (eligible non-recipients measured against recipients) so the business learns true lift, not just redemption counts. Holdouts sized by the registry's power rule; results feed the campaign-management readout.
6. Readout and decision: fixed analysis window, framework-KPI metrics only (no post-hoc metric shopping), decision logged (ship variant / keep control / iterate) with the evidence attached. Shipped variants roll out via the normal catalog/config change path with versioning intact.

## Data / API touches

- New tables `experiments{id, hypothesis, variants_json, status (draft|running|paused|decided), ship_rule}` + `experiment_assignments{experiment_id, user_id, variant}` (sticky, written once) with RLS posture (operators manage; assignment reads server-side only — variant never trusted from the client). All PG via `worker/db/*`.
- Checkout/pricing surfaces read assignment server-side and stamp `experiment_id + variant` on orders for analysis segmentation. No buyer-facing experiment surface beyond the (honest, stable) treatment itself.
- Analysis reuses funnel + KPI aggregates segmented by variant; no separate experimentation data store in v1.

## Acceptance criteria

- Registration drill: unregistered pricing change blocked from experiment surfaces by review gate; registered test carries hypothesis, MDE, guardrails, and ship rule before launch.
- Stability drill: same user sees the same variant across sessions, devices, and pending windows; no mid-flight re-bucketing under load.
- Guardrail drill: seeded conversion dip auto-pauses the test and pages with the offending slice; finance figures and grandfathered terms provably untouched.
- Holdout drill: promo campaign with holdout reports true lift vs raw redemptions; holdout buyers never learn they were held out through degraded UX.
- Decision drill: readout uses framework KPIs only; decision + evidence logged; shipped variant rolls through catalog versioning with renewals consistent.

## Out of scope

- KPI definitions (see `data-analysis/metrics-framework.md`); pipeline and taxonomy standards (see `data-analysis/analytics-pipeline.md`); dashboard presentation (`sales/sales-analytics.md`).
- Catalog mechanics (`subscription/plan-catalog.md`); campaign operations (`promo-code/`); grant/entitlement logic (never experimented on); P4/P5.

## Open questions

- Q1: Fairness floor — which price treatments require explicit buyer disclosure, and who approves the copy (legal review threshold)?
- Q2: Minimum experiment scale — traffic floors below which tests are declared underpowered instead of run?
