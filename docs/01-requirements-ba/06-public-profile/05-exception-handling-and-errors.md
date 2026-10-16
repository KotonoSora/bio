# Public Profile - Exception Handling and Errors (feature 06)

Phase 1 BA · Path types: Happy / Alternative / Exception. Error `code` values are the API contract; the catalog lives in `docs/02-architecture-techlead/06-public-profile/03-api-contracts-and-payloads.md`.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Claim or check a free username | Happy | normalized echo + `available: true` | none |
| Save valid profile edits | Happy | `200`; purge chip until live (<= 2 min) | toast "Saved" |
| Uppercase or mixed-case input | Alternative | normalized (`Alice!` -> `alice!`), verdict recomputed; never an error by itself | none (echo shown) |
| Username too short / too long | Exception | `422 username_too_short` / `username_too_long` | "Usernames are 3-30 characters." |
| Username has bad characters | Exception | `422 username_bad_chars` | "Use lowercase letters, numbers, and underscores only." |
| Reserved slug (`app`, `shop`, `go`, ...) | Exception | `422 username_reserved` | "That name is reserved by the platform." |
| Retired username (formerly used) | Exception | `422 username_retired` | "That name was used before and stays reserved." |
| Username taken (live profile) | Exception | `422 username_taken` | "That name is taken." |
| Rename to the current username | Exception | `422 username_unchanged`; nothing written | "That is already your username." |
| Raced rename on confirm | Alternative | `422 username_taken`; wizard returns to screen 1, name marked taken | "That name was just taken." |
| Field over limit (`display_name`, `bio`, `seo_*`) | Exception | `422` with the exact code; nothing saved | field-specific: "Bio is limited to 500 characters." |
| Second profile attempt | Exception | `422 profile_limit_reached` | "One profile per account in this version." |
| Turnstile challenge failed | Exception | `422 challenge_failed`; nothing written | "Verification failed. Please retry." |
| Save while offline | Exception | disabled submit; no queue | "You're offline." |
| Session expired mid-wizard | Exception | `401`; wizard input preserved, re-login resumes | "Please sign in to continue." |
| Cross-user API call (IDOR probe) | Exception | `403 forbidden`; RLS denies at DB even without app check | "You do not have access to this profile." |
| Anonymous read of private profile | Exception | `404` with the same shape/timing as unknown (no oracle) | "This page does not exist." |
| Too many checks/renames | Exception | `429 throttled` + `Retry-After` | "Too many attempts. Try again in <cooldown>." |
| HTML or `javascript:` payload in fields | Exception | stored stripped (HTML) or rejected (`javascript:`); audit note | none (content silently sanitized) |
