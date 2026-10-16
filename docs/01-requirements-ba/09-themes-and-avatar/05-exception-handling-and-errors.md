# Themes and Avatar - Exception Handling and Errors (feature 09)

Phase 1 BA · Path types: Happy / Alternative / Exception. Error `code` values are the API contract; the catalog lives in `docs/02-architecture-techlead/09-themes-and-avatar/03-api-contracts-and-payloads.md`.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Valid PNG/JPEG/WebP upload | Happy | Validated -> name `profile/avatars/{ulid}.{ext}` -> file write -> row update -> old file deleted -> audit + event | avatar swaps everywhere |
| Valid loop MP4 on a `videoAvatar` theme | Happy | Same pipeline; loop cap asserted | avatar swaps everywhere |
| Delete avatar (file already gone) | Happy | `204`; row key cleared unconditionally | default glyph returns |
| Unknown `themeId` (registry changed) | Alternative | `404 theme_not_found`; gallery refreshed | "That theme is no longer available." |
| Pro theme on a free plan (direct API) | Exception | `403 theme_forbidden`; UI rolls back to active theme | upgrade sheet opens |
| Entitlement check unreachable | Alternative | Pro themes fail closed (`403 theme_forbidden`); standard themes unaffected | upgrade sheet (retry later) |
| File over 2 MB | Exception | Client pre-check blocks before any byte; server re-checks while reading | "Images up to 2 MB." |
| Renamed script / SVG / HTML content | Exception | `422 avatar_bad_type`; rejected by content sniffing, not extension; zero file writes | "Images only (PNG, JPEG, WebP)." |
| MP4 on a theme without `videoAvatar` | Exception | `422 avatar_video_not_allowed`; nothing written | "Video avatars need an animated theme." |
| MP4 longer than 10 s | Exception | `422 avatar_video_too_long`; nothing written | "Loops up to 10 s." |
| MP4 without `ftyp` marker | Exception | `422 avatar_bad_type` (content sniff fails) | "Only MP4 loops are supported." |
| Crash between file write and row update | Alternative | One orphan file; request unaffected; sweep deletes it after 24 h | none |
| Theme switch while avatar upload pending | Alternative | Independent resources; independent rollbacks; both may complete | none |
| Purge pending after theme/avatar save | Alternative | Purge chip shows `publishing...`; clears on fresh content or after 2 min | "publishing..." (chip) |
| Unknown `themeId` at public render (stale catalog) | Alternative | Fresh catalog retry; still unknown -> default theme | none (page renders) |
| Rapid upload bursts | Exception | Turnstile + rate rules -> `429 throttled` + `Retry-After` | "Too many uploads. Try again shortly." |
| Cross-user write / IDOR probe | Exception | `403 forbidden`; session `user_id` only; RLS denies at DB | "You do not have access to this profile." |
| Session expired mid-upload | Exception | `401`; re-login keeps the selected file name; bytes require re-pick | "Please sign in to continue." |
| Forged `Content-Type: image/png` with script bytes | Exception | Magic-byte check fails -> `422 avatar_bad_type`; claimed MIME never trusted | "Images only (PNG, JPEG, WebP)." |
