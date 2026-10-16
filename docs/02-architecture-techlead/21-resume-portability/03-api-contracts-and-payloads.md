# Resume Portability - API Contracts and Payloads (feature 21)

Phase 2 TL · Endpoints in `resume-service` (owner-scoped) + two additive internal rows in `profile-service` (landed in this change). JSON only; problems follow RFC 9457. Auth/rate rules: `docs/02-architecture-techlead/21-resume-portability/05-security-logging-and-rate-limiting.md`.

## Endpoint map

| Method & path | Auth | Request | Success | Errors (code) |
|---|---|---|---|---|
| `GET /api/v1/export/resume` | session | - | 200 stream `application/json` (`Content-Disposition: attachment; filename=resume-backup-{date}.json`) | 401; 429 `throttled` |
| `GET /api/v1/export/resume/{documentId}` | session | - | 200 stream (single document) | 404 `document_not_found`; 403; 401; 429 |
| `POST /api/v1/import/resume?dry_run=true\|false` | session + CSRF + Turnstile | multipart `file`; `Idempotency-Key` header (required when `dry_run=false`) | 200 `DryRunResponse` / 201 `ImportResultResponse` | 413 `import_too_large`; 422 `import_malformed`, `import_version_unsupported`, `validation_failed`; 429 `import_quota_exhausted`, `throttled`; 503 `import_unavailable` |
| `POST /api/v1/import/linkedin` | session + CSRF + Turnstile | multipart `file` (LinkedIn export) | 201 `AdapterImportResponse` | 413; 422; 429; 503 `import_unavailable` |
| `POST /api/v1/import/github` | session + CSRF + Turnstile | `GithubImportRequest` | 201 `AdapterImportResponse` | 422 `validation_failed`; 429; 503 `import_unavailable` |

## Kotlin DTOs (source of truth for the SPA type mirror)

```kotlin
data class ExportEnvelope(
    val schemaVersion: String, val exportedAt: Instant,
    val documents: List<ExportDocument>, val blocks: List<ExportBlock>,
)
data class ExportDocument(
    val exportId: String, val title: String, val templateId: String,
    val isPublic: Boolean, val publishedVersion: Int?,
    val snapshots: List<ExportSnapshot>,
)
data class ExportSnapshot(val version: Int, val snapshotJson: Map<String, Any>, val createdAt: Instant)
data class ExportBlock(
    val type: String, val position: Int, val payloadJson: Map<String, Any>,
    val isVisible: Boolean, val visibleFrom: Instant?, val visibleUntil: Instant?,
)
data class DryRunResponse(
    val wouldCreate: WouldCreate, val documents: List<PlannedDocument>,
    val warnings: List<ImportWarning>, val conflicts: List<PublicConflict>,
)
data class WouldCreate(val documents: Int, val snapshots: Int, val blocks: Int)
data class PlannedDocument(val title: String, val snapshots: Int, val isPublicAfter: Boolean)
data class ImportWarning(val path: String, val reason: String)
data class PublicConflict(val title: String, val reason: String)
data class ImportResultResponse(val documents: List<ImportedDocumentRef>, val blocksLinked: Int, val auditAt: Instant)
data class ImportedDocumentRef(val documentId: String, val title: String)
data class GithubImportRequest(@field:Pattern(regexp = "^[a-zA-Z0-9-]{1,39}$") val handle: String)
data class AdapterImportResponse(val documentId: String, val provenance: String, val reviewRequired: List<String>)
```

Jackson: envelope classes serialize snake_case (TL 01 D-3); endpoint DTOs are camelCase.

## Cross-service rows landed in this change (feature 08 contract file)

| Row | Purpose |
|---|---|
| `GET /internal/v1/blocks/resume/by-user/{user_id}` -> 200 list `{id, position, payloadJson, isVisible, visibleFrom, visibleUntil}` (empty when none) | Export block read (resume type only - structural scope guard) |
| `POST /internal/v1/blocks/resume` body `{userId, documentId, position, payloadJson, isVisible, visibleFrom, visibleUntil}` -> 201 `{id}`; validation identical to the owner route (type fixed, closed payload, window rules) | Import relink (phase 2) |

## Code catalog

| `code` | HTTP | UI mapping |
|---|---|---|
| `import_too_large` | 413 | step-A size state (client pre-check mirrors) |
| `import_malformed` | 422 | `Couldn't read this file` + position detail |
| `import_version_unsupported` | 422 | found vs supported versions |
| `validation_failed` | 422 | closed-schema / handle-format details |
| `import_quota_exhausted` | 429 | daily-limit copy |
| `throttled` | 429 | cooldown (gateway burst or in-flight lock) |
| `import_unavailable` | 503 | retry banner; same `Idempotency-Key` |
| `document_not_found` | 404 | reused from feature 17 |

## Events

None emitted, none consumed: imports land non-public (zero cache impact); adapters write private drafts only; the fixture gate is build-time.

## Governance

- Envelope changes bump `schema_version` (TL 02); response DTOs are additive-only.
- `Idempotency-Key` is generated at upload and reused across dry-run, confirm, retry, and post-login re-submit (BA AC-12).
- Adapter responses never carry raw fetched payloads - mapped fields + flags only.
