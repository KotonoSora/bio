# Conversion Behavior (friction, hesitation, and segments)

PLAN ref: none — new scope beyond PLAN P0–P5, flagged (see `user-behavior/buyer-journey-tracking.md` for placement). Trails there; hypotheses feed `data-analysis/experimentation.md`; KPI definitions in `data-analysis/metrics-framework.md`.

## Purpose

Turn journey trails into conversion decisions: which frictions actually cost paid orders, which segments hesitate where, and what to test next — ranked by revenue impact, not anecdote. Every checkout flow has a loudest complainer and a quietest killer; this analysis separates them so engineering effort lands on the leaks worth fixing instead of the squeakiest tickets.

## Users

- Operator/founder: gets a ranked friction backlog (leak × reach × confidence) each cycle.
- Experiment designer: picks the next test from evidence, with baseline rates attached.
- Support lead: learns which error classes and provider failures drive contacts, and fixes messaging first.

## Functional requirements

1. Friction ranking (monthly cadence): each friction marker from the journey doc scored by `orders_touched × abandon_lift_vs_baseline × segment_value`, producing a ranked backlog with baselines attached. Correlation is labeled as correlation — causal claims require an experiment (handoff rule to the experimentation registry, which owns ship decisions).
2. Segment cuts (closed set, k-anonymized): new vs returning buyer, acquisition channel (direct/affiliate/campaign/ad), device class, provider chosen, plan vs product, coupon-involved vs full-price. Segments compare stage-conversion and marker rates; segments below the anonymity floor collapse into "other" automatically.
3. Hesitation patterns (named, detected, counted): provider-switch loops (2+ switches in one order), quote-revisit stalls (3+ proforma reopens without paying), coupon-hunting exits (invalid attempts followed by abandon), return-page refresh storms (polling without resolution), and cross-device resume (started mobile, paid desktop — counted, never stitched invasively). Each pattern carries a playbook pointer (e.g. switch-loops → provider ordering test; refresh storms → confirming-UX fix).
4. Error-class analysis: buyer-facing error classes ranked by frequency × abandon-rate, feeding message/copy fixes first (cheapest lever) and flow fixes second. Raw gateway text never leaves the ops boundary; buyers only ever saw the mapped message, which is what gets analyzed.
5. Price-sensitivity signals (read-only, carefully bounded): coupon-attempt rates by plan/term, yearly-vs-monthly mix shifts after price tests, and discount-depth vs renewal-quality correlation. Findings inform pricing experiments — they never trigger personalized pricing (forbidden by the experimentation fairness floor).
6. Reporting rhythm: monthly friction review (ranked backlog + segment deltas + experiment proposals) plus anomaly-triggered deep dives on funnel cliffs (shared alerting with sales-analytics). Reports name revenue at stake in ranges, never false-precision point estimates.

## Data / API touches

- Reads journey aggregates + funnel tables + order events; writes nothing buyer-visible (analysis outputs land in ops reports and the experiment registry). Computations run as batch jobs on the analytics cadence (hourly-or-better freshness per the sales-analytics decision). All PG via `worker/db/*`; RLS restricts to operator/analyst roles.
- Segment definitions versioned alongside the metrics framework (a renamed segment is a new version with overlap reporting, same discipline as KPI changes).
- No new collection: every input already flows from the journey-tracking events; gaps found here become event-registration requests, not ad-hoc logging.

## Acceptance criteria

- Ranking drill: seeded friction (provider outage on one rail, confusing coupon error) ranks first and second by revenue-at-stake with correct baselines; anecdote-heavy but low-impact issues rank low.
- Segment drill: sub-floor segments collapse to "other" in every readout; no segment re-identifies buyers (review gate on minimum-bucket enforcement).
- Playbook drill: each named hesitation pattern links to its remediation experiment or fix; refresh-storm spike during an incident auto-suggests the confirming-UX fix.
- Correlation-discipline drill: no readout claims causation without a linked experiment; causal language blocked by review checklist.
- Privacy drill: price-sensitivity work provably feeds only aggregate experiments, never personalized treatment (code + review gate).

## Out of scope

- Journey event model and trails (see `user-behavior/buyer-journey-tracking.md`); retention and win-back analysis (see `user-behavior/retention-behavior.md`).
- Experiment execution and ship decisions (`data-analysis/experimentation.md`); KPI definitions (`data-analysis/metrics-framework.md`); funnel dashboards (`sales/sales-analytics.md`); P4/P5.

## Open questions

- Q1: Review cadence — monthly friction review confirmed, or tighter during launch quarters?
- Q2: Revenue-at-stake ranges — show confidence intervals in readouts, or point estimates with labeled uncertainty?
