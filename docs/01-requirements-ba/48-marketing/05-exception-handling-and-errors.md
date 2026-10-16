# Marketing Consent and Broadcasts - Exception Handling and Errors (feature 48) - GATED

Phase 1 BA · GATED: at amendment; problems follow RFC 9457 (`code` field). No addresses, tokens, or consent internals leak beyond the caller's own scope. The live gate itself: any pre-amendment attempt to find this surface is a release-blocking finding (BR-01).

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Marketing send attempted while gated | System | Pipeline refuses `marketing_not_accepted` (today's behavior stays until amendment) | Internal only |
| Send classified as transactional but marketing in content | Review | Treated as a consent violation; remediation defined | Ops: "Class mismatch — fix before send." |
| Pre-ticked marketing box found in any form | Review gate | UI + API review fails; ship blocked | "Marketing boxes are unchecked by default." |
| Marketing opt-in attempted on a suppressed address | API | Capture recorded, eligibility unchanged; re-consent flow offered | "This address needs a fresh confirmation first." |
| Re-consent attempts beyond 3/address/year | API | 429 `throttled` (abuse guard) | "Too many attempts — try again later." |
| CSV import with invalid/duplicate/suppressed rows | Flow | Batch imports valid rows; exact row-level report; nothing loosened | "12 rows skipped — download the report." |
| Address parked after 12 months | System | Excluded from eligibility; history kept; unpark on engagement | Ops view only |
| Webhook signature invalid | Webhook | 401, event dropped, alert; stats unaffected | Internal only |
| Duplicate webhook event id | Webhook | Idempotent no-op | None |
| Complaint-rate breach | System | Sends auto-pause; ops paged; stats stay readable | "Sends paused — complaint rate above threshold." |
| Export of suppressed addresses without a case | API | 403; case path explained; audited | "A support case is required for this export." |
| Creator form leads probed in platform eligibility | System | Empty by silo construction; probe logged | Internal only |
| Preference token expired or forged | Public page | Neutral expired page; fresh link path | "This link expired — request a new one." |
| Test-send to a non-operator address | API | 422 `validation_failed` | "Test sends go to operator addresses only." |
| Scheduling/segment/A-B attempt | API | 404/422 (controls do not exist) | "Not available." |
| Raw HTML pasted into the body editor | UI/API | Stripped; sanitized templates only | "Paste plain text — formatting comes from the template." |
| Mid-batch pause request | System | Dispatch halts within seconds; resume never double-sends (per-recipient idempotency) | "Paused — resume is safe." |
| Account deletion with pending campaign | System | Address withdraws all classes + suppresses; next send excludes it | None (deletion proceeds) |
