# User Behavior (Journeys, Conversion, Retention) - Security, Logging, and Rate Limiting (feature 53) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/53-user-behavior/`. GATED: S-1 is live now; all other controls bind the future build at the spec amendment. Baseline controls: `docs/02-architecture-techlead/00-platform-baseline/`.

## Controls

| # | Control | Rule |
|---|---|---|
| S-1 | Gate enforcement (LIVE) | Tooling asserts zero behavior surface each release (schema/OpenAPI/source/bundle); a hit blocks promotion — the gate is tool-enforced, not policy |
| S-2 | Stitching integrity | Joins only on `(user_id or email hash) + order/proforma context`; zero fingerprinting/device-graph code (grep gate); logged-out traffic unattributed until identification |
| S-3 | Anonymity enforcement | K-floor (default 10, config) enforced at the readout layer; sub-floor collapse automatic; no raw-trail export route exists anywhere |
| S-4 | Non-consumption gate | Behavioral data provably unread by gating/pricing/fraud: CI dependency check + review gate; risk bands consumable only by the 36 seam (reminder intensity + support readiness) |
| S-5 | Marker discipline | Outcome classes only (`coupon_attempted` never stores tried codes; `error_shown` never stores raw gateway text); markers are counted, never acted on in real time |
| S-6 | No intervention | No creep-popups, no dynamic pricing, no real-time behavioral interventions in v1 — release review blocks any such code path |
| S-7 | Privacy propagation | Raw trails TTL-bound per 52's classes; deletion requests clear raw stores within the documented window; aggregates (anonymous) stand; declined-consent traffic forms no journeys |
| S-8 | Send-path discipline | Win-back/rescue ride 36/48 paths only; consent classes arbitrated at send time by 48's eligibility; promo stacking rules apply; consent-declined buyers excluded by construction |
| S-9 | Correlation discipline | Friction readouts label correlation; causal claims require a linked 52-registry experiment; review checklist blocks unlabeled causal language |
| S-10 | Access scoping | Behavior readouts operator/analyst only; bands internal-only (never buyer-visible); earner scopes see only their ledgers; zero PII beyond owner-scoped keys |

## Rate limits (at amendment; analytics-service Redis db 6)

| Surface | Limit | On breach |
|---|---|---|
| Behavior readouts | 300/h/role | 429 `throttled` |
| Internal risk-band reads | 600/h/service | 429 |
| Experiment handoffs | 30/h/analyst | 429 |
| Retention-section exports (aggregate CSV) | 10/h/operator | 429 |

## Degrade matrix (at amendment)

| Failure | Behavior |
|---|---|
| Assembly batch down | Readouts serve last aggregates with timestamp; no fabricated freshness; alert after 2 missed runs |
| Sweep down | Orphan count monitored; alerts before retention-window breach; no silent orphans |
| Risk-band read down | 36 falls back to its own cadence (no band tuning); nothing buyer-facing changes; bands never fail open into gates (no consumer exists) |
| Win-back send path down | 36/48 queues with backlog; results reconcile on recovery; ledger truth unaffected |
| Experiment registry (52) down | Handoffs queue; no friction claim upgrades to causal without a link |
| Redis down | Rate limits degrade to conservative fixed windows; readouts unaffected |

## Audit and observability (at amendment)

Audits (24-month): segment-definition versions, playbook link changes, experiment handoffs, deletion propagations, band-logic versions, sweep runs, readout access by role. Metrics: journey counts by stage, marker rates, sub-floor suppression counts, inferred closures, adoption/band distributions, win-back responses, sweep outcomes — zero buyer labels.

## Support answers (at amendment)

"Where did buyers drop off?" → journey aggregates: stage transitions + markers per segment (k-anonymized). "Which friction matters most?" → ranked backlog with baselines; correlation labeled; playbook attached. "Why did this cohort churn?" → cancel reasons + band reasons per account (internal). "Did win-back work?" → won_back/ignored/unsubscribed by cohort/offer/recency. "Can behavior data change my price?" → no — dependency-gated: prices, gates, and treatment never read behavior data.
