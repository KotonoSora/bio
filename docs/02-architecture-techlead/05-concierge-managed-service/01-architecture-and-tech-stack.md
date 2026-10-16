# Concierge Managed Service - Architecture and Tech Stack (feature 05)

Phase 2 TL · Service: `account-service` extension (`account_db` migration `V3`; no new service, no new container). Implements `docs/01-requirements-ba/05-concierge-managed-service/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | The registry (contracts, tasks, approvals, staff, SOW templates) lives in `account-service`; the client surfaces are dashboard routes there. | Low sales volume and staffed operations need records + gates, not a new service; the extraction triggers (independent load/team/release cadence) are not met. |
| D-2 | Execution is exclusively through agency mechanics (features 45-47: assignment, Act-as-user, step-up, audit); this feature stores work references, never artifacts or a second execution path. | One powers model, one audit trail; the no-backdoor invariant (BR-02) is structural. |
| D-3 | Dispatch gating is a field contract: concierge-origin dispatch requests carry `origin=concierge` + `approvalRef`; dispatch endpoints (features 38-44) verify via the internal approval-check route, fail-closed. | Approval enforcement lives at the dispatch endpoint, not operator discipline; registered now, implemented by the consuming features. |
| D-4 | Pause/exit are dual-confirmed via a pending transition on the contract row + a counterpart confirm route; the initiator can cancel. | Two officers, one state machine; no lost transitions (durable columns, not chat). |
| D-5 | Staffing presence is data: `concierge_staff` + `concierge_sow_templates`; activation runs a presence query. | BR-08 as a check, not copy; auditable who/when. |
| D-6 | One state event `concierge.contract_state_changed.v1` covers activation, pause, exit. | Invoicing (features 32-40), scheduler halts, and handoff tooling all consume one payload. |
| D-7 | Retainer-only: no `trial` state, no seat billing, no money math here. DEVIATION FLAGGED: the legacy status enum included `trial`; Q-02 resolved retainer-only (a pilot motion needs a flagged amendment). | Q-02 resolution; invoicing stays in features 32-40. |

DEVIATION CARRIED: legacy `PLAN` P0–P5 labels are replaced by the batch order (`MIGRATION.md`); legacy Cloudflare Workers/Pages wording is superseded by the baseline microservice stack (in force since the baseline batch).

- Org clients: legacy `org/client_ref` resolves to the client account (`user_id`) in v1; org-owned contracts arrive with features 45-47 and require a flagged amendment - no org tables are invented here.

## Boundaries

`account-service` owns: contract/task/approval/staff/template rows, dual-confirm transitions, SLA timestamps, the approval-check internal route, the state event. It does not own: agency powers (features 45-47), dispatch execution (features 38-44), invoicing (features 32-40), reports content (their features - referenced by `work_refs`), or legal/compliance advice (referred out).

## Consumers

| Consumer | Contract |
|---|---|
| Features 38-44 (dispatch endpoints) | `origin=concierge` + `approvalRef` field contract; `GET /internal/v1/concierge/approvals/{id}` verify, fail-closed |
| Features 32-40 (invoicing) | `concierge.contract_state_changed.v1`: `active` -> retainer invoice; `exited` -> stop; credits via standard receipts |
| Features 45-47 (agency tooling) | Operator assignment + Act-as-user; client history reads; handoff runbook surfaces |
| Feature 02 (purge) | Participant contract deletes concierge rows for the client account (minimized retention caveat in TL 02) |

## Stack notes

Spring Boot `4.1.1` / Kotlin `2.4.21` unchanged. New internals: `ContractService` (lifecycle + dual-confirm), `TaskBoardService` (transitions + SLA timestamps), `ApprovalService` (append-only records + signed-email link verify), `StaffingGate` (presence query). Signed-email confirmations use short-lived signed links (JWT, 15 min, single-use jti in Redis db 7 - account-service's). No new containers; `V3__concierge.sql` applies on boot.
