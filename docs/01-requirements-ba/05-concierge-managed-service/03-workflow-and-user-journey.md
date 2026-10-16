# Concierge Managed Service - Workflow and User Journey (feature 05)

Phase 1 BA · Flows executed by `docs/02-architecture-techlead/05-concierge-managed-service/`. Rules cited as `BR-nn` from `docs/01-requirements-ba/05-concierge-managed-service/02-user-stories-and-business-rules.md`.

## Sale to activation

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Agency owner | Creates a draft contract (client, SOW menu items + volumes, SLA tier) | Draft recorded; SOW viewable by the client | BR-01 |
| 2 | Concierge client | Approves the setup plan + SOW | Immutable approval record (dashboard or signed email); scope recorded | BR-04 |
| 3 | Agency owner | Activates | Staffing presence check (>= 1 template, >= 1 operator, >= 1 owner); fail -> `422 staffing_incomplete`; pass -> `active` | BR-08 |
| 4 | System | Activation commits | `concierge.contract_state_changed.v1` (`active`); retainer invoicing (features 32-40); setup tasks seeded on the board | BR-09 |

## Task lifecycle

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Concierge client or owner | Requests/schedules an item (within SOW) | Task `requested`; off-menu or over-volume -> `refused` + referral | BR-01, BR-12 |
| 2 | Concierge client | Approves the item | Immutable approval (`approved`); scope recorded | BR-03, BR-04 |
| 3 | Concierge operator | Executes via agency-gated endpoints (Act-as-user, step-up) | `in_progress` -> `done` with work references; every action in the client's history | BR-02, BR-05 |
| 4 | Dispatch endpoint (send/publish) | Sees concierge-origin work | Verifies `approvalRef`; missing/invalid -> refuses dispatch | BR-03 |
| 5 | Agency owner | Reviews quality | Monthly review entry + report task on the board | BR-05, BR-06 |

## Pause

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Client or owner | Requests pause | `pending_transition=pause`; counterpart notified; work continues until confirmed | BR-10 |
| 2 | Counterpart | Confirms | `paused`; access powers suspend; queued concierge-origin work halts; content stays live | BR-10 |
| 3 | Same initiator | Cancels before confirm | Pending cleared; nothing changed | BR-10 |

## Exit and handoff

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Client or owner | Requests exit | `pending_transition=exit`; counterpart notified | BR-10 |
| 2 | Counterpart | Confirms | Handoff checklist starts: credentials inventory, content ownership confirmation, scheduled-work transfer-or-cancel (client choice), final report | BR-10 |
| 3 | Agency owner | Completes handoff | `exited`; agency links sever; queued concierge work canceled or transferred; credits path closes | BR-10, BR-11 |
| 4 | System | Exit commits | `concierge.contract_state_changed.v1` (`exited`); invoicing stops; records minimized per retention | BR-09, BR-11 |

## SLA miss

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System | Response or completion window elapses on a task | Miss surfaced on the board with the contract's credit-rule path | BR-06 |
| 2 | Agency owner | Applies the contract's credit rule | Credit recorded via the standard receipt pipeline (features 32-40) | BR-06, BR-09 |

## Boundary refusals

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Client or owner | Asks for anything off-boundary (billing instruments, policy promises, customer contact, prohibited content) | Refused on the spot; reason recorded; referral where applicable | BR-07 |
| 2 | Operator | Attempts a direct edit outside agency powers | Denied + audited; no state change | BR-02 |
