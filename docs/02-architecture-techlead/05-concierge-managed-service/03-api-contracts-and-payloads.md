# Concierge Managed Service - API Contracts and Payloads (feature 05)

Phase 2 TL · Client + internal REST contracts. Auth/rate rules: `docs/02-architecture-techlead/05-concierge-managed-service/05-security-logging-and-rate-limiting.md`. JSON only; problems follow RFC 9457.

## Client endpoints (dashboard class, owner-scoped)

| Method & path | Auth | Success | Errors (code) |
|---|---|---|---|
| `GET /api/v1/concierge` | session | 200 `ContractResponse` (current engagement, any state — exited history retained per BR-10/BR-11; `null` when none) | 401 |
| `GET /api/v1/concierge/tasks` | session | 200 list of `TaskResponse` | 401 |
| `POST /api/v1/concierge/tasks` | session + CSRF | 201 `TaskResponse` (`requested`) | 404 `contract_not_found`; 409 `transition_conflict`; 422 `task_off_menu`; 429 |
| `POST /api/v1/concierge/tasks/{id}/approve` | session + CSRF | 200 `ApprovalResponse` (re-approve returns the existing record) | 404 `task_not_found`; 409 `transition_conflict`; 429 |
| `POST /api/v1/concierge/approve` | session + CSRF | 200 `ApprovalResponse` — setup plan (draft only; re-approve returns the record) | 404; 409 `transition_conflict` |
| `POST /api/v1/concierge/pause` | session + CSRF | 202 `ContractResponse` (`pendingTransition=pause`); 200 when confirming the agency's request | 404; 409 `transition_conflict` |
| `POST /api/v1/concierge/exit` | session + CSRF | 202 `ContractResponse` (`pendingTransition=exit`); 200 when confirming the agency's request (`ExitDecisionRequest`: `scheduledWork` + `acknowledge`) | 404; 409; 422 |
| `DELETE /api/v1/concierge/transition` | session + CSRF | 204 (initiator cancels pending) | 404 |

`user_id` is server-set; SOW scope + volume checks run before any task row is written (BR-01, BR-12).

## Internal endpoints

| Method & path | Auth | Success | Errors |
|---|---|---|---|
| `POST /internal/v1/concierge/contracts` | service JWT, agency owner | 201 `ContractResponse` (`draft`) | 422 |
| `PATCH /internal/v1/concierge/contracts/{id}` | service JWT, agency owner | 200 `ContractResponse` (activate / confirm pause, resume, exit / update draft SOW+tier) | 404; 409 `transition_conflict`; 422 `staffing_incomplete` |
| `GET /internal/v1/concierge/contracts` | service JWT, ops | 200 list (assigned/owner view) | 401 |
| `POST /internal/v1/concierge/tasks` | service JWT, assigned operator (`scheduled` origin; `userId` optional — blank resolves the single live contract) | 201 `TaskResponse` | 403 `task_forbidden`; 401 |
| `POST /internal/v1/concierge/tasks/{id}/status` | service JWT, assigned operator | 200 `TaskResponse` (`in_progress`/`done`; `workRefs` at done) | 403 `task_forbidden`; 409 `transition_conflict` |
| `POST /internal/v1/concierge/tasks/{id}/refuse` | service JWT, operator/owner | 200 `TaskResponse` (`refused` + reason) | 403; 409 |
| `POST /internal/v1/concierge/staff` | service JWT, agency owner | 201 | 422 |
| `DELETE /internal/v1/concierge/staff/{id}` | service JWT, agency owner | 204 | 404; 409 `last_owner_forbidden` |
| `POST /internal/v1/concierge/templates` (and `PATCH .../{id}`) | service JWT, agency owner | 201 / 200 | 422 |
| `GET /internal/v1/concierge/staff` / `GET .../templates` | service JWT, ops | 200 list | 401 |
| `POST /internal/v1/concierge/approvals/signed-email-link` | service JWT, agency owner | 201 `{token, expiresAt}` (15-min TTL, single-use) | 403; 404; 409 |
| `POST /internal/v1/concierge/approvals/signed-email` | service JWT, account-service callback | 200 `ApprovalResponse` | 422 `signature_invalid` |
| `GET /internal/v1/concierge/approvals/{id}` | service JWT | 200 `ApprovalCheckResponse` | 404 |

## Kotlin DTOs (source of truth for the SPA type mirror)

```kotlin
data class ContractResponse(val id: String, val status: String, val slaTier: String, val sow: List<SowItem>,
    val pendingTransition: String? = null, val activatedAt: java.time.Instant? = null, val exitedAt: java.time.Instant? = null,
    // ADDITIVE (BA 04/BA 05 surfaces; additive-only per governance below)
    val sowVersion: Int = 1, val pendingBy: String? = null, val sla: SlaTierView? = null,
    val handoff: HandoffView? = null, val setupApproval: ApprovalResponse? = null)
data class SlaTierView(val tier: String, val responseWindowMinutes: Long?, val completionWindowMinutes: Long?, val creditRule: String)
data class HandoffView(val startedAt: java.time.Instant?, val scheduledWork: String?, val items: List<HandoffItemView>, val completedAt: java.time.Instant?)
data class HandoffItemView(val key: String, val at: java.time.Instant?)
data class SowItem(val kind: String, val volume: Int? = null)
data class TaskResponse(val id: String, val kind: String, val status: String, val origin: String,
    val requestedAt: java.time.Instant, val approvedAt: java.time.Instant? = null,
    val completedAt: java.time.Instant? = null, val workRefs: List<String> = emptyList(), val refusalReason: String? = null,
    val notes: String? = null, val referral: String? = null, val operatorName: String? = null,
    val slaMiss: String? = null, val approvals: List<ApprovalResponse> = emptyList())
data class ApproveTaskRequest(val scope: String = "")
data class ExitDecisionRequest(val scheduledWork: String? = null, val acknowledge: String? = null)
data class ApprovalResponse(val id: String, val taskId: String? = null, val scope: String, val channel: String, val approvedAt: java.time.Instant)
data class ApprovalCheckResponse(val valid: Boolean, val taskId: String? = null, val scope: String? = null, val contractStatus: String? = null)
```

ADDITIVE fields realize the BA 04 rows (SOW version, setup-plan approval, handoff, work log) and BA 05 SLA/refusal surfaces; nullable `taskId` carries contract-level approvals. Nulls are omitted on the wire — consumers treat absent as null.

## Code catalog

| `code` | HTTP | UI mapping |
|---|---|---|
| `contract_not_found` | 404 | empty board state |
| `task_not_found` | 404 | board refresh |
| `task_off_menu` | 422 | refusal copy + referral |
| `volume_exceeded` | 422 | over-volume copy + SOW update guidance |
| `task_forbidden` | 403 | operator assignment error |
| `transition_conflict` | 409 | state banner (paused/exited/pending) |
| `staffing_incomplete` | 422 | internal-facing only |
| `last_owner_forbidden` | 409 | internal-facing only |
| `signature_invalid` | 422 | resend signed link |
| `throttled` | 429 | cooldown text |

## Dispatch gate contract (normative, consumed by features 38-44)

- Concierge-origin dispatch requests carry `origin=concierge` + `approvalRef` (a `concierge_approvals.id`).
- The dispatch endpoint calls `GET /internal/v1/concierge/approvals/{id}` and requires `valid=true`, `contractStatus=active`, and scope match; any failure, timeout, or non-200 -> refuse dispatch (fail-closed). Cached `<= 30 s` per approval id; budget `<= 100 ms` p95 added.
- Approval records are append-only; superseding records replace validity by being newer - the check returns the latest record for the task.

## Events

| Event | Payload | Consumers |
|---|---|---|
| `concierge.contract_state_changed.v1` | `contractId, userId, state (active\|paused\|exited), slaTier, at` | Invoicing (features 32-40: retainer start/stop), schedulers (halt concierge-origin queued work on pause/exit), notices, handoff tooling |

After-commit, at-least-once, idempotent consumers; lost events self-heal via reads (`GET /api/v1/concierge`).

Governance: additive-only; codes rename-frozen; no money values in this surface (invoicing features own amounts).
