# Automate Triggers - Workflow and User Journey (feature 44)

Phase 1 BA · W-1..W-4. Rules: `docs/01-requirements-ba/44-automate-triggers/02-user-stories-and-business-rules.md`. Contracts: `docs/02-architecture-techlead/44-automate-triggers/03-api-contracts-and-payloads.md`.

## W-1 Webhook → match → intent

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Instagram | posts a comment webhook | signature verified → event persisted (anti-replay) → enqueued to `automate.watch` | BR-17 |
| 2 | Watcher | consumes the event | evaluates the first observed version: scope, self-comment, blocklist, collision-ordered match | BR-01..BR-05 |
| 3 | System | decision made | trigger-log row (matched / no_match / self_comment / blocklisted) | BR-05 |
| 4 | System | matched | one intent created (`matched`) per (automation, comment); delay clock starts | BR-06, BR-17 |
| 5 | Creator | opens the trigger log | sees every comment's decision + intent status | US-03 |

## W-2 Delivery pipeline (gated order)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Delivery consumer | delay elapsed, intent due | contacts quota (43's single check) → suppression → window → quiet hours → fire cap → pacing, in order | BR-10 |
| 2 | System | any hold trips | intent holds (`held_quota|held_quiet|held_cap`); re-evaluated when the hold lifts | BR-10..BR-12 |
| 3 | System | all gates pass | status `sending`; public reply posted; DM sent with the single rendered link | BR-08, BR-13 |
| 4 | System | accepted | `replied` → `dm_sent`; contact captured (43); then silence | BR-16, BR-08 |
| 5 | System | window elapsed before send | `window_lapsed`; no retry exists | BR-09 |
| 6 | System | failure | `failed` with class + reason (transient retries bounded, permanent terminal) | BR-18 |

## W-3 Spike → burst-abort → reinstate

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Monitor | error rate > 20% over 10 min (min 20 attempts) | automation auto-pauses; ops paged; creator notified | BR-14 |
| 2 | System | in-flight intents | complete or fail per class; new matches hold as paused | BR-14 |
| 3 | Ops | reviews, fixes root cause | reinstate (audited with reason); paused intents resume in order | BR-14 |
| 4 | Creator | sees the pause notice | plain-language reason + status | US-04 |

## W-4 Opt-out and template review

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Commenter | replies `OPTOUT` in any thread | 43's suppression transition; no DM for that comment | BR-16 |
| 2 | System | suppressed contact matches later | intent `skipped_suppressed`; nothing sent, nothing captured | BR-16 |
| 3 | Someone | reports a template (platform or support) | template → `pending_review`; automation pauses until decided | BR-15, Q-03 |
| 4 | Ops | decides approve/reject with reason | approve → automation resumes; reject → template blocked, creator notified | BR-15 |
