# Link Blocks - Exception Handling and Errors (feature 08)

Phase 1 BA · Path types: Happy / Alternative / Exception. Error `code` values are the API contract; the catalog lives in `docs/02-architecture-techlead/08-link-blocks/03-api-contracts-and-payloads.md`.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Create with valid payload | Happy | Ownership assert -> validate -> `position = max + 1` -> save -> audit + event | toast "Block added" (appended at end) |
| Duplicate | Happy | Copy inserted directly below source; followers shift in one batch; single audit | toast "Duplicated" |
| Delete | Happy | `204`; row animates out; `Undo` for 5 s re-POSTs the cached payload as a NEW row | toast with `Undo` |
| Unknown `type` via direct API | Exception | `422 unknown_block_type`; zero writes; DB CHECK is the backstop | "That block type is not supported." |
| Non-`https:` URL | Exception | `422 bad_url_scheme`; blocked pre-send AND server-rejected (same message) | "URL must start with https://." |
| Title over 120 chars | Exception | `422 title_too_long`; counter in field | "Title is too long (max 120)." |
| Payload over per-type cap | Exception | `422 payload_too_long` | "This block's content is too large." |
| Inverted schedule window | Exception | `422 bad_schedule_window`; zero writes; live label corrected | "End time must be after start time." |
| 251st block | Exception | `422 block_limit_reached`; `Add block` disabled with cap restated | "Limit of 250 blocks reached." |
| Type change attempt on edit | Exception | `422 validation_failed`; type is immutable, delete + create instead | "Type cannot be changed; delete and recreate." |
| Reorder with stale or foreign id | Exception | `422 reorder_mismatch`; zero writes; positions untouched | list rolls back + "Order changed elsewhere. Reload." |
| Patch or delete of an unknown id | Exception | `404 block_not_found` | row dropped + list reload |
| Hide toggle save fails | Alternative | Optimistic chip flip reverted + toast | "Couldn't save. Reverted." |
| Hidden/future/expired block publicly | Alternative | Absent from public HTML/JSON; dashboard row shows status chip with date | none |
| Cross-user mutation / IDOR probe | Exception | `403 forbidden`; RLS denies at DB even without the app check | "You do not have access to this profile." |
| Script/iframe/event-handler in any payload field | Alternative | Sanitizer strips at write; stored value is already clean; anchors render `rel="noopener ugc"` | none |
| CSRF token missing / session expired | Exception | `401`/`403`; dialog input preserved for re-login | "Please sign in to continue." |
| Write rate limit breach | Exception | `429 throttled` + `Retry-After` | "Too many changes. Try again shortly." |
