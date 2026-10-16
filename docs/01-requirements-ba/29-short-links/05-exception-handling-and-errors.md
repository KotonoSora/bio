# Short Links - Exception Handling and Errors (feature 29)

Phase 1 BA · Path types: Happy / Alternative / Exception. Error `code` values are the API contract; the catalog lives in `docs/02-architecture-techlead/29-short-links/04-api-contracts-and-payloads-part-2.md`.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Mint live link (valid destination, code free) | Happy | 201 + quota headers; row prepended | none |
| Open live `/go/<code>` | Happy | 302 no-store; hit event enqueued | none |
| Vanity code already taken | Alternative | 409 `code_taken`; dialog offers 3 generated alternatives | "That code is taken. Try one of these:" |
| Destination on blocklist at mint | Exception | 422 `destination_blocked`; nothing persisted | "This destination cannot be shortened (blocked list)." |
| Destination re-listed after mint | Exception | Resolve re-check fails -> 403 suspension page; owner notified in-app | "This link was suspended." |
| Quota exhausted | Exception | 429 `link_quota_exhausted` + reset date; submit disabled client-side pre-flight | "You have used all N mints this month. Resets <date>." |
| Mint flood from one IP/account | Exception | 429 throttle (20/min account, 60/min IP) + WAF | "Too many requests. Try again in a minute." |
| Unknown code | Exception | 404 branded page; only after 100/min/IP defense | "We could not find this link." |
| Expired code | Exception | 410 branded gone page with creator contact; owner sees expired chip | "This link has expired." |
| Dormant code, fallback set / not set | Alternative | 302 to fallback / 410 gone page | "This link has moved." / expired copy |
| Suspended code | Exception | 403 branded suspension page; destination never leaks | "This link is unavailable." |
| Edit attempts to change `code`, `kind`, or owner | Exception | 422 `immutable_field`; server ignores the field | "Codes and owners cannot be changed." |
| Session expired mid-mint | Exception | 401; dialog state preserved, SSO re-login resumes | "Please sign in to finish creating this link." |
| Bulk mint attempted by creator | Exception | 403 + audit event; ops-only path | "Bulk creation is not available." |
| Generation fails 5x (collision storm) | Exception | 503 `generation_unavailable` + ops alert | "Something went wrong. Please retry." |
| Double-submit / double-mint race | Exception | PK collision loses; exactly one link returned | none (one row appears) |
| Redis cache down | Exception | Resolve degrades to direct database read (p95 <= 300 ms); mint quota fails closed | none visible / "Please retry." on mint |
| Blocklist client down at mint | Exception | Fail closed: 503 `blocklist_unavailable` | "Please retry in a moment." |
| Tombstone then open | Exception | 410 gone page; owner list keeps the tombstoned row + stats | "This link has expired." |
