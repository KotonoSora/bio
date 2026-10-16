# User Behavior (Journeys, Conversion, Retention) - Workflow and User Journey (feature 53) - GATED

Phase 1 BA · W-1..W-6. GATED: all flows are design; nothing executes until the amendment. Rules: `docs/01-requirements-ba/53-user-behavior/02-user-stories-and-business-rules.md`. Contracts: `docs/02-architecture-techlead/53-user-behavior/03-api-contracts-and-payloads.md`.

## W-1 Journey assembly

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Buyer | views a plan / starts checkout | stage events emit with `journey_id` via 52's pipeline (validated, queued, deduped) | BR-02, BR-03 |
| 2 | System | multi-day purchase (proforma → approval → pay) | same `journey_id` family across surfaces reads as one story | BR-07 |
| 3 | System | grant/failure/cancel/expiry | journey-close event writes; trail marked complete | BR-06 |
| 4 | System | 14 days of inactivity on an open trail | closure sweep finalizes as marked-inferred `abandoned`; no orphans past retention | BR-06, Q-02 |

## W-2 Friction review (monthly)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System | markers aggregate | ranked backlog: `orders_touched × abandon_lift × segment_value` with baselines | BR-08 |
| 2 | Analyst | reads the backlog | correlation labeled; playbook pointer per pattern; revenue-at-stake as ranges | BR-08, BR-10, BR-13 |
| 3 | Experiment designer | picks the next test | proposal enters 52's registry with baseline rates attached | BR-08 |
| 4 | System | funnel cliff detected | anomaly deep dive fires via 51's shared alerting | BR-13 |

## W-3 Cancel → win-back

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Buyer | cancels | exit-reason capture (closed list + optional text) shown — one-click skippable; cancel completes this session regardless | BR-16 |
| 2 | System | reasons aggregate | distributions by cohort/plan/tenure; top causes feed the support lead's fix order | BR-16, BR-11 |
| 3 | System | save offer considered | only as a registered experiment with honest copy; never a maze | BR-16 |
| 4 | Lifecycle marketer | win-back sequence runs | tracked won_back/ignored/unsubscribed by cohort/offer/recency; consent-declined buyers excluded | BR-17 |

## W-4 Adoption → lapse → reminder tuning

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System | first-value moment reached (per-plan checklist) | adoption event records; time-to-activated distribution updates | BR-14 |
| 2 | System | non-adopter approaches renewal | flagged for onboarding attention; band reasons visible | BR-14, BR-15 |
| 3 | System | decay/support/payment signals combine | explainable band (`healthy/watch/at-risk`) with contributing reasons | BR-15 |
| 4 | Lifecycle job (36) | reads bands via scoped internal read | reminder intensity + support readiness tuned — nothing else changes | BR-15 |
| 5 | Buyer | checks their treatment | prices, gates, and features provably unchanged by behavior data | BR-05, BR-15 |

## W-5 Trial rescue

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System | trial engagement curves build | activated vs dormant split; conversion by engagement band | BR-18 |
| 2 | Lifecycle marketer | dormant-trial rescue messaging tuned | evidence feeds trial-length/scope experiments, not defaults | BR-18 |
| 3 | System | trial ends | journey-close writes (`renewed` or `lapsed`) | BR-06 |

## W-6 Privacy / deletion

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Buyer | requests deletion | raw trails cleared within the documented window; aggregates (anonymous) stand | BR-05 |
| 2 | Privacy reviewer | audits readouts | k-floor enforced everywhere; sub-floor collapses to "other" | BR-05, BR-09 |
| 3 | Reviewer | audits code paths | behavioral data provably unread by gates/pricing/fraud auto-actions (grep + dependency check) | BR-05 |
| 4 | Anyone | attempts off-platform sharing | No route or surface exists | BR-05 |
