# Resume Sharing Links - API Contracts and Payloads (feature 20)

Phase 2 TL · Two owner-scoped helper endpoints in `resume-service` + cross-service rows landed in this change. JSON only; problems follow RFC 9457. Auth/rate rules: `docs/02-architecture-techlead/20-resume-sharing-links/05-security-logging-and-rate-limiting.md`.

## Helper endpoints (`resume-service`, owner-scoped)

| Method & path | Auth | Success | Errors (code) |
|---|---|---|---|
| `GET /api/v1/documents/{id}/share-links` | session | 200 `ShareLinksResponse` (pure read; never mints) | 404 `document_not_found`; 403; 503 `share_unavailable`; 429 |
| `POST /api/v1/documents/{id}/share-links/shorten` | session + CSRF | 200 `ShortenResponse` (mint-or-get; idempotent by `(owner, document)`; `created:true` only on the first call) | 404; 403; 409 `transition_conflict` (not published); 503 `share_unavailable`; 429 |

Derivation inputs: own document row (`is_public`, `published_version`) + feature 06 `by-user` (username) + feature 04 `by-user` (verified hosts, landed in this change) + feature 08 `by-document` (linkage state) + any existing resume-ref short row. `unpublished: true` (no URLs except `publishHint: true`) replaces error states for non-public documents - the dialog shows the publish entry, never dead rows.

## Cross-service rows landed in this change

| Owner | Row | Purpose |
|---|---|---|
| profile-service (feature 04) | `GET /internal/v1/domains/by-user/{user_id}` -> 200 list `{host, status}` (empty when none) | verified-host derivation for the custom row |
| link-service (feature 29) | `POST /internal/v1/links/resume-share` (service JWT) body `{userId, documentId, destination}` -> 200 `{code, shortUrl, created}`; mint-or-get by ref pair, quota-exempt | idempotent mint behind the shorten endpoint |
| link-service (feature 29) | `username.renamed.v1` consumer: rewrite `destination` for rows with `resume_ref_user_id = userId` (old -> new username); purge `links:code:{code}`; idempotent; 301 is the lost-event backstop | rename-proof short links |
| link-service (feature 29) | `V2__resume_refs.sql`: `resume_ref_user_id`, `resume_ref_document_id` + partial unique | idempotency + rewrite targeting |

## Kotlin DTOs (source of truth for the SPA type mirror)

```kotlin
data class ShareLinksResponse(
    val standard: String? = null,          // null when unpublished
    val seoSameAsStandard: Boolean = true, // never a second URL; FE verifies tags on open
    val shortened: String? = null,         // null until the first copy mints
    val custom: String? = null,            // https://{verifiedHost}/cv
    val bioAnchor: String? = null,         // https://{host}/{username}#resume
    val bioLinked: Boolean = false,
    val unpublished: Boolean = false,
)
data class ShortenResponse(val shortened: String, val created: Boolean)
```

## Code catalog

| `code` | HTTP | UI mapping |
|---|---|---|
| `share_unavailable` | 503 | retry banner (profile/domain read or mint dependency failed) |
| `document_not_found` | 404 | reused from feature 17 |
| `transition_conflict` | 409 | reused from feature 17 (shorten on a non-public document) |
| `throttled` | 429 | cooldown text |

No other new codes: shortener failures pass through its own stable catalog; revocation states are data (`unpublished`), not errors.

## Events

This feature emits none; the fan-out consumes existing events (matrix in TL 01). The new inbound subscription (`username.renamed.v1` -> link-service) is registered above and in feature 29's part-2.

## Governance

- Helper response fields additive-only; `seoSameAsStandard` is permanently `true` (a separate SEO URL can never be introduced without a flagged amendment).
- Mint endpoint is idempotent by contract: clients may retry freely; `created` is display-only truth.
- No pricing, quota, or plan fields exist on either surface.
