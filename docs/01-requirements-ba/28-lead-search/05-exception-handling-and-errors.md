# Lead Search - Exception Handling and Errors (feature 28)

Phase 1 BA · Codes are stable; UI copy localizes vi/en.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Query under 2 chars | API | 422 `search_too_short`; no query logged | "Type at least 2 characters." |
| Unknown filter value / malformed date | API | 422 `filter_invalid`; filters unchanged | "Check the filter values." |
| Lead not found or foreign | API | 404 `lead_not_found` (foreign rows read as absent — no existence oracle) | "Contact not found." |
| Note over cap | API | 422 `note_too_long`; nothing written | "Notes are limited to 2 000 characters." |
| Tag over 40 chars / invalid chars | API | 422 `tag_invalid` | "Tags are 40 characters max." |
| Vocabulary cap reached | API | 422 `tag_limit_reached` | "You've reached 50 tags — reuse an existing one." |
| Suppression without reason | API | 422 `suppress_reason_required` | "Add a reason for the opt-out record." |
| Suppress an already-suppressed lead | API | idempotent 200; no duplicate event | (success) |
| Re-invite a suppressed lead | API | 422 `reinvite_blocked_suppressed` | "This contact opted out — re-invite is disabled." |
| Re-invite rate cap | API | 429 `reinvite_limited` | "Re-invite already sent — try again tomorrow." |
| Export with conflicting selection (filters + ids) | API | 422 `export_invalid` | "Choose either the filtered set or checked rows." |
| Export quota (20/day, 2 concurrent) | API | 429 `export_quota` | "Export limit reached — try later." |
| Download before job completes | API | 409 `export_not_ready` | "Export is still running." |
| Grant expired | API | 410 `grant_expired`; job may be re-granted while it lives | "Download link expired — open the export again." |
| Foreign export job / object | API | 404 `export_not_found`; no existence oracle | "Export not found." |
| Unsubscribe token expired/invalid | API | 422 `unsubscribe_expired` / `unsubscribe_invalid`; zero state change | branded error state |
| Cross-creator search/read/action | API | 403 + RLS denies; zero rows | "You don't have access to this." |
| Forward seam unavailable (orders/booking/automation) | UI | timeline section absent with a quiet reason label | "Not yet connected." |
