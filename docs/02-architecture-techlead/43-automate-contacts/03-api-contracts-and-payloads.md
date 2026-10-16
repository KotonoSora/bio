# Automate Contacts - API Contracts and Payloads (feature 43)

Phase 2 TL · Owner + removal-center + ops contracts. Auth/rate rules: `docs/02-architecture-techlead/43-automate-contacts/05-security-logging-and-rate-limiting.md`. Problems follow RFC 9457.

## Endpoints

| Method & path | Auth | Request | Success | Errors (code) |
|---|---|---|---|---|
| `GET /api/v1/contacts?automation=&keyword=&media=&status=&cursor=` | session | - | `200 ContactPage` (owner-scoped, filter-aware) | 401; 429 |
| `POST /api/v1/contacts/{id}/suppress` | session + CSRF | `SuppressRequest` | `200 ContactView` (idempotent) | 404 `contact_not_found`; 429 |
| `POST /api/v1/contacts/export` | session + CSRF | `ExportRequest` | `202 {exportId}` | 422 `validation_failed` (empty filter); 429 `export_throttled` |
| `GET /api/v1/contacts/exports/{exportId}` | session | - | `200 {url, expiresAt}` (signed ≤ 24 h) | 404; 409 `export_not_ready`; 429 |
| `POST /api/v1/contacts/handoff` | session + CSRF | `ExportRequest` | `200 HandoffResult` | 422; 429 `export_throttled` |
| `GET /api/v1/contacts/quota` | session | - | `200 {counted, limit, window}` (shared counting function) | 401 |
| `GET /api/v1/contacts/removal/{token}` | anonymous (signed, expiring) | - | `200 {creatorLabel, state: valid\|expired\|invalid}` | 429 |
| `POST /api/v1/contacts/removal/{token}/suppress` | anonymous (token) | - | `200 {status: suppressed\|already_suppressed}` | 400 `removal_token_invalid`; 429 |
| `GET /api/v1/ops/contacts/aggregates` | ops role | - | `200` aggregates (counts by status/network, quota holds) | 403; 429 |

```kotlin
data class ContactView(val id: String, val networkRefMasked: String, val firstAutomationId: String,
  val firstKeyword: String?, val sourceMediaId: String?, val consentClass: String,
  val consentAt: Instant, val status: String, val firstSeenAt: Instant, val lastSeenAt: Instant)
data class SuppressRequest(val reason: String) // owner_action class
data class ExportRequest(val automationId: String? = null, val keyword: String? = null,
  val mediaId: String? = null, val status: String? = null)
data class HandoffResult(val imported: Int, val skipped: Int, val skipReasons: Map<String, Int>)
```

## Events (publish-service stream `publish.events`)

| Event | Payload | Emitted by | Consumers |
|---|---|---|---|
| `contact.captured.v1` | `{contactId, profileId, automationId, keyword?}` | capture (insert branch) | dashboards; 44 metrics |
| `contact.suppressed.v1` | `{contactId, profileId, reason}` | suppression transition | 44 (stop matching in-flight) |

Post-commit, at-least-once, idempotent consumers (baseline 03).

## Rules

- Reads are owner-scoped (RLS); `networkRefMasked` is the only ref form in API responses; raw refs/emails never leave the service except through the audited CSV/handoff paths.
- Suppress: one transition for owner action; idempotent repeats return 200; exclusion propagates ≤ 5 s to all send paths.
- Export: filter-aware CSV to MinIO; audited with count + filter class; signed URL ≤ 24 h; empty filters refused; rate-limited.
- Handoff: eligible rows only (`email NOT NULL AND status='active'`); `POST /internal/v1/leads` idempotent per row with `source="automation:{automationId}:{keyword?}"`, `sourceRef=contactId`, `marketingConsent=false`, consent copy version copied verbatim; skipped reasons counted; pacing ≤ 10 rows/s.
- Removal center: signed expiring token (no login); invalid/expired states are neutral and enumeration-free; suppression via token lands `reason='removal_center'`.
- Quota: the endpoint reads the shared counting function; no separate counter exists.
- Ops: aggregates only; raw contact access does not exist (by absence).

## Error catalog additions

| Code | Status | UI Behavior |
|---|---|---|
| `contact_not_found` | 404 | Refresh list |
| `export_throttled` | 429 | Retry window copy |
| `export_not_ready` | 409 | "Still building — check again shortly." |
| `removal_token_invalid` | 400 | Neutral expired-link page |
| `marketing_consent_required` | 422 | Caller-side (gated surfaces): "Only consented for automation replies." |

Reused: `throttled`, `validation_failed`.
