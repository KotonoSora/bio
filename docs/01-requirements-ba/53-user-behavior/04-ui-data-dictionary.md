# User Behavior (Journeys, Conversion, Retention) - UI Data Dictionary (feature 53) - GATED

Phase 1 BA · GATED: no surface below may be built before the amendment. Surfaces: journey replay (k-anonymized), friction backlog, segment readouts, retention section, risk-band internal view. No buyer-facing surface exists in this feature.

## Journey replay + friction backlog

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `journeyTrailView` | Timeline | On load | Anonymized trail: ordered stages + markers; no account identifiers beyond scoped keys | Readable only aggregated or k-anonymized (floor 10) |
| `markerChips` | Chips | On load | `provider_switched`, `coupon_attempted` (outcome class only), `amount_mismatch_shown`, `quote_revisited`, `threedsecure_abandoned`, `error_shown` (class only) | Never the tried coupon code, never raw gateway text |
| `inferredBadge` | Badge | On sweep-closed trails | Marks closure-sweep finalizations as inferred | Observed vs inferred visibly distinct |
| `frictionBacklog` | Ranked table | Monthly | `orders_touched × abandon_lift × segment_value`; baselines attached | Correlation labeled; causation links an experiment |
| `playbookLinks` | Links | Per pattern | Each hesitation pattern links its remediation playbook | E.g. switch loops → provider-ordering test |
| `revenueAtStake` | Range display | Per row | Confidence interval shown | Never a false-precision point estimate |

## Segment + error readouts

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `segmentCuts` | Tables | On load | Closed set: new/returning, channel, device class, provider, plan vs product, coupon-involved | Sub-floor → "other" automatically |
| `stageConversion` | Chart | On load | Stage-conversion + marker rates per segment | k-anonymized everywhere |
| `errorClassTable` | Table | On load | Error classes ranked by frequency × abandon-rate | Only the mapped message buyers saw; raw text never leaves ops |
| `priceSensitivityPanel` | Read-only stats | On load | Coupon-attempt rates, term mix shifts, discount-depth vs renewal quality | Feeds aggregate experiments only — no personalized pricing control exists |

## Retention section (shared monthly cadence)

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| `adoptionPanel` | Distribution charts | On load | Time-to-activated by cohort; checklist version noted | Non-adopters flagged pre-renewal |
| `lapseBandsView` | Internal table | On load | Band (`healthy/watch/at-risk`) + contributing reasons per account | Internal-only; never buyer-visible |
| `cancelReasons` | Table | On load | Exit reasons by cohort/plan/tenure; skip rate shown | One-click skip measured, never punished |
| `saveOfferResults` | Experiment-linked | After runs | Save offers only as registered experiments | Honest copy; no maze |
| `winbackResults` | Table | Monthly | won_back/ignored/unsubscribed by cohort/offer/recency | Consent-declined buyers absent by construction |
| `trialEngagement` | Curves + bands | On load | Activated vs dormant; conversion by engagement band | Feeds trial experiments |

## Validation and behavior notes

- Every readout enforces the k-anonymity floor; there is no "raw export" button anywhere.
- Risk-band reasons render as plain-language contributors (adoption, usage decay, support contacts, payment expiry) — no opaque scores.
- Empty states: no journeys yet → pipeline registration guidance; no wins/losses in a period → honest zero with the window stated.
- No surface in this feature can change buyer treatment: no action buttons exist on behavior data beyond experiment proposals (which route to 52's registry).
