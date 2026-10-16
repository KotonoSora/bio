# Agency Roster - Exception Handling and Errors (feature 45)

Phase 1 BA · Problems follow RFC 9457 (`code` field). Invite tokens and session material never appear in messages or logs.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Expired/used/revoked invite token | API | 422 `invite_invalid` with the reason class; zero state change | "This invite expired — ask for a new one." |
| Duplicate pending invite for a username | API | 409 `invite_conflict`; re-invite rate-limited | "An invite is already pending for {username}." |
| Unknown username | Flow | Invite-to-register; converts on signup without duplicates | "We'll email {username} an invite to join." |
| Manager attempts owner role or billing change | API | 403 `role_denied`; escalation attempt audit-flagged | "Only the owner can do that." |
| Attempt to remove/demote the owner | API | 403 `owner_immutable` | "The owner can't be removed — transfer ownership first." |
| Staff invite without an active agency subscription | API | 409 `agency_subscription_required` (46's entitlement read) | "Subscribe to add staff seats." |
| Act-as start: gate failure (unassigned, revoked, stale step-up, suspended) | API | 403 `act_gate_failed` naming the condition; logged; zero session | Condition-specific copy (e.g., "Take a fresh login to continue.") |
| Act-as start while another session is active on the profile | API | 409 `presence_locked` | "Another staff member is editing this profile." |
| Act-as attempt at an account-level endpoint | API | 403 + audit-flag (allowlist enforcement) | "That action isn't available in Act-as mode." |
| Act-as request with stripped/tampered marker | API | Fails authentication entirely (distinct issuer; validators never confuse classes) | Sign-in prompt |
| Client revoke mid-session | System | Live sessions die instantly; in-flight mutation fails closed; unlink per rules | Staff: "Access was revoked by the client." |
| Org-wide act-suspend | System | All staff sessions killed; new starts refused | Staff: "Agency editing is suspended." |
| Complaint freeze | System | Implicated staffer's act-as frozen; read surfaces continue | Staff: "Editing paused pending review." |
| Org delete within grace | API | Org suspended (typed confirm); restore available 30 days | "Deleted — restore within 30 days." |
| Org delete past grace | System | Hard delete of org rows; member/client data per retention decision; profiles untouched | None (gone) |
| Stale-session request after removal/unlink | System | Fails closed within token TTL (per-request check) | Staff: access lost |
| Invite flood | Gateway | Rate limit | 429 "Too many invites — wait." |
| Cross-org access attempt | API | 403/404 by RLS + per-request scoping; zero rows | "Not found." |
