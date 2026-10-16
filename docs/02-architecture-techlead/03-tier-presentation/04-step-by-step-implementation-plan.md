# Tier Presentation - Step-by-Step Implementation Plan (feature 03)

Phase 2 TL · Dependency-ordered chain; each task's acceptance goal gates the next. Criteria cited from `docs/01-requirements-ba/03-tier-presentation/06-acceptance-criteria.md`.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `GET /api/v1/plan`: snapshot + optional subscription projection; absent fields pre-36; degrade to tier-only on projection timeout | Feature 01 T-11 | AC-04 green |
| T-02 | BE | Config keys `yearlyDiscountPct` / `trialDays` + boot validation (`<= 44`, integral VND math helper) + absence semantics | T-01 | AC-03, AC-09, NFR-05 green |
| T-03 | BE | `profiles.limit` registration in the capability map (free `"1"`, pro `"unlimited"`) + conformance test against feature 06's guard | Feature 01 T-11; feature 06 guard (T-04 of `docs/02-architecture-techlead/06-public-profile/`) | AC-05 green |
| T-04 | BE | `GET /api/v1/plans` payload conformance fixture + cache headers; contract frozen for the catalog implementer | T-02 | Contract test green; G4 clean |
| T-05 | FE | Pricing page: tier cards, term toggle, saving badge (absence-aware), trial CTA (absence-aware), registry-driven claims | T-02 | AC-01, AC-02, AC-09 green |
| T-06 | FE | Account plan panel: current tier, renewal row, trial row, cancel + change dialogs calling feature 36 endpoints (forward) | T-01 | AC-06, AC-08 UI halves green |
| T-07 | DevOps | Drift test: registry vs rendered copy; literal-number scan; extra-claim check; wired to CI | T-05 | AC-01, AC-02 enforced mechanically |
| T-08 | DevOps | Release artifacts: registry export + rendered snapshot archived at G7; diff vs previous tag | T-07 | NFR-08 green |

## Notes

- Forward couplings are registered, never stubbed: feature 36 supplies the subscription projection, cancellation, and trial mechanics; their fixtures attach when that batch migrates (AC-06/AC-07/AC-08 execution halves).
- The `profiles.limit` value flip is exercised end-to-end with the feature 01 snapshot fixture (free -> pro within 30 s) - this is also feature 06's second-profile seam drill.
- No new service, table, or container appears in this plan (TL 02's no-schema drill holds).
- Feature 06's architecture file already points the second-profile deferral at feature 03 entitlement; this change closes that pointer.
