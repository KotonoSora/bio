# Resume Templates - Step-by-Step Implementation Plan (feature 18)

Phase 2 TL · Dependency-ordered chain; each task's acceptance goal gates the next. Criteria cited from `docs/01-requirements-ba/18-resume-templates/06-acceptance-criteria.md`.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `TemplateRegistry` + boot validation (unique ids; every token consumed) + `GET /api/v1/resume-templates` | Feature 17 T-01 | TL 02 drills 2, 3; AC-07 (read half) green |
| T-02 | BE | Selection validation on feature 17's PUT: registry check + premium gate (snapshot, fail-closed) + `resume.cv_changed.v1` on switch + audit | T-01 | AC-07 (write half), AC-08 green |
| T-03 | FE | Screen renderers for the three launch templates + gallery strip + picker integration (unsaved-preview pill, save flow) | T-01 | AC-11 green; gallery drill green |
| T-04 | FE | Print trees per template + A4 CSS (`print-color-adjust: exact`, page-break rules) + Chrome/Safari fixtures + bind-at-open + re-open notice | T-03 | AC-02, AC-03 green |
| T-05 | BE | cv text-parity layout cues: public-site applies `data-template` + density classes from the published payload (feature 10 edit) | T-01 | AC-04 green; parity diff clean |
| T-06 | DevOps | Thumbnail pipeline (fixture-only typed inputs, placeholder content, `<= 100 KB`, fingerprinting) | T-03 | AC-09 green |
| T-07 | DevOps/QA | Gates + drills: CI consistency gate (registry <-> bundle), server no-PDF grep, client no-PDF grep, zero-network print path, AC-01..AC-11 wiring | T-02..T-06 | G5 probes + G7 spot-check green |

## Notes

- Launch registry: three free templates (single-column default `classic` + two variants, Q-01); density/zoom are view-only (Q-05); premium mechanism ships dormant (Q-03).
- Print correctness is fixture-gated per template per release (NFR-09); a template without archived Chrome + Safari fixtures cannot ship.
- The print path issuing zero network calls is asserted in the client test harness (print hook spy), not just by review.
- Rollout order: T-01..T-02 dark (no FE), T-03 first user-visible, T-04/T-06 gate the template release, T-05 rides the next public-site deploy.
- Portability (feature 21) reads `templateId` as plain text; export/restore never validates against the registry (import keeps unknown ids + read fallback handles rendering).
- R1 delivery plan folded (legacy execution plan, file deleted with this batch): recorded split trigger - top-slug pre-render/ISR extracts at P5 scale-out on measured cache-hit collapse, never before; the bundle grep also rejects Node-only/headless-browser imports alongside the no-PDF gates (T-07).
