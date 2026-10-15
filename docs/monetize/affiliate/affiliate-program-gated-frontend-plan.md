# Affiliate Program Gated Frontend Plan (enrollment + self-service — buildable at plan update)

PLAN ref: none — P4-growth, GATED. BA source: `docs/monetize/affiliate/affiliate-program.md`. Server truth in the program backend plan above (manual approval, tiers, ladder — never redefined here).

## Purpose

Hold the buildable enrollment/self-service UX: application with honest manual-review expectations, tier visibility, terms acceptance, conduct-notice surfaces, and the appeal path. Nothing here renders before the plan update.

## Users

- Frontend developer (future): builds apply + self-service + appeal screens.
- QA (now): asserts zero affiliate UI exists; (future) enrollment/ladder drills.
- Affiliate (future): applies once, understands tiers and rules, appeals with records.

## Functional requirements

1. Surfaces (future): public `Apply as affiliate` (identity + payout details + channel description + terms version shown inline with scroll-to-accept); affiliate self-service (profile, links/codes with copy buttons, read-only earnings entry linking commission screens); status center (applied → under review → active|suspended|terminated with reasons); appeal composer (linked from every adverse notice).
2. Step 1 — application flow: form (validated inline) → explicit `Manual review takes N days` copy beside submit (expectation set before click — silence here breeds "where is my commission" tickets) → Turnstile → submit → `Application received` with reference id (trackable without exposing queue internals).
3. Step 2 — tier display flow: current tier + rate + volume progress to next tier (`$X more this month steps next month's rate to Y%` — next-month-only semantics rendered, so mid-month threshold crossings never look broken) + rate history (stamps per change with effective dates).
4. Step 3 — terms flow: terms version rendered full-text at apply (not linked-away); acceptance timestamped; material changes trigger in-app re-acceptance gate (blocks new attributions until accepted — serving impact stated before the button, not after).
5. Step 4 — adverse-action flow: suspension/termination notices show reason + evidence class + appeal window countdown + appeal composer entry (one click from the notice — appeal buried in support menus is denial by UX); forfeiture scope stated (`Only flagged earnings affected` vs full, per the backend ladder).
6. Step 5 — buyer-price honesty (affiliate-facing reassurance): self-service shows `Your buyers pay normal prices — commission comes from margin` (margin-funded rule visible to the people whose reputation rides on it).

## Data / API touches

- Screens → endpoints map (all GATED, absent now): apply → intake; self-service → profile/links/earnings reads; status/appeal → decision + appeal endpoints. Session cookie + CSRF; Turnstile on apply submit.

## Acceptance criteria

- Absence drill (now): zero affiliate UI and routes.
- Application drill (at build): review-time copy present; reference id issued; no auto-approval affordance exists.
- Tier drill (at build): next-month semantics rendered; rate history complete.
- Ladder drill (at build): notices carry reason + evidence class + countdown + one-click appeal.
- Security drill (at build): no rate-setting UI exists anywhere (grep); cross-affiliate views 403.

## Out of scope

- Enrollment/tier/ladder logic (backend plan above); table contracts (database plan above); link/code mechanics (tracking plans); computation/payouts (commission plans).

## Open questions

- Q1: Application review-time SLA display — fixed N days or dynamic queue estimate?
- Q2: Appeal composer in-app vs support-ticket handoff?
