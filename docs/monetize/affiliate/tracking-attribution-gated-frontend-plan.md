# Affiliate Tracking Attribution Gated Frontend Plan (affiliate stats — buildable at plan update)

PLAN ref: none — P4-growth, GATED. BA source: `docs/monetize/affiliate/tracking-attribution.md`. Server truth in the tracking backend plan above (precedence, windows, gaming filters — never redefined here).

## Purpose

Hold the buildable affiliate-stats UX: clicks/conversions/pending/confirmed visibility, evidence-linked credits, and PII-free aggregates. Nothing here renders before the plan update.

## Users

- Frontend developer (future): builds affiliate stats screens.
- QA (now): asserts zero tracking UI exists; (future) precedence-display/privacy drills.
- Affiliate (future): sees near-real-time performance with reproducible credits.

## Functional requirements

1. Surfaces (future): affiliate stats tab (clicks, conversions, pending/confirmed states, rate stamps, window countdowns per pending line) + evidence view per credit (click id, code?, timestamps — the reproducibility bundle, human-readable) + link/code manager (slug display, suffix rotation entry, copy buttons).
2. Step 1 — stats flow: `GET stats` → clicks/conversions/pending/confirmed with per-line rate stamps + window status (`12 days left in window` on pending lines — time-bounded hope, not open-ended expectation) → aggregates only, zero buyer PII anywhere (review asserts column absence in the stats payloads, not just hidden columns).
3. Step 2 — evidence flow: per-credit `Why this credited` entry → evidence bundle view (click, code if any, timestamps, precedence applied — e.g. `Code beat cookie per precedence rule`) → dispute entry point (pre-filled with the bundle id — disputes start assembled, not described).
4. Step 3 — link/code management flow: slug display (vanity-stable) + suffix rotation (`Rotate after leak` with `old links stop crediting` copy — rotation semantics stated before the button) + promo-code display for code-based attribution (codes consumed from the promo-code slice, shown here read-only).
5. Step 4 — declined-consent honesty: stats show unattributed-checkout counts in aggregate (`N checkouts unattributed (consent declined)` — the number exists so affiliates don't read unattributed volume as tracking failure).

## Data / API touches

- Screens → endpoints map (all GATED, absent now): stats tab → `GET stats`; evidence → bundle reads; link manager → code/rotation endpoints. Session cookie + CSRF.

## Acceptance criteria

- Absence drill (now): zero tracking UI and routes.
- Display drill (at build): window countdowns on pending lines; rate stamps per line; PII-free payloads (assertion, not review).
- Evidence drill (at build): every credit decomposes to its bundle with precedence stated.
- Rotation drill (at build): suffix rotation semantics stated pre-click; old links stop crediting.
- Privacy drill (at build): unattributed aggregate shown; zero buyer PII in any affiliate payload.

## Out of scope

- Precedence/window/gaming logic (backend plan above); click/attribution contracts (database plan above); enrollment/tiers (program plans); computation/payouts (commission plans).

## Open questions

- Q1: Near-real-time definition for stats — minutes cadence labeled, or tighter?
- Q2: Evidence bundle visibility — full bundle to affiliates, or redacted timestamps?
