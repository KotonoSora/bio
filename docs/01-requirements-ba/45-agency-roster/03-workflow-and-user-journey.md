# Agency Roster - Workflow and User Journey (feature 45)

Phase 1 BA · W-1..W-5. Rules: `docs/01-requirements-ba/45-agency-roster/02-user-stories-and-business-rules.md`. Contracts: `docs/02-architecture-techlead/45-agency-roster/03-api-contracts-and-payloads.md`.

## W-1 Invite → accept

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Owner/manager | invites a username + role | staff invites check the agency subscription (46); token minted (hashed at rest, 7-day expiry); dashboard invite + transactional email | BR-02, BR-13 |
| 2 | System | unknown username | invite-to-register flow; converts to membership on signup — no shadow account | BR-02 |
| 3 | Invitee | accepts via token | single-use verification → membership row `active` with the exact role | BR-02, BR-03 |
| 4 | Invitee | declines | recorded; no nag loop | BR-02 |
| 5 | System | expired/used/revoked token | refused with the specific reason; zero state change | BR-02 |

## W-2 Role change / removal / suspension

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Owner/manager | changes a role within the matrix | server-side matrix check → row updated + audit before/after | BR-03, BR-10 |
| 2 | System | manager attempts owner/billing change | 403 `role_denied`; escalation attempt audit-flagged | BR-03 |
| 3 | Owner/manager | suspends a member | powers freeze instantly (all surfaces); reversible | BR-05 |
| 4 | Owner/manager | removes a member | membership `removed`; reporting + act-as lost on next request | BR-05 |
| 5 | System | stale session probes | fail closed within token TTL | BR-05 |

## W-3 Client link / unlink

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Staff | links a client profile | `org_clients` row `pending`; profile owner notified to accept | BR-04 |
| 2 | Profile owner | accepts | row `active`; reporting + act-as assignment possible | BR-04 |
| 3 | Either side | unlinks | row `unlinked`; reporting + act-as severed immediately; cache purge event | BR-04 |
| 4 | System | ownership check | profile `user_id` never changes (aggregation only) | BR-04, Q-05 |

## W-4 Act-as session lifecycle

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Staff | starts a session on an assigned profile | all gates checked (membership, link, assignment, no revoke, fresh step-up) → presence lock acquired → session row + act-as token | BR-07, BR-09 |
| 2 | System | any gate fails | 403 naming the condition; logged; zero session created | BR-07 |
| 3 | Staff | edits blocks/theme/products within scope | per-request allowlist; every mutation audited with actor + on-behalf-of + before/after | BR-08, BR-10 |
| 4 | Client | sees the presence indicator | "staff is editing" while active | BR-09 |
| 5 | Staff | ends the session (or TTL/hard stop) | session row closed with reason; token dead | BR-09 |
| 6 | Client | opens their staff-action history | sees who, when, what changed (before/after) | BR-10 |

## W-5 Revoke / complaint freeze

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Client | one-click revoke agency access | all live sessions on their profiles die instantly; unlink per W-3; in-flight mutations fail closed | BR-11 |
| 2 | Owner/manager | org-wide act-suspend | all staff act sessions killed; new starts refused until reinstated | BR-11 |
| 3 | Client | files a complaint | implicated staffer's act-as frozen pending review; read surfaces continue | BR-12 |
| 4 | System | velocity anomaly on starts | ops alert; sessions remain gate-checked | BR-12 |
