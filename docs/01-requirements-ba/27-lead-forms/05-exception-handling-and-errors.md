# Lead Forms - Exception Handling and Errors (feature 27)

Phase 1 BA · Codes are stable; UI copy localizes vi/en.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Malformed email | API | 422 `email_invalid`; nothing written | "Check the email address." |
| Field over cap (name/phone/note) | API | 422 `validation_failed`; nothing written | inline per-field hint |
| Duplicate submit (double-tap/replay) | API | uniform 202; coalesced within 60 s; one lead | (success state once) |
| Unknown/inactive block | API | identical 202; consumer drops with internal metric | (success state — anti-oracle) |
| Form velocity-paused | API | 429 `submit_paused` | "This form is paused — try again later." |
| Gateway rate cap / bot challenge failed | Gateway | 429 / challenge re-issue; uniform after clearance | "Too many attempts — try again shortly." |
| Confirm token expired | API | POST returns 422 `confirm_expired`; page offers re-request | "This link expired — request a new confirmation." |
| Confirm token invalid/tampered | API | 422 `confirm_invalid`; zero state change | branded error state |
| Confirm re-request flood | API | rate-limited (3/h per address) | "A confirmation was already sent." |
| Scanner prefetch of confirm link | System | GET is read-only; never confirms | - |
| Provider bounce/complaint (forward) | System | status per strictest-wins; inbox chip updates | - |
| Cross-creator inbox/detail access | API | 403 + RLS denies; zero rows | "You don't have access to this." |
| Re-submission after suppression | System | merge records `resubmitted`; status stays `suppressed` | (success state — no signal) |
| Consent grant attempt via API | System | impossible: no endpoint exists (surface review gate) | - |
| Redirect target invalid | Config save | 422 `redirect_invalid` (own page or link-block target only) | "Choose a valid redirect target." |
