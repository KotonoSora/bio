# Verification Badge and Follower Display - Workflow and User Journey (feature 07)

Phase 1 BA · Flows are normative; rules cite `02-user-stories-and-business-rules.md`, errors cite `05-exception-handling-and-errors.md`.

## Journey 1 - Apply for verification (creator)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Opens `/app/trust`; card shows `none` | `Request verification` entry point; no badge anywhere | BR-01 |
| 2 | SPA | Opens the evidence step, picks a file (PDF/PNG/JPEG, <= 10 MB) | Presign call -> direct-to-blob upload with progress -> returns key | BR-03 |
| 3 | SPA | Submits `POST /api/v1/profile/verify-request` with the key | `201` flips the card to `pending` timeline (submitted -> under review, no badge) | BR-02 |
| 4 | SPA | Polls `GET /api/v1/profile/verify-status` every 60 s while pending | Polling stops on any terminal state | - |
| 5 | Ops reviewer | Decides (approve/reject/revoke) with a reason | Status flips; audit row (actor + reason); purge event published | BR-05, BR-11 |
| 6 | SPA | Next poll renders `verified` / `rejected` (+ reason, re-apply) / `revoked` (notice) | Badge preview shows only for `verified` | BR-01 |

Alternative: second application while `pending` -> `409 verify_already_pending`, card collapses to the pending view (no duplicate forms).

## Journey 2 - Ops decision

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Ops reviewer | Opens the role-gated queue | Rows: profile, status, age, evidence link, history excerpt | BR-10 |
| 2 | Ops reviewer | Opens evidence | Sandboxed viewer via short-lived presigned URL (never inline) | BR-03 |
| 3 | Ops reviewer | Decides with mandatory reason; revoke requires typed confirmation | `200`; queue row updates; audit excerpt appended | BR-11 |
| 4 | System | Badge state changes | Public badge appears/removes within purge SLA (<= 2 min) | BR-05 |

## Journey 3 - Rename interplay (creator + system)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Completes a rename while `verified` | Rename hook sets status `pending` in the same transaction; badge hidden | BR-04 |
| 2 | SPA | Next `verify-status` poll | Card shows `Re-review required after rename - badge hidden meanwhile` | BR-04 |
| 3 | Ops reviewer | Re-approves | Badge returns; public page catches up <= 2 min | BR-05 |

## Journey 4 - Socials and follower totals (creator)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Edits rows (network, handle, URL, self-reported count) | Row-level validation on save | BR-09 |
| 2 | Profile service | `PUT /api/v1/profile/socials` | Validates, caps counts at 100,000,000, writes history row, recomputes total + breakdown; 10x jump -> `follower_jump_review` notice (count applied) | BR-06 |
| 3 | Creator | Presses refresh (max 6x/h) | Keyless server-side fetch of public counts; totals update; `429` shows cooldown | BR-07 |
| 4 | System | Scheduled job every 24 h | Refetches per network within budget; failures keep last-good + `stale` | BR-07 |

## Journey 5 - Public display (visitor)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Visitor | Opens `/:username` | SSR renders badge (if `verified`) + combined total from cached row | BR-08 |
| 2 | System | Network outage at a source | Page still renders; total shows `last updated ... (stale)` | BR-07 |
| 3 | Visitor | Hovers/taps the total | Breakdown tooltip with per-network values and provenance labels | BR-06 |
