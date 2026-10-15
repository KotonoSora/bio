# Retargeting Reach Gated Frontend Plan (segments + flights + lift — buildable at plan update)

PLAN ref: none — P4-growth, GATED. BA source: `docs/monetize/advertising/retargeting-reach.md`. Server truth in the retargeting backend plan above (evaluator, eligibility, lift — never redefined here).

## Purpose

Hold the buildable segment/flight UX: versioned definitions with live sizes, consent-labeled targeting, fatigue visibility, and holdout lift reports. Nothing here renders before the plan update.

## Users

- Frontend developer (future): builds segment builder + flight + lift screens.
- QA (now): asserts zero retargeting UI exists; (future) consent/fatigue/lift drills.
- Creator/advertiser (future): builds lawful segments, reads honest lift.

## Functional requirements

1. Surfaces (future): segment builder (event-set picker from the closed v1 set, lookback select, cap display, consent-basis label per segment — shown before launch, not buried); live size preview (`≈N members, as of HH:MM` — exact-ish labeled); flight launcher (surface select onsite/email, control-pct slider with 10% default, fatigue preview); lift report (size/reach/conversion/suppression vs control).
2. Step 1 — builder flow: pick events → lookback → cap shown → consent basis auto-labeled (lifecycle vs marketing — the label determines which sends are legal downstream, stated at build time so creators don't design unusable segments) → preview size → save versioned definition (edits version, never mutate — running meaning frozen, stated in-UI).
3. Step 2 — flight flow: segment → surface → control slider (minimum enforced — control-less launch blocked client-side AND server-side) → launch → live status with reach/suppression counters → pause anytime (immediate, stated).
4. Step 3 — consent visibility: each segment/flight shows its consent basis + what it permits (`Lifecycle reminders only` vs `Promo eligible`) — opted-out-majority segments display the restriction before spend, not after disappointment.
5. Step 4 — fatigue UX: per-segment caps visible with consumption bars; exclusion segments listed (past buyers excluded by default, removable with confirm); every message preview shows its cause line + stop link (template fidelity proven in preview, not promised).
6. Step 5 — lift honesty: reports lead with the control comparison (lift vs control, never vs zero); control-less flights show `No lift claim available` instead of a number (absence of evidence rendered as absence, not zero).

## Data / API touches

- Screens → endpoints map (all GATED, absent now): builder → segment CRUD + preview-size; flights → launch/pause; reports → lift report. Session cookie + CSRF.

## Acceptance criteria

- Absence drill (now): zero retargeting UI and routes.
- Builder drill (at build): closed event set only; consent auto-labeled; edits version.
- Flight drill (at build): control minimum enforced both layers; pause immediate.
- Consent drill (at build): restriction visible pre-spend; refusal reasons logged.
- Lift drill (at build): control comparison leads; control-less shows absence-not-zero.

## Out of scope

- Evaluator/flights/lift logic (backend plan above); segment contracts (database plan above); inventory/counting/earnings; broadcasts/consent classes; leads; third-party tracking.

## Open questions

- Q1: Size preview cadence — live-ish or snapshotted per evaluation tick?
- Q2: Cause-line templates — fixed platform copy or creator-editable within bounds?
