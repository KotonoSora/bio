# Resume Documents - Exception Handling and Errors (feature 17)

Phase 1 BA · Failure paths handled in `docs/02-architecture-techlead/17-resume-documents/05-security-logging-and-rate-limiting.md`. Codes are stable contract; problems follow RFC 9457.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Unknown document id | API | `404 document_not_found`; list refresh guidance | "This resume no longer exists." |
| Foreign document id | API | `403` (same shape as 404 to the client where leak-free is required; `403` for explicit cross-user probes) | "You don't have access to this resume." |
| Delete with a visible linked block, no confirm | API | `422 delete_needs_confirm`; blocking confirm dialog | "This resume is linked to your public page." + consequence checkbox |
| Publish with no saved version | API | `422 snapshot_required`; nothing changes | "Save a snapshot before publishing." |
| Snapshot exceeds 2MB | API | `422 snapshot_too_large`; draft intact | "Resume too large - trim the longest sections." + section guidance |
| Profile read fails during capture | API | `503 capture_unavailable`; no partial version; retry safe | "Couldn't read your profile - try again." |
| Unknown version number | API | `404 version_not_found`; timeline refresh | "That version isn't available." |
| Archived version retrieval slow | API | Served from blob with `archived: true`; upfront notice | "Retrieving an archived version - this can take a few seconds." |
| Second tab saves concurrently | API | Both versions kept (N+1, N+2); timeline shows both | No error; toast "Saved as v{n}" |
| Publish race (two tabs) | API | Transaction + partial unique; last write wins cleanly; other document displaced with notice | "This replaces "{title}" as your public CV." |
| Unlink fails during confirmed delete | API | Delete aborted (block stays, document stays); retry safe; never document-deleted-with-visible-link | "Couldn't update your page - try again." |
| Unpublish | API | Flags cleared; `/cv` parity 404 within `<= 60 s` | Confirm states: "Your CV link will stop working." |
| Profile edited after save | UI/System | Old version reprinted byte-identical; no error - by design | Frozen pill + source rev reveal the source |
| Session expires mid-draft | UI | Draft preserved client-side; re-login resumes | "You were signed out - your draft is saved here." |
