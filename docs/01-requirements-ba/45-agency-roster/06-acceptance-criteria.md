# Agency Roster - Acceptance Criteria (feature 45)

Phase 1 BA · BDD; drills run against `docs/02-architecture-techlead/45-agency-roster/` implementations.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Invite drill | A username invite, expired/used token, and an unknown username | Accept or signup runs | Valid invites link with the exact role; expired/used tokens refused with zero state change; unknown-username flows convert on signup without duplicate rows |
| AC-02 | Matrix drill | The full role × endpoint fixture (owner/manager/member/client) | Every endpoint is exercised, including a member→owner escalation attempt | Matrix green; escalation 403 + audit-flagged; UI-hidden buttons never substitute for server checks |
| AC-03 | Effect drill | Removed member, unlinked client, suspended member | The next request runs | Reporting + Act-as lost; stale sessions fail closed within TTL; suspension freezes powers across all surfaces |
| AC-04 | Client-link drill | Staff-initiated link; either-side unlink | Acceptance and unlink run | Staff links pend until owner accepts; unlinks sever immediately; profile `user_id` never changes |
| AC-05 | Act gate drill | Unassigned profile, revoked access, stale step-up, suspended membership | Start is attempted | 403 naming the condition; zero sessions created; conditions logged |
| AC-06 | Act scope drill | A live session; allowlisted edits vs account-level attempts vs stripped marker | Requests run | Allowlisted edits succeed; account-level attempts 403 + audit-flag; stripped-marker replays fail authentication |
| AC-07 | Act audit drill | A fixture editing session | Replayed from `audit_log` | Actions match action-for-action with before/after; client history view matches support view; append-only holds at DB grants |
| AC-08 | Revocation drill | Client revoke; complaint freeze | Live sessions and new starts run | Revoke ends live sessions within seconds (in-flight fails closed); freeze blocks new starts while read surfaces continue |
| AC-09 | Expiry drill | Idle session; long-active session | TTLs elapse | Idle dies at 30 min; active hard-stops at 8 h requiring fresh step-up; presence lock releases with the session |
| AC-10 | Concurrency drill | A staffer with 3 live sessions starting a 4th; two staffers on one profile | Starts run | 4th refused (cap); second staffer gets `presence_locked`; no interleaved edits exist |
| AC-11 | Org lifecycle drill | Typed-confirm delete; restore within grace; post-grace state | Each runs | Delete suspends + audits; restore returns the org intact; past grace hard-deletes org rows with client profiles untouched |
| AC-12 | Seat seam drill | A staff invite without an active agency subscription | Invite is attempted | 409 `agency_subscription_required`; client invites unaffected; after subscription, invites proceed |
