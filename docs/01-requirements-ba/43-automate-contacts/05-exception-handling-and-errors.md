# Automate Contacts - Exception Handling and Errors (feature 43)

Phase 1 BA · Problems follow RFC 9457 (`code` field). Message bodies and network refs never appear in error payloads beyond the caller's own ids.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Marketing send attempted to a transactional-only contact | API (sender gate) | Refused by the single eligibility check; `marketing_consent_required` surfaced to the caller (gated 48–53 surfaces) | "This contact only consented to the automation reply." |
| Suppression raced with an in-flight send | System | Send intent already accepted proceeds (transactional promise kept); all future sends excluded; race audited | None (contact sees the promised reply; then silence) |
| `OPTOUT` from a contact with no row | Watcher (44) | Suppression transition creates a suppressed row (so future matches stay silent) | None |
| Duplicate identity (same ref, two capture paths) | System | Merge keeps strictest consent + earliest provenance; one row remains; audited | None |
| Export while rate-limited | API | 429 `export_throttled` with retry window | "Exports are limited — try again in {n} minutes." |
| Export of an empty filter | API | 422 `validation_failed` (empty result set) | "Nothing matches these filters." |
| Leads handoff with no eligible rows | API | `200` with `imported=0, skipped=n` (not an error) | "No rows had an email + consent — nothing to import." |
| Handoff row rejected by 27 (invalid email/duplicate) | System | Counted as skipped; retryable individually; no partial consent writes | Handoff dialog: "n skipped — see details." |
| Contact self-removes with expired token | Removal center | Honest neutral page; no enumeration; no state change | "This link expired — use the latest message's link." |
| Platform removal button (provider signal) | Webhook | Verified → suppression transition; invalid signatures refused | None |
| Stale contact receives a queued send attempt | Sender gate | Excluded (`stale` not eligible); contact re-engages only via fresh comment | None |
| Cross-creator suppression assumption | System | Impossible by design: suppression is per-profile; audit shows per-creator scope | None |
| Ops requests raw contact list | System | No such route exists (aggregates only); attempts are 404/403 by absence | None |
| Account deletion (02's purge) | System | Contact rows deleted with the profile; export files expire; handoff refs orphan harmlessly | None |
