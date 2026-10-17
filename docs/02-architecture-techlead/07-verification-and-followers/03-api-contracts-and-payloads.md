# Verification Badge and Follower Display - API Contracts and Payloads (feature 07)

Phase 2 TL · Dashboard, ops, and public payload changes. Auth and rate rules: `06-security-logging-and-rate-limiting.md`. JSON only; problem bodies follow RFC 9457 (model of `docs/02-architecture-techlead/06-public-profile/03-api-contracts-and-payloads.md`).

## Endpoint map

| Method & path | Auth | Request | Success | Errors (code) |
|---|---|---|---|---|
| `POST /api/v1/profile/verify-evidence/upload-grant` | session | file metadata (mime, size) | 200 `EvidenceUploadGrant` | 422 `validation_failed` |
| `POST /api/v1/profile/verify-request` | session + CSRF + Turnstile | `VerifyRequestRequest` | 201 `{status: pending}` | 409 `verify_already_pending`; 422 `validation_failed` |
| `GET /api/v1/profile/verify-status` | session | - | 200 `TrustStatusResponse` | 401 |
| `PUT /api/v1/profile/socials` | session + CSRF | `SocialsUpdateRequest` | 200 `SocialsResponse` | 422 `bad_social_url`, `bad_handle`, `follower_count_capped`, `validation_failed`; 429 |
| `POST /api/v1/profile/followers/refresh` | session + CSRF | - | 200 `SocialsResponse` | 429 `throttled` |
| `GET /api/v1/ops/verify/queue?status=&cursor=` | `ROLE_OPS` | - | 200 list of `OpsQueueItem` | 403 |
| `POST /api/v1/ops/verify/{id}/evidence-url` | `ROLE_OPS` | - | 200 `{url, expiresAt}` | 403 |
| `POST /api/v1/ops/verify/{id}/{approve\|reject\|revoke}` | `ROLE_OPS` + CSRF | `DecideRequest` | 200 `{status}` | 422 `validation_failed`; 403 |

## Kotlin DTOs (data classes; source of truth for the SPA type mirror)

```kotlin
enum class VerificationStatus { NONE, PENDING, VERIFIED, REJECTED, REVOKED }

data class EvidenceUploadGrant(val fileName: String, val uploadUrl: String, val expiresAt: java.time.Instant)
data class VerifyRequestRequest(@field:NotBlank val evidenceKey: String)
data class TrustStatusResponse(
    val status: VerificationStatus, val decidedAt: java.time.Instant?,
    val total: Long, val breakdown: Map<String, Long>,
    val stale: Boolean, val updatedAt: java.time.Instant?,
)
data class SocialLink(
    val network: String,                                  // instagram|tiktok|youtube|generic
    @field:NotBlank val handle: String,
    @field:NotBlank val url: String,
)
data class SocialsUpdateRequest(
    val links: List<SocialLink>,
    val selfCounts: Map<String, Long>,                    // network -> count (cap 100_000_000)
)
data class SocialsResponse(
    val total: Long, val breakdown: Map<String, Long>,
    val stale: Boolean, val updatedAt: java.time.Instant?,
    val jumpFlagged: Boolean,
)
data class DecideRequest(@field:NotBlank @field:Size(max = 500) val reason: String)
data class OpsQueueItem(
    val id: String, val username: String, val status: VerificationStatus,
    val createdAt: java.time.Instant, val evidenceRef: String?,
)
```

## Additive extension of the public resolve payload (feature 06 DTO)

`PublicProfileResponse` gains four additive fields (no removals, no renames): `verificationStatus: String`, `followerTotal: Long`, `followerBreakdown: Map<String, Long>`, `followerStale: Boolean`. SSR (feature 10) renders the badge from `verificationStatus == VERIFIED` via the shared SVG component and shows the total with provenance + stale labels. The additive-only CI gate (`docs/02-architecture-techlead/06-public-profile/03-api-contracts-and-payloads.md`, governance section) covers this change.

## Code catalog (in addition to the feature 06 catalog)

| `code` | HTTP | Meaning | UI mapping |
|---|---|---|---|
| `verify_already_pending` | 409 | one open request per profile | pending view (no duplicate form) |
| `bad_handle` / `bad_social_url` | 422 | handle pattern / non-https URL violation | the respective editor row field |
| `follower_count_capped` | 422 | above 100,000,000 | count row, cap restated |
| `validation_failed` | 422 | reason missing, wrong evidence type/size | decide panel / evidence step |

`follower_jump_review` is not an error: the jump is flagged via `jumpFlagged=true` on `SocialsResponse` and stored for ops review (count applied).

## Events and governance

Badge and total changes reuse `profile.published.v1` (`username, updatedAt`) - no new event; consumers: feature 10 (page + sitemap purge); a lost event heals via the 60 s cache TTL. Ops decisions additionally write the audit row (actor + reason). OpenAPI gate: additive-only; every code above is contract-tested in T-12. Ops queue pagination: opaque cursor, `limit` 1-100, default 50.
