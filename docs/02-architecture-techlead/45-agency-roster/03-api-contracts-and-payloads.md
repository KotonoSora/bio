# Agency Roster - API Contracts and Payloads (feature 45)

Phase 2 TL · Roster + Act-as-user + internal seat contracts. Auth/rate rules: `docs/02-architecture-techlead/45-agency-roster/05-security-logging-and-rate-limiting.md`. Problems follow RFC 9457.

## Endpoints

| Method & path | Auth | Request | Success | Errors (code) |
|---|---|---|---|---|
| `POST /api/v1/orgs` | session + CSRF | `OrgCreateRequest` | `201 OrgView` | 422 `validation_failed`; 429 |
| `GET /api/v1/orgs` | session | - | `200 List<OrgView>` (own memberships) | 401; 429 |
| `PATCH /api/v1/orgs/{id}` | session + CSRF (owner/manager) | `{name?, logoKey?, contactEmail?}` | `200 OrgView` | 403 `role_denied`; 404; 429 |
| `DELETE /api/v1/orgs/{id}` | session + CSRF (owner) | `{confirmName}` | `200 {status: suspended_deleted, deleteAfter}` | 403; 409 `confirm_mismatch`; 429 |
| `POST /api/v1/orgs/{id}/restore` | session + CSRF (owner) | - | `200 OrgView` | 403; 409 `grace_expired`; 429 |
| `POST /api/v1/orgs/{id}/invites` | session + CSRF (owner/manager) | `InviteCreateRequest` | `201 InviteView` | 403; 409 `invite_conflict`, `agency_subscription_required` (staff roles); 422; 429 |
| `POST /api/v1/org-invites/accept` | session + CSRF | `InviteAcceptRequest` | `200 OrgView` (membership linked) | 422 `invite_invalid`, `token_invalid`; 429 |
| `POST /api/v1/org-invites/decline` | session + CSRF | `InviteAcceptRequest` | `200 {status: declined}` | 422; 429 |
| `POST /api/v1/orgs/{id}/invites/{inviteId}/revoke` | session + CSRF (owner/manager) | - | `200 InviteView` | 403; 404; 429 |
| `PATCH /api/v1/orgs/{id}/members/{userId}` | session + CSRF (matrix) | `{role}` | `200 MemberView` | 403 `role_denied`, `owner_immutable`; 404; 429 |
| `POST /api/v1/orgs/{id}/members/{userId}/suspend`, `/reinstate` | session + CSRF (matrix) | - | `200 MemberView` | 403; 404; 429 |
| `DELETE /api/v1/orgs/{id}/members/{userId}` | session + CSRF (matrix) | - | `200 {status: removed}` | 403 `owner_immutable`; 404; 429 |
| `POST /api/v1/orgs/{id}/clients` | session + CSRF (staff) | `ClientLinkRequest` | `201 ClientLinkView` (`pending`) | 403; 409 `link_conflict`; 429 |
| `POST /api/v1/orgs/{id}/clients/{clientId}/accept`, `/unlink` | session + CSRF (owner side / either side) | - | `200 ClientLinkView` | 403; 404; 429 |
| `POST /api/v1/orgs/{id}/clients/{clientId}/assign`, `/unassign` | session + CSRF (owner/manager) | `{userId}` | `200 {assigned: Boolean}` | 403; 404; 422; 429 |
| `POST /api/v1/agency/act/start` | session + CSRF + step-up | `ActStartRequest` | `200 ActSessionView` (act-as token minted) | 403 `act_gate_failed`, `step_up_required`; 409 `presence_locked`; 429 |
| `POST /api/v1/agency/act/end` | act-as session | - | `200 {endedAt}` | 401; 429 |
| `GET /api/v1/agency/act/history?profileId=&actorId=&cursor=` | session (client/owner/support views) | - | `200 ActHistoryPage` | 403; 429 |
| `POST /api/v1/agency/act/revoke` | session + CSRF (client) | `{profileId}` | `200 {endedSessions: Int}` | 404; 429 |
| `POST /api/v1/orgs/{id}/act-suspend` | session + CSRF (owner/manager) | `{reason}` | `200 {endedSessions: Int}` | 403; 429 |
| `GET /internal/v1/agency/seats?cursor=` | service JWT | - | `200 SeatPage` (per-org counts) | 401 |
| `GET /internal/v1/agency/access?orgId=&userId=` | service JWT | - | `200 AccessView` (role + linked/assigned profileIds) | 401 |

```kotlin
data class OrgCreateRequest(val name: String)
data class OrgView(val id: String, val name: String, val role: String, val status: String)
data class InviteCreateRequest(val username: String, val role: String, val clientScope: List<String> = emptyList())
data class InviteAcceptRequest(val token: String)
data class InviteView(val id: String, val username: String, val role: String, val status: String, val expiresAt: Instant)
data class MemberView(val userId: String, val role: String, val status: String)
data class ClientLinkRequest(val profileId: String)
data class ClientLinkView(val id: String, val profileId: String, val status: String)
data class ActStartRequest(val profileId: String)
data class ActSessionView(val id: String, val profileId: String, val expiresAt: Instant, val hardStopAt: Instant)
data class ActHistoryRow(val sessionId: String, val actorUserId: String, val action: String,
  val at: Instant, val beforeRef: String?, val afterRef: String?)
data class SeatRow(val orgId: String, val seats: Int, val byRole: Map<String, Int>)
data class AccessView(val role: String, val status: String, val profileIds: List<String>)
```

## Events (account-service stream `account.events`)

| Event | Payload | Emitted by | Consumers |
|---|---|---|---|
| `org.membership_changed.v1` | `{orgId, userId, role, status, change}` | membership transitions | 47 (reporting scope refresh) |
| `org.client_link_changed.v1` | `{orgId, profileId, userId, status}` | link/unlink | 47 (cache purge + scope) |

Post-commit, at-least-once, idempotent consumers (baseline 03).

## Rules

- Every roster endpoint re-checks membership + role server-side (`RoleMatrix`); UI never gates; escalation attempts 403 + audit-flagged; owner row immutable to non-owners.
- Invites: token shown once, hash at rest; accept is single-use + expiry-checked in one transaction; staff-role invites check the agency entitlement (`GET /internal/v1/billing/agency/entitlement?orgId=`, 46) and refuse `agency_subscription_required` without it; clients never require seats.
- Act-as start: all gates (membership, link, assignment, no revoke, agency entitlement active (46), step-up ≤ 10 min) → presence lock via the partial unique index → session row + act-as token (distinct issuer, claims `{act_as:true, actor, org, profile}`); any gate failure names the condition.
- Act-as tokens: 30-min idle TTL sliding with activity to an 8-h hard stop; downstream services enforce the per-endpoint-class allowlist; account-level classes deny by default (baseline 08).
- Revocations: client revoke ends live sessions ≤ 5 s + unlink; org-wide act-suspend ends staff sessions; in-flight act-as mutations fail closed per request.
- Internal routes: seats (46) + access (47) — service-JWT only; seat counts computed from roster state (active owner+manager+member; invited/suspended excluded); access returns `{role, status, profileIds[]}` (owner/manager all linked, member assigned, client own) for 47's guard.
- Org delete/restore: typed confirm; restore within 30-day grace; past grace hard-deletes org rows; client profiles untouched.

## Error catalog additions

| Code | Status | UI Behavior |
|---|---|---|
| `invite_invalid` | 422 | Expired/used/revoked reason class |
| `invite_conflict` | 409 | Pending invite exists |
| `token_invalid` | 422 | Neutral invalid-link page |
| `role_denied` | 403 | Matrix explanation; escalation audit-flagged |
| `owner_immutable` | 403 | Transfer-first copy |
| `step_up_required` | 403 | Fresh login prompt |
| `act_gate_failed` | 403 | Condition-specific copy |
| `presence_locked` | 409 | "Another staff member is editing." |
| `agency_subscription_required` | 409 | Subscribe path (46) |
| `link_conflict` | 409 | Existing link state shown |
| `confirm_mismatch` | 409 | Typed name mismatch |
| `grace_expired` | 409 | Restore window closed |

Reused: `throttled`, `validation_failed`.
