# Sales Fees and Profit Calculator - Step-by-Step Implementation Plan (feature 26)

Phase 2 TL · Dependency-ordered; acceptance goals cite `docs/01-requirements-ba/26-shop-fees-and-calculator/06-acceptance-criteria.md`.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `V5__shop_fees_and_calculator.sql` + RLS + grants + dual-control CHECK; `pinned_rate_version` column | Feature 25 T-01 | TL 02 drills 1-6 green |
| T-02 | BE | Rate-version lifecycle routes (draft validation, dual-control approve, publish) + audit + public schedule read + 60 s cache + pending-state refusals | T-01 | AC-11, AC-12 (refusal half) green |
| T-03 | BE | Pure quote function: integer bp math, both rounding rules, overflow guards, free/tip/linkout rules; `fees/quote` + internal seam | T-01 | AC-01, AC-05, AC-06 green |
| T-04 | BE | Cost table ops route + audit; merch-quote `cost_unknown` path | T-01 | AC-01 (cost half) green |
| T-05 | BE | Calculator endpoint: forward/reverse, line decomposition, tier comparison, break-even with plan-price seam (pending when unpriced) | T-03, T-04 | AC-06, AC-09 green |
| T-06 | BE | Scenarios (append-only versions, requote diff) + publish-price (pinned version, below-cost block) | T-05 | AC-07, AC-08 green |
| T-07 | FE | Schedule page, calculator UI (mode toggle, decomposition labels), tier comparison panel, scenario list + requote banner, dashboard decomposition trio, checkout seller-view lines | T-02..T-06 | BA 04 states render; AC-02, AC-04 UI halves |
| T-08 | DevOps/QA | Identity fixtures (labeled, test DBs), CI drift test (schedule/checkout/dashboard), grep gate (zero literals), release gate (checkout fee-snapshot enablement requires a published version), container drill (Docker + Podman; V4→V5 replay; rollback) | T-02..T-07 | AC-01..AC-04, AC-12 green; G4 + G5 + G7 |

## Notes

- Build-order gate: checkout snapshot + reconciliation enable when 32-40 ships them; break-even renders "pending" until 36 provides plan prices — nothing is stubbed.
- No-invention is structural, not aspirational: no rate literal exists in the codebase defaults, env templates, or seed data; only the dual-control config publishes values.
- Historical fidelity: a closed period is reproducible from the order's pinned `rate_version` + the same function (support replay drill).
- Display drift test covers the surfaces shipping now; when the marketing pricing page (48-53) lands, it consumes `GET /api/v1/fees/schedule` and joins the same test.
- Scenario purge rides the standard account-deletion participant; config tables have no user dimension to purge.
