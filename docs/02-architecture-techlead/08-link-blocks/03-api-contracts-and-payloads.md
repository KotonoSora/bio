# Link Blocks - API Contracts and Payloads (feature 08)

Phase 2 TL · Dashboard + internal REST contracts. Auth and rate rules: `05-security-logging-and-rate-limiting.md`. JSON only; problem bodies follow RFC 9457 (model of `docs/02-architecture-techlead/06-public-profile/03-api-contracts-and-payloads.md`).

## Endpoint map

| Method & path | Auth | Request | Success | Errors (code) |
|---|---|---|---|---|
| `GET /api/v1/blocks?profileId=` | session | - | 200 list of `BlockResponse` (computed `hiddenReason`) | 403 |
| `POST /api/v1/blocks` | session + CSRF | `CreateBlockRequest` | 201 `BlockResponse` | 422 `unknown_block_type`, `bad_url_scheme`, `title_too_long`, `payload_too_long`, `bad_schedule_window`, `block_limit_reached`; 403; 429 |
| `PATCH /api/v1/blocks/{id}` | session + CSRF | `UpdateBlockRequest` (partial) | 200 `BlockResponse` | 404 `block_not_found`; 422 `validation_failed` (incl. type change), field codes; 403 |
| `DELETE /api/v1/blocks/{id}` | session + CSRF | - | 204 | 404; 403 |
| `POST /api/v1/blocks/reorder` | session + CSRF | `ReorderRequest` | 200 re-sequenced list of `BlockResponse` | 422 `reorder_mismatch`; 403; 429 |
| `GET /internal/v1/profiles/{username}/blocks` | service JWT | - | 200 list of `PublicBlockResponse` (windowed, ordered) | 404 `profile_absent`; 401 |
| `PATCH /api/v1/blocks/{id}/link-document` | session + CSRF | `{documentId?}` | 200 `BlockResponse` | 404 `block_not_found`; 422; 403 |
| `GET /internal/v1/blocks/by-document/{documentId}` | service JWT | - | 200 `{linked, visible, blockId?, username?}` | 401 |
| `POST /internal/v1/blocks/{id}/unlink-document` | service JWT | - | 204 (idempotent: hides the block + clears `documentId`) | 401 |
| `GET /internal/v1/blocks/resume/by-user/{user_id}` | service JWT | - | 200 list of resume-block rows `{id, position, payloadJson, isVisible, visibleFrom, visibleUntil}` (feature 21 export; empty when none) | 401 |
| `POST /internal/v1/blocks/resume` | service JWT | `{userId, documentId, position, payloadJson, isVisible, visibleFrom, visibleUntil}` | 201 `{id}` (feature 21 import relink; validation identical to the owner route: type fixed, closed payload, window rules) | 422 `validation_failed`; 401 |

## Kotlin DTOs (data classes; source of truth for the SPA type mirror)

```kotlin
enum class BlockType { LINK, SOCIAL, GALLERY, VIDEO, MUSIC, EVENT, FORM, PRODUCT, COURSE, TIP, RESUME, CONTACT, EMBED }
enum class HiddenReason { VISIBLE, HIDDEN, SCHEDULED_FUTURE, EXPIRED }

data class CreateBlockRequest(
    @field:NotBlank val profileId: String,
    val type: BlockType,
    val payload: Map<String, Any?> = emptyMap(),
    val isVisible: Boolean = true,
    val visibleFrom: java.time.Instant? = null,
    val visibleUntil: java.time.Instant? = null,
)
data class UpdateBlockRequest(                       // no `type` field exists - immutability by shape
    val payload: Map<String, Any?>? = null,
    val isVisible: Boolean? = null,
    val visibleFrom: java.time.Instant? = null,
    val visibleUntil: java.time.Instant? = null,
)
data class ReorderRequest(@field:NotBlank val profileId: String, @field:NotEmpty val ids: List<String>)
data class BlockResponse(
    val id: String, val type: BlockType, val position: Int,
    val payload: Map<String, Any?>, val isVisible: Boolean,
    val hiddenReason: HiddenReason,
    val visibleFrom: java.time.Instant?, val visibleUntil: java.time.Instant?,
    val updatedAt: java.time.Instant,
)
data class PublicBlockResponse(val id: String, val type: BlockType, val position: Int, val payload: Map<String, Any?>)
```

`PATCH` carrying a `type` key is rejected `422 validation_failed` (immutability enforced in the service, not only by DTO shape). No request DTO has a `position` field - positions are server-assigned.

## Semantics (normative)

- **Payload schemas (validator).** link `{url https required, title <= 120, size in small|medium|big|button, thumbnailKey?}`; social `{network, url}`; embed `{provider, url}`; resume `{documentId, showContact?, sectionOrder?[]}`; gallery `{items[{url, alt}]}`; video/music/event/contact `{title <= 120?, url? https}` with per-type size caps; form `{headline <= 120, offerCopy? <= 280, fields[] from the closed menu (email required; name, phone, note optional), submitLabel <= 40, successMessage <= 200, redirect? (own page or link-block target), doubleOptIn bool, copyVersion}` (`copyVersion` server-stamped on every save; capture semantics: `docs/02-architecture-techlead/27-lead-forms/`); product/course/tip `{productId}` (feature 22 binding: availability resolves at render via feature 22's public read; non-live or foreign targets render the unavailable state). URLs: `https:` + non-empty host; `http:`/`javascript:`/`data:` rejected. Every string sanitized (script/iframe/event-handler stripped) before storage.
- **Reorder.** Transaction: set-equality assert (`set(ids) == set(profile block ids)`, else `422 reorder_mismatch`, zero writes) -> positions 0..n batch update -> one audit row -> one event. Duplicate: copy inserted at `position + 1` with followers shifted in the same batch.
- **Window.** Public predicate: `is_visible AND (visible_from IS NULL OR now >= visible_from) AND (visible_until IS NULL OR now < visible_until)`; order `position ASC, id ASC`. Expired/hidden/future blocks are excluded server-side, never client-side.
- **Internal read.** Windowed `PublicBlockResponse` list keyed by username; `profile_absent` (404, equal shape/timing) for missing, private, suspended, or retired profiles - reuses the feature 06 code so the renderer's behavior matches resolve.
- **Linkage (feature 17).** `link-document` touches only `payloadJson.documentId`; `unlink-document` is idempotent and hides the block; resume-service orchestrates delete order (unlink before document delete) - profile-service never deletes documents.

## Events and governance

Every mutation publishes `profile.published.v1` (`username, updatedAt`) after commit; consumers: feature 10 (page + sitemap purge); lost events heal via the 60 s TTL. Reorder publishes exactly one event. OpenAPI gate: additive-only; every code above is contract-tested in T-10.
