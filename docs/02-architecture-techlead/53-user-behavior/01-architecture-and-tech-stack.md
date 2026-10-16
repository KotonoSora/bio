# User Behavior (Journeys, Conversion, Retention) - Architecture and Tech Stack (feature 53) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/53-user-behavior/` (7 files, gated). GATED: design-complete only — no component below exists in any image, bundle, or migration until a spec amendment approves it (BR-01, AC-01). Stack pins per `docs/02-architecture-techlead/00-platform-baseline/`. Seams consumed: 52's pipeline taxonomy + experiment registry, 36's lifecycle execution, 48's send path + consent classes, 51's alerting.

## Boundary (at amendment): `analytics-service` extension

When built: `analytics-service` (existing, Redis db 6) gains `V6__behavior.sql` — journey aggregates, friction rankings, exit reasons, retention bands, adoption flags, win-back results. Journey events ride 52's pipeline (registered taxonomy); the lifecycle job reads bands through a scoped internal read; nothing here is ever readable by gating, pricing, or fraud paths.

| Component (at amendment) | Change | Notes |
|---|---|---|
| `JourneyAssembler` | Batch over raw + order tables; `journey_id` grouping; closure sweeps | Never in-request; sweep marks inferred closures |
| `JourneyAggregates` (V6) | `buyer_journeys_daily` (k-anonymized) | Floor 10 enforced in every readout; no raw export exists |
| `FrictionAnalyzer` (V6) | `friction_ranking` + playbook links + segment cuts | Correlation labeled; causation routes to 52's registry |
| `ExitReasons` (V6) | `exit_reasons` distributions | Capture rides 36's cancel flow (no buyer surface here) |
| `RetentionScorer` (V6) | `adoption_flags` + `retention_bands` | Explainable bands with visible reasons; schema-separated from gates/pricing/fraud |
| `WinbackResults` (V6) | `winback_results` by cohort/offer/recency | Sends ride 36/48 paths; consent arbitrated by 48's eligibility |
| Risk-band internal read | 36 registers at amendment: reminder intensity + support readiness only | No other consumer may exist (dependency gate) |
| Shared alerting | Funnel-cliff deep dives ride 51's alerting | One ops channel for money + funnel alerts |

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | Extend `analytics-service` (V6); behavior aggregates in `analytics_db` | Behavior data stays in analytics; zero new services |
| D-2 | Journey assembly batch-only; joins strictly on `(user_id or email hash) + order/proforma context` | BR-03; no fingerprinting code exists anywhere (grep gate) |
| D-3 | K-anonymity floor (default 10, config) enforced at the readout layer, not per consumer | BR-05; one enforcement point, no raw export path |
| D-4 | Retention bands live in buyer-scoped analytics tables, schema-separated from gating/entitlement/fraud | BR-15; dependency + review gate proves non-consumption |
| D-5 | Journey closure sweeps mark inferred endings distinctly from observed ones | BR-06; dashboards never present inference as observation |
| D-6 | Friction rankings label correlation; causal claims require a linked experiment in 52's registry | BR-08; the registry owns ship decisions |
| D-7 | Win-back/rescue sends reuse 36/48 paths with 48's consent arbitration — no new mail infrastructure | BR-17; consent classes apply at send time |
| D-8 | Price-sensitivity findings feed aggregate experiments only; personalized pricing is structurally absent | BR-12; 52's fairness floor restated as an architecture constraint |

## Cross-edits (all at amendment, not now)

| Target | Edit (at amendment) |
|---|---|
| `docs/02-architecture-techlead/00-platform-baseline/01-architecture-and-service-topology.md` | analytics-service row note: behavior extensions (V6) |
| `docs/02-architecture-techlead/52-data-analysis/` | journey/marker events registered in the taxonomy; experiment handoff rows |
| `docs/02-architecture-techlead/36-subscription-lifecycle/` | risk-band internal read seam (reminder intensity + support readiness only) |
| `docs/02-architecture-techlead/48-marketing/` | win-back/rescue via campaign path; eligibility arbitrates consent per recipient |
| `docs/02-architecture-techlead/51-sales/` | funnel-cliff deep dives share the alert channel |
| `MIGRATION.md` | feature 53 row (doc migration landed with this batch; build at amendment) |
