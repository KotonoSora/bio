# Account Deletion - Exception Handling and Errors (feature 02)

Phase 1 BA · Failure paths handled in `docs/02-architecture-techlead/02-account-deletion/06-security-logging-and-rate-limiting.md`. Codes are stable contract; problems follow RFC 9457.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Stale step-up at request | API | `403 step_up_required` (feature 01 code); nothing committed | "Confirm it's you to continue." |
| Typed confirmation mismatch | API | `422 confirmation_mismatch`; request not created | "Type your username exactly (or DELETE) to continue." |
| Request repeated during grace | API | Idempotent: `200` with current status; no duplicate row, no reset of `purge_due_at` | Status panel (no error) |
| Cancel without an active request | API | `409 delete_not_requested` | "There's nothing to cancel." |
| Cancel after purge completed | API | `410 purge_completed` (no session can exist post-purge; kept for ops calls and stale tabs) | "This account has already been deleted." |
| Participant timeout during purge | Job | Bounded retry next run; status stays `grace` with `purge_started_at`; 24 h stuck pages ops | None (user sees countdown extended honestly; support can explain) |
| Participant reports partial delete | Job | Counts recorded; retry idempotent by `runId`; certificate withheld | None user-visible |
| Certificate probe finds residual rows | Job | Pages ops with certificate diff; purge re-run for the account | None user-visible; support notified |
| Reminder email bounce | Job | Flag for support; reminder not silently dropped (retry once, then support) | None user-visible |
| Export link used after purge | UI | Export surfaces 404 (their data is gone); no partial file served | "That export is no longer available." |
| Username claimed during grace | API (profile) | `422 username_taken` (profile suspended, not deleted) | "That name is taken." |
| Username claimed after purge | API (profile) | `422 username_retired` | "That name was used before and stays reserved." |
| Legal hold active at purge | Job | Held evidence retained (documented subset); everything else purges; certificate notes the hold | None user-visible |
| Scheduler/billing consumer down | Fan-out | Event retried at-least-once; consumer self-heals via the 60 s status cache; no duplicate charges/cancels (idempotent keys) | None user-visible |
| Support attempts direct purge | Internal | Route absent for support roles; attempt logged as denied | n/a |
