# Publish Composer - Workflow and User Journey (feature 42)

Phase 1 BA · W-1..W-5. Rules: `docs/01-requirements-ba/42-publish-composer/02-user-stories-and-business-rules.md`. Contracts: `docs/02-architecture-techlead/42-publish-composer/03-api-contracts-and-payloads.md`.

## W-1 Compose + schedule

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | opens composer, uploads video | multipart parts issued; gates (duration/size/codec/magic-byte) verified before upload completes | BR-01, BR-02 |
| 2 | Creator | writes caption, picks targets | per-network trims + counters; only healthy accounts selectable; default all healthy | BR-03, BR-04 |
| 3 | Creator | sets now or `run_at`, confirms | synchronous validation (gates, caps, quota) → draft/scheduled row + per-target rows queued | BR-05, BR-07, BR-08 |
| 4 | System | quota check | counted at successful publish, not at schedule; in-composer meter reads the same counting function | BR-07 |
| 5 | Creator | edits/reschedules before lock | allowed until 5 min before `run_at`; after → 409 `lock_window` | BR-05 |

## W-2 Dispatch fan-out

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Scheduler | `run_at` reached (DB-locked, idempotent) | one child job per target enqueued to `publish.jobs` (message carries ids + opaque lease id only) | BR-08 |
| 2 | Consumer | job arrives | status `sending`; vault lease resolves credentials in-process; provider publish call with idempotency key where offered | BR-08, BR-11 |
| 3 | System | provider accepted | status `published` + provider ref; parent rollup recomputes | BR-09 |
| 4 | Poller | confirmation due | status/media-id polling with backoff until confirmed or 15-min deadline → `failed` (`timeout`) | BR-10 |
| 5 | System | mixed outcomes | parent `partial`; N-of-7 view shows exactly which legs stand | BR-09, BR-14 |

## W-3 Status + retry

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | opens post status | live per-target rows + N-of-7; reasons in plain language | BR-14 |
| 2 | Creator | retries a failed target | eligibility check → new attempt; dedupe record prevents double-posts | BR-11, BR-12 |
| 3 | System | transient class | auto-retry with capped attempts + backoff; state `retrying` | BR-12 |
| 4 | System | permanent class | `failed` immediately with fix guidance; no futile retries | BR-12 |
| 5 | System | ambiguous timeout | poll-then-ask: reconcile first; if unverifiable → confirm gate before any repost | BR-11, Q-04 |

## W-4 Outage + quarantine

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Ops | pauses a network leg | dispatch for that leg stops; affected posts `queued_behind_outage` + creator notice | BR-13 |
| 2 | System | queued jobs behind the pause | held in order; no drop, no reorder | BR-13 |
| 3 | Ops | resumes | replay in original order; normal dispatch resumes | BR-13 |
| 4 | System | poison job (N redeliveries) | parked to dead-letter; ops page; replay tool (dual-control on mass) | BR-13 |

## W-5 Late dispatch

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | System | scheduler outage pushed `run_at` into the past | dispatch proceeds on recovery | BR-05 |
| 2 | System | target published past its slot | `late` flag set on parent + creator notice | BR-05 |
| 3 | Creator | sees notice | status view shows exactly what went out late and when | BR-14 |
