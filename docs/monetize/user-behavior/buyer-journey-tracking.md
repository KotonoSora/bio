# Buyer Journey Tracking (lifecycle event trails)

PLAN ref: none — new scope beyond PLAN P0–P5, flagged explicitly. Proposed as P4-growth with the analytics track; needs a plan update before build. Event mechanics follow `data-analysis/analytics-pipeline.md`; funnel KPIs live in `sales/sales-analytics.md` — this doc owns the journey model both consume.

## Purpose

Define the complete buyer lifecycle as one analyzable trail — from first plan view through checkout attempts, provider switches, grant, renewal, and eventual cancel or win-back — so any drop-off or loop can be traced step by step instead of guessed from aggregate counts. Funnel dashboards say WHERE buyers leave; journey trails say WHAT they did before leaving. Both are needed; neither substitutes for the other.

## Users

- Operator/analyst: *As an Operator/analyst, I want to replay anonymized journey trails to diagnose friction (e.g. provider-switch loops before abandon) so that the complete buyer lifecycle as one analyzable trail as defined here.* replays anonymized journey trails to diagnose friction (e.g. provider-switch loops before abandon).
- Experiment designer: *As an Experiment designer, I want to source hypotheses with evidential trails instead of opinions (feeds `data-analysis/experimentation.md`) so that the complete buyer lifecycle as one analyzable trail as defined here.* sources hypotheses with evidential trails instead of opinions (feeds `data-analysis/experimentation.md`).
- Buyer: *As a Buyer, I want to be tracked only within strict privacy bounds — journey data never changes their prices, gate, or treatment so that the complete buyer lifecycle as one analyzable trail as defined here.* tracked only within strict privacy bounds — journey data never changes their prices, gates, or treatment.

## Functional requirements

1. Journey stages (closed vocabulary, registered per the pipeline taxonomy): `plan_viewed → checkout_started → provider_selected → handoff_shown → authorize_attempted → grant_confirmed | failed | abandoned`, then post-purchase `renewal_due → renewed | lapsed`, `cancel_initiated → cancelled`, `winback_offered → won_back | ignored`. Every checkout/order event carries the stable `journey_id` (per buyer + purchase intent, rotated per order — never a cross-order super-identifier).
2. Session stitching rules: events join a journey on `(user_id or buyer_email hash) + orderCode/proforma context`, never on fingerprinting, third-party cookies, or device graphs. Logged-out browsing links to the purchase journey only at login/checkout identification — pre-identification browsing stays unattributed to the account.
3. Friction markers (first-class events, not inferred): `provider_switched` (from→to + timestamp), `coupon_attempted` (valid/invalid outcome class only — never the tried code), `amount_mismatch_shown`, `quote_revisited` (proforma reopen count), `threedsecure_abandoned`, `error_shown` (error class, never raw gateway text). Markers exist to be counted and segmented, not to trigger real-time interventions in v1 (no creep-popups, no dynamic pricing off behavior — explicitly forbidden).
4. Privacy bounds (normative): first-party only; no behavioral data sold, shared, or used for advertiser targeting; journey trails readable only in aggregated or k-anonymized form (minimum bucket sizes enforced — see Q1); raw trails TTL-bound per the pipeline retention classes with buyer deletion requests honored across raw stores. Behavioral data never feeds gates, prices, or fraud auto-bans (fraud signals stay in their own audited pipelines).
5. Trail completeness contract: every grant, failure, cancel, and expiry writes its journey-close event (no orphan open journeys past the retention window — a closure sweep finalizes stale trails as `abandoned`/`lapsed` by rule, visibly marked as inferred rather than observed).
6. Cross-surface continuity: plan-page quote previews, checkout, pending page, billing history, and renewal reminders all emit with the same `journey_id` family so a multi-day purchase (proforma → approval → pay) reads as one story.

## Data / API touches

- Events flow through the standard pipeline (validate → queue → dedupe → aggregates per `data-analysis/analytics-pipeline.md`); journey assembly is a batch job over raw + order tables, never in-request. New aggregate `buyer_journeys_daily{day, segment..., stage_transitions, friction_counts}` (k-anonymized, minimum-bucket enforced). All PG via `worker/db/*`; RLS restricts to operator/analyst roles.
- No buyer/affiliate/creator-facing surface: journey data never appears in scoped earner dashboards or buyer UX.
- Consent posture follows the analytics model (declined-consent traffic contributes only unattributed, aggregate-safe counts — journeys simply don't form for it).

## Acceptance criteria

- Trail drill: seeded multi-day purchase (view → proforma → provider switch → coupon attempt → pay → renew) assembles into one complete journey with all markers ordered correctly.
  *GWT: Given Trail drill: seeded multi-day purchase (view; When the transition executes; Then proforma → provider switch → coupon attempt → pay → renew) assembles into one complete journey with all markers ordered correctly.*
- Stitching drill: logged-out browsing stays unattributed until identification; no fingerprint/device-graph joins exist in code (review gate).
  *GWT: Given Stitching drill context; When logged-out browsing stays unattributed until identification; Then no fingerprint/device-graph joins exist in code (review gate).*
- Privacy drill: sub-threshold segments suppressed in every readout; deletion request clears raw trails within window; behavioral data provably unused by gates/pricing/fraud-auto-actions (grep + review gate).
  *GWT: Given Privacy drill context; When sub-threshold segments suppressed in every readout; Then behavioral data provably unused by gates/pricing/fraud-auto-actions (grep + review gate).*
- Closure drill: stale open journeys finalize as marked-inferred within the window; no orphans past retention.
  *GWT: Given Closure drill context; When stale open journeys finalize as marked-inferred within the window; Then no orphans past retention.*
- Auth probe: non-ops roles on journey aggregates → 403; earner-scoped callers see only their ledgers.
  *GWT: Given Auth probe: non-ops roles on journey aggregates; When the transition executes; Then 403; earner-scoped callers see only their ledgers.*

## Out of scope

- Friction analysis and segment readouts (see `user-behavior/conversion-behavior.md`); retention and win-back analysis (see `user-behavior/retention-behavior.md`).
- Pipeline mechanics and taxonomy (`data-analysis/analytics-pipeline.md`); experiment design (`data-analysis/experimentation.md`); funnel KPIs (`sales/sales-analytics.md`); page-view analytics (`docs/link-to-bio/`); P4/P5.

## Open questions

- Q1: K-anonymity floor — minimum bucket size for journey readouts (e.g. 10 journeys)?
- Q2: Journey window — how long may a single purchase intent span before it splits (proforma validity vs fixed cap)?
