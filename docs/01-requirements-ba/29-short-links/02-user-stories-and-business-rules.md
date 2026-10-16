# Short Links - User Stories and Business Rules (feature 29)

Phase 1 BA · Stories seed the tests in `06-acceptance-criteria.md`; rules `BR-xx` are quoted by every later file.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Creator | mint a short link for any destination from one dialog | campaigns stay trackable | Must |
| US-02 | Creator | claim a vanity code when it is free | branded links match the campaign | Must |
| US-03 | Creator | set expiry or rely on the campaign default | dead links do not outlive campaigns | Must |
| US-04 | Creator | edit destination and expiry without the code changing | printed QR codes and SMS keep working | Must |
| US-05 | Creator | tombstone a link and keep its stats | history stays complete | Must |
| US-06 | Creator | see quota state before and after minting | no surprise 429s | Should |
| US-07 | Visitor | land on the destination within a blink of opening a short URL | redirects feel instant | Must |
| US-08 | Abuse reviewer | suspend one destination and kill every link to it | one action closes the hole platform-wide | Must |
| US-09 | Platform ops | bulk-mint through an audited ops action | campaigns onboard without a public spam loophole | Should |

## Business rules

- **BR-01 Namespace.** All short links live under `/go/<code>` (primary); `/r/` and `/a/` are aliases resolving through the same table. `go`, `r`, `a` stay permanently reserved: the profile service rejects them as usernames, and profile `/:username` paths never enter this system.
- **BR-02 Code generation.** Random codes: base62, length >= 7, from a CSPRNG; never sequential, never timestamp-derived; PK collision fails the mint closed and is retried at most 5 times. Vanity codes: 3-32 chars of `[a-z0-9-]`, no leading/trailing hyphen, screened for profanity and reserved words, scoped per account.
- **BR-03 Immutability.** A code is never renamed and never recycled while any click history exists; destination history is never silently rewritten (status flips are audited).
- **BR-04 Redirect semantics.** Default: 302 with `Cache-Control: private, no-store` - every open re-hits the counter. 301 only for explicit-permanent vanity moves; browsers cache it, so later opens are uncounted by design.
- **BR-05 Safety.** Destination must be `https:`. The platform blocklist (phishing/malware/abuse) is checked at mint and re-checked at resolve. A re-listed destination suspends its links; the destination is never silently rewritten.
- **BR-06 Expiry.** Past `expires_at`, resolve serves a branded gone page with HTTP 410 and creator contact - never a homepage redirect, never a blind 404. Materialized hourly by sweep AND computed at resolve; both must agree.
- **BR-07 Dormant.** Owner-removed destination with `fallback_url` resolves to the fallback; without one, the gone page. Dormancy never produces a blind 404.
- **BR-08 Tombstones.** Creator delete sets `expires_at = now()`; row and clicks remain. Hard delete is refused while analytics references the code.
- **BR-09 Budgets.** Per-account monthly mint quota (v1 default 500, plan-configurable) plus per-IP rate limits; Turnstile on mint and report; bulk minting is ops-only and audited.
- **BR-10 Counting.** Resolve performs zero database writes; a hit event is enqueued after the response decision; the analytics feature owns aggregates.
- **BR-11 Scoping.** Owner scoping plus row-level security; `user_id` is server-set from the gateway identity, never from client input.
