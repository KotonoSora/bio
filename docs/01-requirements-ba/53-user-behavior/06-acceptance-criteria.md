# User Behavior (Journeys, Conversion, Retention) - Acceptance Criteria (feature 53) - GATED

Phase 1 BA · GATED: AC-01 runs every release (live now); AC-02..AC-16 run only at/after the spec amendment. Drills reference `docs/02-architecture-techlead/53-user-behavior/04-step-by-step-implementation-plan.md`.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Absence drill (LIVE) | Any release candidate while gated | Schema diff, OpenAPI diff, source + bundle grep run | Zero behavior surface found (journey tables, replay UI, friction backlog, risk-band code); a hit blocks promotion by tooling |
| AC-02 | Trail drill | Seeded multi-day purchase (view → proforma → provider switch → coupon attempt → pay → renew) | Assembly runs | One complete journey with all markers ordered correctly; `journey_id` family continuous across surfaces |
| AC-03 | Stitching drill | Logged-out browsing before identification | Stitching evaluated | Stays unattributed until login/checkout; no fingerprint/device-graph joins exist in code (review gate) |
| AC-04 | Privacy drill | Sub-floor segments; a deletion request; code-path audit | Readouts + audit run | Sub-floor suppressed everywhere; raw trails cleared within window; behavioral data provably unread by gates/pricing/fraud auto-actions |
| AC-05 | Closure drill | Stale open journeys | Sweep runs | Finalized as marked-inferred within the window; zero orphans past retention |
| AC-06 | Auth drill | Non-ops roles on behavior readouts; earner-scoped callers | Attempts run | 403 both ways; earner callers see only their ledgers |
| AC-07 | Ranking drill | Seeded friction (provider outage on one rail; confusing coupon error) vs anecdote-heavy low-impact issues | Backlog ranks | First and second by revenue-at-stake with correct baselines; low-impact issues rank low; correlation labeled |
| AC-08 | Segment drill | Sub-floor cuts | Readouts render | Collapse to "other" in every readout; no segment re-identifies buyers (minimum-bucket review gate) |
| AC-09 | Playbook drill | Each named hesitation pattern; a refresh-storm spike during an incident | Links evaluated | Every pattern links its remediation; the spike auto-suggests the confirming-UX fix |
| AC-10 | Correlation-discipline drill | A readout claiming causation without an experiment | Review checklist runs | Causal language blocked; correlation label enforced |
| AC-11 | Price-sensitivity drill | Coupon-attempt and term-mix findings | Consumers audited | Feeds only aggregate experiments; no personalized treatment path exists (code + review gate) |
| AC-12 | Adoption drill | Seeded cohorts | Distributions build | Correct time-to-activated by cohort; non-adopters flagged pre-renewal with reason-visible bands |
| AC-13 | Lapse drill | Decaying-usage accounts; payment-expiry fixtures | Bands evaluate | Correct bands + contributing reasons; scores provably unread by gating/pricing/fraud code paths (dependency + review gate) |
| AC-14 | Cancel drill | A cancelling buyer answering nothing; save offers | Flow runs | Cancel completes same-session regardless; one-click skip measured; save offers only as registered experiments with honest copy |
| AC-15 | Win-back drill | Outreach across cohorts/offers/recency; consent-declined buyers | Results cut | Response reported by cohort/offer/recency; consent-declined buyers provably excluded from every send |
| AC-16 | Trial drill | Activated vs dormant trials | Curves + bands report | Engagement-banded conversion correct; dormant-rescue messaging feeds the trial experiment backlog |
