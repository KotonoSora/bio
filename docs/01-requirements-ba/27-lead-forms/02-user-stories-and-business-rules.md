# Lead Forms - User Stories and Business Rules (feature 27)

Phase 1 BA · Realized by `docs/02-architecture-techlead/27-lead-forms/` (BR-01..BR-16).

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Visitor | submit my email in one tap and know exactly what I signed up for | capture is frictionless and honest | Must |
| US-02 | Creator | configure fields and offer copy per form and see submissions in my dashboard | I can follow up | Must |
| US-03 | Visitor | receive confirmation + double opt-in email where enabled | my subscription is provable | Should |
| US-04 | Creator | trust that duplicates, replays, and spam never pollute my inbox | the list stays clean | Must |
| US-05 | Contact | have consent recorded at birth with the exact copy I saw | my choice is provable | Must |

## Business rules

- BR-01 Form config (block `payload_json`, closed schema): `{headline ≤ 120, offerCopy? ≤ 280, fields[] from the closed menu (email required; name, phone, note optional), submitLabel ≤ 40, successMessage ≤ 200, redirect? (own page or link-block target), doubleOptIn bool, copyVersion}`; `copyVersion` is server-stamped (hash) on every save; unknown keys reject.
- BR-02 Validation: client-side inline (format + did-you-mean for major domains) is UX only; the server re-validates format, normalizes email lowercase, caps lengths (email ≤ 254, name ≤ 80, phone ≤ 20, note ≤ 500) and sanitizes before storage.
- BR-03 Capture pipeline: the public submit handler writes exactly one capture-outbox row and never touches leads directly; the scheduled consumer (batch ≤ 50, every 2 s, backoff retries) performs dedupe/merge; inbox landing p95 ≤ 5 s; unprocessable rows drop after 24 h with an internal metric.
- BR-04 Dedupe/idempotency: `leads` is unique on `(profile_id, email)`; an identical `(block, email)` submission within 60 s coalesces into the existing capture (no second event); later submissions record `resubmitted` and merge under strictest-state-wins.
- BR-05 Consent at birth: classes `lifecycle|marketing`; marketing ONLY when the visitor checked the box (default unchecked); `consent_at` + `consent_copy_version` come from the submission; later explicit checked submissions upgrade the class; omission never downgrades; suppression beats every consent state.
- BR-06 Status machine: `pending→active` (double-opt-in confirm); `active→unsubscribed` (contact or preference center); provider signals (forward, notification pipeline) set `bounced|complained`; manual `suppressed` (feature 28) is permanent; strictest rank wins: `suppressed > complained > bounced > unsubscribed > active > pending`; re-submission never resurrects suppression.
- BR-07 Double opt-in: `pending` rows are send-ineligible but remain visible in the inbox; the confirm token is HMAC-signed, 48 h expiry, single-purpose; confirm is an explicit POST (a GET only shows the confirm page); expired tokens offer clean re-request without duplicate rows; re-requests rate-limited.
- BR-08 Welcome: at most one template-bound welcome on activation; no drip or sequence exists in this feature.
- BR-09 Inbox: per-form feed, newest-first, unread markers, owner-scoped to the creator's own profiles; per-lead detail belongs to feature 28.
- BR-10 Anti-oracle: accepted, duplicate, and unknown-block submissions return the identical 202 body and timing; no successful response reveals whether an email already exists; malformed email alone returns 422.
- BR-11 Abuse budgets: Turnstile-class challenge + gateway WAF/rate rules on submit; per-block velocity cap 100/min per source pauses the form 15 minutes with a creator-visible notice; raw signals (IP hash counters) age out on a 24 h TTL.
- BR-12 Sanitization/no reflection: script/iframe/event-handler content in name/phone/note is stripped before storage; the success state renders creator copy only, never visitor input; CSP applies.
- BR-13 No SDKs: no third-party scripts on the capture surface; submission is same-origin from the SSR page; native email keyboard/autofill honored.
- BR-14 Audit: `lead_events` is append-only (`captured`, `resubmitted`, `confirmed`, `status_changed`, `note_added`, `tag_added`, `tag_removed`, `suppressed`, `reinvited`, `exported`, `read`), 24-month retention, actor-stamped.
- BR-15 Purge: account deletion removes the creator's leads, events, and outbox rows; the leads data changes nothing about feature 02's grace mechanics.
- BR-16 No manual consent grants: no endpoint or UI can set a consent class outside the visitor's own checked submission (surface review gate).
