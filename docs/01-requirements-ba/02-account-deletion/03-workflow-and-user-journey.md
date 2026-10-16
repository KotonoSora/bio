# Account Deletion - Workflow and User Journey (feature 02)

Phase 1 BA · Flows executed by `docs/02-architecture-techlead/02-account-deletion/`. Rules cited as `BR-nn` from `docs/01-requirements-ba/02-account-deletion/02-user-stories-and-business-rules.md`.

## Request and deactivation

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Leaving user | Settings -> Delete account | Consequences screen (vanishes vs stays) + export-first offers; no dark pattern | BR-01 |
| 2 | Leaving user | Confirms | Step-up re-auth if older than 10 min, then typed confirmation (username or `DELETE`) | BR-01 |
| 3 | System | Request commits | Immediate deactivation: all sessions revoked; `deletion_requests` row `grace` with `purge_due_at` = +30 d; events emitted | BR-02, BR-11 |
| 4 | System | Fan-out | Profile surfaces resolve `missing` within `<= 60 s`; sitemap drops; cache purge attempted; schedulers cancel queued work; billing stops | BR-02 |
| 5 | System | Audit | Request event with actor + confirmation method; user lands on the status panel (countdown + purge date) | BR-12 |

## Grace, restore, and reminder

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System | Day 23 arrives | One transactional reminder email naming the purge date; bounce surfaced to support | BR-03 |
| 2 | Leaving user | Logs in during grace | Session works; restore screen shown; public surfaces stay hidden until confirm | BR-03 |
| 3 | Leaving user | Confirms restore | Status -> `cancelled`; unsuspend event; public surfaces return within `<= 60 s`; state byte-identical | BR-03 |
| 4 | System | Restore completes | Audit row; billing does NOT resume; fresh checkout required to resubscribe | BR-03 |

## Purge run (day 30)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System (job) | Daily run finds due accounts | Sets `purge_started_at`; calls every registered participant `POST /internal/v1/account/purge` (idempotent by `runId`) | BR-04, BR-09 |
| 2 | Participant services | Purge call arrives | Delete owner-scoped rows + objects; return per-table counts; no cross-user deletes | BR-05, BR-06 |
| 3 | System | All participants succeed | Emits certificate (counts, objects, run id), status -> `purged`; audit row | BR-08, BR-12 |
| 4 | System | Any participant fails | Status stays `grace` with `purge_started_at`; bounded retry next run; 24 h stuck -> page ops; no user-visible "purged" until true | BR-09 |
| 5 | System | Weekly | Residual probe per purged account; non-zero -> page ops with certificate diff | BR-10 |

## Re-registration and reuse

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Outsider | Claims the held username during grace | Rejected `username_taken` (profile suspended, not deleted) | BR-07 |
| 2 | Former user | Signs up after purge with the same IdP identity | New account, new user id; email accepted; username retired -> must pick a new one (`username_retired`) | BR-07 |
| 3 | Former user | Starts fresh | Zero history: no old grants, no resurrected consent or suppression rows | BR-07 |

## Support verification

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Support/ops | Ticket asks "is it gone?" | Reads deletion status + certificate through the audited route; no content access | BR-08, BR-11 |
| 2 | System | Read occurs | Audit row (actor, target, reason) | BR-12 |
