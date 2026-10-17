# Shop Fulfillment - Exception Handling and Errors (feature 23)

Phase 1 BA · Codes are stable; UI copy localizes vi/en.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Artwork over 20 MB | API | 422 `artwork_too_large`; no file write | "Artwork must be 20 MB or smaller." |
| Artwork fails magic-byte (incl. SVG/HTML) | API | 422 `artwork_bad_type` | "Upload a PNG, JPEG, WebP, or PDF." |
| File over 500 MB | API | 422 `file_too_large` | "Files must be 500 MB or smaller." |
| Scriptable/fake-mime file | API | 422 `file_bad_type`; allowlist only | "This file type isn't allowed." |
| Digital/paywall product publish with no current version | API | 409 `publish_precondition_unmet` | "Upload and publish a file before going live." |
| Merch product publish with no pinned design | API | 409 `publish_precondition_unmet` | "Attach a design before going live." |
| Access-link before payment or after refund | API | 403 `entitlement_required` | "Purchase this item to download it." |
| Window lapsed | API | 403 `entitlement_expired` | "Your download window ended — re-purchase or contact support." |
| Fetch count exhausted | API | 403 `entitlement_exhausted` | "Download limit reached — contact the creator." |
| Anomaly flag | API | 403 `entitlement_flagged`; paused pending support | "Access is paused for review — contact support." |
| Locked body request without entitlement | API | 403 `entitlement_required`; zero body bytes | "Purchase this post to read it." |
| Partner callback tampered / unsigned | API | 401 `callback_signature_invalid`; no state change | (partner-facing JSON only) |
| Duplicate callback replay | API | 202 accepted, deduped by event id; no double transition | (partner-facing JSON only) |
| Unpaid/cancelled order dispatch attempt | System | zero jobs created; counter `merch.dispatch.skipped` | (invisible to buyer) |
| Pre-print refund | System | job `cancelled`; adapter `cancelJob`; buyer sees CANCELED | order page copy |
| Post-print refund | System | partner reprint policy applies; no platform promise | order page copy + policy link |
| Extension beyond caps | API | 422 `extension_invalid` | "Extensions are limited to 90 days and 20 downloads." |
| Callback storm / rate breach | Gateway | 429 + `Retry-After`; queue absorbs | (partner-facing JSON only) |
