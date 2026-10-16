# Resume Format Sharing - API Contracts and Payloads (feature 19)

Phase 2 TL · Author + public gallery + ops REST contracts. Auth/rate rules: `docs/02-architecture-techlead/19-resume-format-sharing/05-security-logging-and-rate-limiting.md`. JSON only; problems follow RFC 9457.

## Author endpoints (session, owner-scoped)

| Method & path | Auth | Success | Errors (code) |
|---|---|---|---|
| `GET /api/v1/resume-formats` | session | 200 list of `FormatResponse` (own; grouped by slug with version lists) | 401 |
| `POST /api/v1/resume-formats` | session + CSRF | 201 `FormatResponse` draft | 422; 429 |
| `PUT /api/v1/resume-formats/{id}` | session + CSRF | 200 `FormatResponse` (drafts only; published rows reject `409`) | 404 `format_not_found`; 409 `format_state_conflict`; 422 field-mapped codes |
| `POST /api/v1/resume-formats/{id}/publish` | session + CSRF | 201 `PublishFormatResponse` (validate + scrub + insert vN+1 + delete draft, one tx) | 404; 409; 422 |
| `POST /api/v1/resume-formats/{id}/unpublish` | session + CSRF | 200 `FormatResponse` (status flip; clones unaffected) | 404; 409 |
| `POST /api/v1/resume-formats/{id}/clone` | session + CSRF | 201 `CloneFormatResponse` (`already:false`) or 200 (`already:true`) | 404; 409 (not published) |
| `POST /api/v1/resume-formats/{id}/report` | session or anonymous + Turnstile | 202 (ops queue; reporter-agnostic) | 404; 429 |

## Public gallery (anonymous, throttled, edge-cached)

| Method & path | Auth | Success | Errors |
|---|---|---|---|
| `GET /api/v1/gallery/formats` | none | 200 list of `GalleryCardResponse` (published only; page cursor) | 429 |
| `GET /api/v1/gallery/formats/{slug}` | none | 200 `GalleryDetailResponse` (latest published version + author attribution state) | 404 (missing/removed-shape per contract); 429 |

## Ops (internal, role-gated, audited)

| Method & path | Auth | Success | Errors |
|---|---|---|---|
| `POST /internal/v1/resume-formats/{id}/remove` | service JWT, ops scope | 200 (status `removed`; reason mandatory; purge event) | 404; 422 (no reason); 401 |
| (participant) `POST /internal/v1/account/purge` | service JWT | 200 counts incl. `formats`, `format_clones`, thumbnail objects | 401 |

## Kotlin DTOs (source of truth for the SPA type mirror)

```kotlin
data class CreateFormatRequest(@field:NotBlank val name: String)
data class UpdateFormatRequest(val name: String? = null, val layout: Map<String, Any?>? = null)
data class FormatResponse(val id: String, val slug: String, val name: String, val version: Int, val status: String,
    val layout: Map<String, Any?>, val thumbnailUrl: String? = null, val authorUsername: String,
    val cloneCount: Int, val updatedAt: java.time.Instant)
data class PublishFormatResponse(val slug: String, val version: Int)
data class CloneFormatResponse(val formatId: String, val already: Boolean)
data class GalleryCardResponse(val slug: String, val name: String, val version: Int, val thumbnailUrl: String?,
    val authorUsername: String, val authorResolvable: Boolean, val cloneCount: Int)
data class GalleryDetailResponse(val slug: String, val name: String, val versions: List<Int>,
    val latestVersion: Int, val thumbnailUrl: String?, val authorUsername: String, val authorResolvable: Boolean,
    val cloneCount: Int, val reportable: Boolean)
```

## Code catalog

| `code` | HTTP | UI mapping |
|---|---|---|
| `format_personal_data` / `format_bad_style` / `format_bad_script` / `format_bad_url` | 422 | field-mapped builder errors naming the key path |
| `format_not_found` | 404 | list refresh |
| `format_state_conflict` | 409 | state banner (`already published`, `not published`, etc.) |
| `unknown_template` | 422 | reused from feature 18: foreign/unknown `fmt:` selection |
| `throttled` | 429 | cooldown text |

## Semantics (normative)

- Selection extension: feature 17's `PUT /api/v1/documents/{id}` accepts `template_id = "fmt:{formatId}"` when a clone row for the caller exists; the render pipeline uses the layout interpreter; registry ids behave as in feature 18.
- Attribution: `authorResolvable` is computed via the feature 06 internal resolve at read time (rename -> link to new username; hidden/missing -> false, frozen string non-linked); purge removes rows entirely.
- Clone counts are query-time `COUNT(*)` with a 60 s gallery cache; no counter column exists.
- Removed formats: detail returns the removed-state payload (not a bare 404) so takedown is visibly public; clones keep rendering.

## Events

| Event | Payload | Consumers |
|---|---|---|
| `format.gallery_changed.v1` | `slug, formatId, state (published\|unpublished\|removed), at` | Gallery edge purge (60 s TTL backstop), ops tooling dashboards |

After-commit, at-least-once, idempotent; lost events heal via the gallery TTL.

Governance: additive-only; codes rename-frozen; no charge/pricing fields exist (formats are ungated); updates list in this change: feature 02 participant roster, feature 18's selection semantics note.
