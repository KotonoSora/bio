# Concierge Managed Service - Step-by-Step Implementation Plan (feature 05)

Phase 2 TL · Dependency-ordered chain; each task's acceptance goal gates the next. Criteria cited from `docs/01-requirements-ba/05-concierge-managed-service/06-acceptance-criteria.md`.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `V3__concierge.sql`: five tables + RLS + append-only grants on `concierge_approvals` + indexes | Feature 01 T-01 (`account_db`) | TL 02 drills 1-3 green |
| T-02 | BE | Contract lifecycle: draft create, `StaffingGate` activation, pause/exit/resume dual-confirm (pending columns + counterpart confirm), initiator cancel | T-01 | AC-03 (pause half), AC-05 green |
| T-03 | BE | Task board: request (SOW scope + volume checks, `task_off_menu`), approve (immutable record + signed-email verify), operator transitions, refuse with reason | T-01 | AC-02 (record half), AC-07, AC-08 green |
| T-04 | BE | Staffing register + SOW template admin routes (owner role, last-owner guard) | T-01 | AC-05, template behavior green |
| T-05 | BE | Approval-check internal route + forward-contract registration edits for features 38-44 (their dispatch endpoints implement the check in their batches) | T-03 | AC-02 (dispatch half) green once consumers land |
| T-06 | BE | SLA clock: timestamps at each transition, tier config mapping (`responseWindow`, `completionWindow`, `creditRule` keys), miss surfacing on the board | T-03 | AC-04 green |
| T-07 | BE | State event + consumer registration (invoicing, schedulers, notices); signed-email link issuance (15 min, single-use) | T-02 | Activation/exit flows observable end-to-end |
| T-08 | FE | Client surfaces: contract card, SOW panel, board, approve dialog, work log, pause/exit dialogs with handoff checklist, refusal/SLA banners | T-02..T-07 | AC-01, AC-06 UI halves + AC-03 (handoff half) green |
| T-09 | DevOps/BE | Drills + gates: AC-01..AC-08 wired, rate envelopes, degrade checks (approval-check outage -> dispatch refuses) | T-05, T-08 | G5 probes + G7 spot-check green |

## Notes

- Activation is the sale gate: `StaffingGate` presence query refuses `422 staffing_incomplete` until >= 1 active template, >= 1 active operator, >= 1 agency owner exist - the AC-05 no-sale-before-staffing drill is a release gate for this feature's *go-to-market*, not for deploying the code (the routes can ship dark; nothing sells while unstaffed).
- Launch blockers owned outside this feature: Q-01 values (SLA windows, retainer price, credit rule) from the staffing/finance cost model; runbooks + named operators (features 45-47 staffing surfaces); the feature 02 purge participant row was updated in this change (concierge tables included).
- Execution primitives stay in features 38-44 (dispatch) and 45-47 (powers); this feature never grows an execution path - review guard.
- Rollout order: T-01..T-04 dark (no client surface), T-05/T-06 before any operator executes with clients, T-08 last; no client sees a board before the staffing gate answers.
