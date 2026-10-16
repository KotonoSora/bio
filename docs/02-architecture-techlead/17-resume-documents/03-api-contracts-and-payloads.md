# Resume Documents - API Contracts and Payloads (feature 17)

Phase 2 TL · Owner + internal REST contracts. Auth/rate rules: `docs/02-architecture-techlead/17-resume-documents/05-security-logging-and-rate-limiting.md`. JSON only; problems follow RFC 9457.

## Owner endpoints (`resume-service`, dashboard class)

| Method & path | Auth | Success | Errors (code) |
|---|---|---|---|
| `GET /api/v1/documents` | session | 200 list of `DocumentResponse` | 401 |
| `POST /api/v1/documents` | session + CSRF | 201 `DocumentResponse` (v0, no snapshot) | 422; 429 |
| `GET /api/v1/documents/{id}` | session | 200 `DocumentResponse` | 404 `document_not_found`; 403 |
| `PUT /api/v1/documents/{id}` | session + CSRF | 200 `DocumentResponse` (title / `templateId`; never touches versions) | 404; 422 (incl. `unknown_template` - feature 18 registry); 403 (`template_forbidden` - premium gate, feature 18) |
| `DELETE /api/v1/documents/{id}?confirmed=` | session + CSRF | 204 (hide/unlink first, then delete) | 404; 422 `delete_needs_confirm`; 403 |
| `POST /api/v1/documents/{id}/snapshot` | session + CSRF | 201 `SaveSnapshotResponse` | 404; 422 `snapshot_too_large`/`validation_failed`; 503 `capture_unavailable`; 429 |
| `GET /api/v1/documents/{id}/versions` | session | 200 list of `VersionSummaryResponse` | 404; 403 |
| `GET /api/v1/documents/{id}/versions/{v}` | session | 200 `VersionResponse` (`archived: true` when blob-served, `json` may be null until fetch completes) | 404 `version_not_found`; 403 |
| `POST /api/v1/documents/{id}/duplicate` | session + CSRF | 201 `DocumentResponse` (`{title} copy`, latest snapshot as v1 or empty draft) | 404; 429 |
| `POST /api/v1/documents/{id}/publish` | session + CSRF | 200 `DocumentResponse` (idempotent-safe; displaced document named by the client confirm) | 404; 422 `snapshot_required`/`version_not_found`; 429 |
| `POST /api/v1/documents/{id}/unpublish` | session + CSRF | 200 `DocumentResponse` (both flags cleared) | 404 |

`user_id` is server-set; `profile_id` resolves from the owner's profile server-side. Body of publish: `{version}`.

## Linkage endpoints (owned by `profile-service`; additive rows landed in feature 08's contract in this change)

| Method & path | Auth | Success | Errors |
|---|---|---|---|
| `PATCH /api/v1/blocks/{id}/link-document` | session + CSRF | 200 `BlockResponse` (`{documentId?}` binds/unbinds without touching other payload fields) | 404; 422; 403 |
| `GET /internal/v1/blocks/by-document/{documentId}` | service JWT | 200 `{linked, visible, blockId?, username?}` | 401 |
| `POST /internal/v1/blocks/{id}/unlink-document` | service JWT | 204 (idempotent: re-run is a no-op) | 401 |

## Internal endpoints (`resume-service`)

| Method & path | Auth | Success | Errors |
|---|---|---|---|
| `GET /internal/v1/resumes/{username}/published` | service JWT | 200 `PublicResumeResponse` (published version payload for `/cv` SSR - lands feature 10's reserved contract) | 404 `resume_absent` (missing/private/draft-only/retired - parity shape); 401 |
| (participant) `POST /internal/v1/account/purge` | service JWT | 200 counts: `documents`, `snapshots`, `objects` (archive NDJSON) | 401 |

## Kotlin DTOs (source of truth for the SPA type mirror)

```kotlin
data class CreateDocumentRequest(@field:NotBlank val title: String)          // <= 120
data class UpdateDocumentRequest(val title: String? = null, val templateId: String? = null)
data class DocumentResponse(val id: String, val title: String, val templateId: String, val isPublic: Boolean,
    val publishedVersion: Int? = null, val latestVersion: Int? = null, val updatedAt: java.time.Instant)
data class SaveSnapshotRequest(val draft: Map<String, Any?>)                 // closed schema validated server-side
data class SaveSnapshotResponse(val version: Int)
data class VersionSummaryResponse(val version: Int, val createdAt: java.time.Instant, val archived: Boolean)
data class VersionResponse(val version: Int, val createdAt: java.time.Instant, val archived: Boolean,
    val json: Map<String, Any?>? = null)
data class PublishRequest(val version: Int)
data class PublicResumeResponse(val documentId: String, val version: Int, val templateId: String,
    val snapshotJson: Map<String, Any?>, val publishedAt: java.time.Instant)
```

## Code catalog

| `code` | HTTP | UI mapping |
|---|---|---|
| `document_not_found` | 404 | list refresh |
| `version_not_found` | 404 | timeline refresh |
| `delete_needs_confirm` | 422 | linkage confirm dialog |
| `snapshot_required` | 422 | save-before-publish guidance |
| `snapshot_too_large` | 422 | section-level size guidance |
| `capture_unavailable` | 503 | retry banner; draft intact |
| `resume_absent` | 404 internal | `/cv` parity parity (feature 10 consumer) |
| `throttled` | 429 | cooldown text |

## Events

| Event | Payload | Consumers |
|---|---|---|
| `resume.cv_changed.v1` | `userId, username, public, documentId, version?, at` | public-site-service: delete `ssr:{host}:{slug}:cv` for the username (all hosts); emitted on publish, unpublish, and template switch (feature 18); self-heals via the 60 s TTL; idempotent |

## Governance

- Additive-only; codes rename-frozen; `document_not_found` and `delete_needs_confirm` carry over legacy names where semantics match (renames forbidden).
- Cross-edits in this change: feature 10's cache/read rows (this contract now consumed), feature 08's linkage rows, feature 02's participant table (resume-service row).
- `schema_version` in `snapshot_json` is versioned for feature 21's portability proofs; this feature's validator is authoritative for it.
