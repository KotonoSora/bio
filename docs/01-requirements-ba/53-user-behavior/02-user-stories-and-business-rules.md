# User Behavior (Journeys, Conversion, Retention) - User Stories and Business Rules (feature 53) - GATED

Phase 1 BA · GATED: design-only; AC-01 (absence) runs every release, all other drills run at/after the amendment. Scope: `docs/01-requirements-ba/53-user-behavior/01-document-context-and-scope.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Operator/analyst | to replay anonymized journey trails to diagnose friction | drop-offs trace step by step instead of guessed from aggregates | High |
| US-02 | Experiment designer | hypotheses with evidential trails and baselines | the next test is chosen from evidence | High |
| US-03 | Operator/founder | a ranked friction backlog (leak × reach × confidence) each cycle | effort lands on the leaks worth fixing | High |
| US-04 | Lifecycle marketer | adoption-to-renewal chains, lapse reasons, win-back effectiveness per cohort | reminder copy and timing come from response data | High |
| US-05 | Support lead | top cancel reasons and error classes in priority order | underlying causes get fixed | Medium |
| US-06 | Buyer | tracking within strict bounds — behavior never changes my prices, gates, or treatment | trust survives analytics | High |

## Business rules

- **BR-01 Gate (LIVE).** Design-complete only: no behavior table, route, job, chunk, or UI string may exist in any image or bundle until a spec amendment; absence drills run every release and a hit blocks promotion.
- **BR-02 Journey stages (closed vocabulary).** `plan_viewed → checkout_started → provider_selected → handoff_shown → authorize_attempted → grant_confirmed | failed | abandoned`, then `renewal_due → renewed | lapsed`, `cancel_initiated → cancelled`, `winback_offered → won_back | ignored`. Every checkout/order event carries the stable `journey_id` (per buyer + purchase intent, rotated per order — never a cross-order super-identifier).
- **BR-03 Stitching rules.** Events join a journey on `(user_id or buyer_email hash) + orderCode/proforma context` — never fingerprinting, third-party cookies, or device graphs. Logged-out browsing links to the purchase journey only at login/checkout identification; pre-identification browsing stays unattributed to the account.
- **BR-04 Friction markers (first-class events, not inferred).** `provider_switched` (from→to + timestamp), `coupon_attempted` (valid/invalid outcome class only — never the tried code), `amount_mismatch_shown`, `quote_revisited` (proforma reopen count), `threedsecure_abandoned`, `error_shown` (error class, never raw gateway text). Markers are counted and segmented only — no real-time interventions in v1 (no creep-popups, no dynamic pricing off behavior — explicitly forbidden).
- **BR-05 Privacy bounds (normative).** First-party only; no behavioral data sold, shared, or used for advertiser targeting; trails readable only aggregated or k-anonymized (floor 10 per Q-01, enforced in every readout); raw trails TTL-bound per 52's classes with buyer deletion requests honored across raw stores; behavioral data never feeds gates, prices, or fraud auto-bans (fraud signals stay in their own audited pipelines).
- **BR-06 Trail completeness.** Every grant, failure, cancel, and expiry writes its journey-close event; a closure sweep finalizes stale trails as `abandoned`/`lapsed` per Q-02, visibly marked as inferred rather than observed — no orphan open journeys past the retention window.
- **BR-07 Cross-surface continuity.** Plan-page quote previews, checkout, pending page, billing history, and renewal reminders all emit the same `journey_id` family — a multi-day purchase (proforma → approval → pay) reads as one story.
- **BR-08 Friction ranking (monthly).** Each marker scored by `orders_touched × abandon_lift_vs_baseline × segment_value` with baselines attached; correlation is labeled as correlation — causal claims require an experiment (handoff to 52's registry, which owns ship decisions).
- **BR-09 Segment cuts (closed set, k-anonymized).** New vs returning buyer, acquisition channel (direct/affiliate/campaign/ad), device class, provider chosen, plan vs product, coupon-involved vs full-price; sub-floor segments collapse to "other" automatically.
- **BR-10 Hesitation patterns (named, detected, counted, playbook-linked).** Provider-switch loops (2+ switches in one order), quote-revisit stalls (3+ proforma reopens without paying), coupon-hunting exits (invalid attempts then abandon), return-page refresh storms (polling without resolution), cross-device resume (counted, never invasively stitched). Each carries its playbook pointer (e.g. switch loops → provider-ordering test; refresh storms → confirming-UX fix).
- **BR-11 Error-class analysis.** Buyer-facing error classes ranked by frequency × abandon-rate; message/copy fixes first (cheapest lever), flow fixes second; raw gateway text never leaves the ops boundary — only the mapped message buyers actually saw is analyzed.
- **BR-12 Price-sensitivity signals (read-only, bounded).** Coupon-attempt rates by plan/term, yearly-vs-monthly mix shifts after price tests, discount-depth vs renewal-quality correlation; findings inform pricing experiments only — never personalized pricing (52's fairness floor forbids it).
- **BR-13 Reporting rhythm.** Monthly friction review (ranked backlog + segment deltas + experiment proposals) plus anomaly-triggered deep dives on funnel cliffs sharing 51's alerting; revenue-at-stake stated in ranges with labeled uncertainty.
- **BR-14 Adoption tracking.** Per-plan "activated" checklists (versioned config, Q-05) recorded as journey events; time-to-activated distributions by cohort; non-adopters flagged for onboarding attention before renewal risk materializes. Adoption is a product-usage fact, never a billing state.
- **BR-15 Lapse-signal scoring (transparent, bounded).** Signals (adoption never reached, usage decay across the term, support-contact spike, payment-method expiry on recurring rails) combine into an explainable band (`healthy|watch|at-risk`) with contributing reasons visible per account; scores drive reminder intensity and support readiness only — never gates, prices, or auto-actions against the buyer.
- **BR-16 Cancel-flow analysis.** Structured exit reasons (closed list + optional free text, one-click skippable — never a dark-pattern retention maze); reason distributions by cohort/plan/tenure; save-offer experiments (discount, pause, plan-switch) run only through 52's registry with honest copy; cancel completes in the same session regardless of answers.
- **BR-17 Win-back measurement.** Lapsed-buyer outreach tracked as `won_back | ignored | unsubscribed`, cut by cohort, offer kind, and time-since-lapse; offers never exceed promo stacking rules and never target buyers who declined marketing consent (48's classes).
- **BR-18 Trial-to-paid behavior.** Trial engagement curves (activated vs dormant), conversion by engagement band, dormant-trial rescue messaging — feeding trial-length/scope experiments with evidence instead of defaults.

## Acceptance criteria pointers

Drills in `docs/01-requirements-ba/53-user-behavior/06-acceptance-criteria.md`: absence (live), trail, stitching, privacy, closure, auth, ranking, segment, playbook, correlation, price-sensitivity, adoption, lapse, cancel, win-back, trial. GWT forms live there.
