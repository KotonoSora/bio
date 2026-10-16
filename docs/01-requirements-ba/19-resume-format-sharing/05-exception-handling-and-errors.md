# Resume Format Sharing - Exception Handling and Errors (feature 19)

Phase 1 BA · Failure paths handled in `docs/02-architecture-techlead/19-resume-format-sharing/05-security-logging-and-rate-limiting.md`. Codes are stable contract; problems follow RFC 9457.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Personal-data keys in layout | API | `422 format_personal_data` naming the key path; zero rows published | "`sections.experience.items.text` is resume data, not layout." |
| Inline style in a field | API | `422 format_bad_style`; removed from consideration | "Styles aren't allowed in formats - use swatches." |
| Script content in a field | API | `422 format_bad_script`; audited | "Scripts aren't allowed in formats." |
| External URL in a field | API | `422 format_bad_url` | "Links aren't part of a format." |
| Publish attempt on a non-draft row | API | `409` state conflict (nothing changes) | "This version is already published." |
| Clone of unpublished/removed format | API | `409` (or `404` for foreign ids - leak-free shape) | "This format isn't available for cloning." |
| Clone already exists | API | `200 {already:true}`; existing copy opens | "You already have this format - opening your copy." |
| Selection of a foreign/unknown `fmt:` id | API | `422 unknown_template` (feature 18 code, extended) | "This format isn't in your library." |
| Foreign format edit/publish | API | `403`; RLS denies direct access | "You don't have access to this format." |
| Annual weekly re-scan hit | System | Flag for human review + metric; no auto-takedown | Ops-facing: "Format {id} flagged by the privacy re-scan." |
| Ops removal without reason | API | `422` (reason mandatory); no state change | Ops-facing: "A removal reason is required." |
| Reported format, author view | UI | Author sees no reporter identity, ever | Author-facing: "This format is under review." |
| Gallery burst | API | `429 throttled` + `Retry-After`; cache absorbs steady traffic | "Too many requests - try again in {seconds}." |
| Offline publish attempt | UI | Blocked; draft persisted locally | "You're offline - publishing is paused." |
| Removed format requested by URL | Public | Detail shows a removed-state page (not a raw 404: takedown is public information) | "This format was removed." |
| Purged author's format requested | Public | Rows deleted by the purge participant; parity like any missing slug | "This format isn't available." |
| Thumbnail generation failure | System | Card renders without image (text-first card); retried by the next publish; metric | No error to visitors |
| Blob store down during publish | API | Publish holds? No: publish fails atomically; draft intact | "Couldn't finish publishing - try again." |
