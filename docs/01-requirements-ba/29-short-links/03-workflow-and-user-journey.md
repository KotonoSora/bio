# Short Links - Workflow and User Journey (feature 29)

Phase 1 BA · Flows below are normative; rules cite `02-user-stories-and-business-rules.md`, errors cite `05-exception-handling-and-errors.md` codes.

## Journey 1 - Mint (creator)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Opens `/app/links`, clicks "New short link" | Mint dialog renders; quota meter shows remaining count | BR-09 |
| 2 | SPA | Types destination; optionally toggles vanity; picks expiry | Client validates shape only (`https:`, length); safety is server-side | BR-05 |
| 3 | SPA | Submits `POST /api/v1/links` with Turnstile token | Submit disabled while in flight | BR-09 |
| 4 | Gateway | Session check, per-account + per-IP rate limit, Turnstile verify | 401 sign-in redirect / 429 throttle | BR-11 |
| 5 | Link service | Validates destination (`https:`, blocklist at mint) | 422 `destination_blocked` | BR-05 |
| 6 | Link service | Checks monthly quota counter | 429 with reset time when exhausted | BR-09 |
| 7 | Link service | Generates random code, or probes vanity PK; bounded retry <= 5 | 409 `code_taken` for vanity; 503 + alert after 5 failures | BR-02, BR-03 |
| 8 | Link service | Inserts `live` row, writes audit, increments quota counter | 201 with `LinkResponse` + quota headers | BR-10, BR-11 |
| 9 | SPA | Prepends row, focuses Copy | Row shows live chip and short URL | US-01 |

Alternatives: vanity taken -> dialog offers 3 generated alternatives (network log shows only mint calls); quota exhausted -> dialog shows reset date and disables submit.

## Journey 2 - Resolve (visitor)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Visitor | Opens `/go/<code>` (or `/r/`, `/a/` alias) | Request enters gateway | BR-01 |
| 2 | Gateway/edge | Bot-class filter, per-IP rate limit | 429 page on flood | BR-09 |
| 3 | Link service | Cache lookup (`links:code:<code>`) | Hit skips the database read | BR-10 |
| 4 | Link service | Miss: PK read, blocklist re-check, state compute | Fixed state machine below | BR-05, BR-06 |
| 5 | Link service | Enqueue hit event after the decision | Fire-and-forget; never blocks the response | BR-10 |
| 6 | Response | live 302 no-store / vanity-permanent 301 / expired or dormant-without-fallback 410 gone page / dormant-with-fallback 302 / suspended 403 page / unknown 404 branded | Destination never leaks on suspended | BR-04, BR-06, BR-07 |

## Journey 3 - Report and suspend (reviewer)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Anyone | Reports a link with reason | 202 queued; Turnstile applied | BR-09 |
| 2 | Abuse reviewer | Opens ops queue, reviews destination + evidence | Rows and clicks preserved | US-08 |
| 3 | Abuse reviewer | Confirms suspend | Status -> `suspended`, audit event, cache purge event published | BR-05 |
| 4 | System | All links to the same destination flip within 5 min | Resolve serves the suspension page | NFR-04 |

## Journey 4 - Expiry and dormant fallback (system)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Scheduler | Hourly sweep | Materializes `expired` for past `expires_at` | BR-06 |
| 2 | Link service | Resolve of expired code | 410 gone page with creator contact | BR-06 |
| 3 | Link service | Resolve of dormant code | 302 to `fallback_url` when declared, else gone page | BR-07 |
