# Public Profile - API Contracts and Payloads (feature 06)

Phase 2 TL · Dashboard + internal REST contracts. Auth and rate rules: `05-security-logging-and-rate-limiting.md`. JSON only; problem bodies follow RFC 9457.

## Endpoint map

| Method & path | Auth | Request | Success | Errors (code) |
|---|---|---|---|---|
| `GET /api/v1/profile` | session | - | 200 `ProfileResponse` | 404 `profile_absent`; 401 |
| `PUT /api/v1/profile` | session + CSRF; Turnstile on create | `UpdateProfileRequest` | 200 `ProfileResponse` | 422 `username_*`, `display_name_too_long`, `bio_too_long`, `seo_too_long`, `profile_limit_reached`, `challenge_failed`; 429 `throttled` |
| `POST /api/v1/profile/username-check` | session | `UsernameCheckRequest` | 200 `UsernameCheckResponse` | 422 `validation_failed`; 429 |
| `POST /api/v1/profile/username-change` | session + CSRF + Turnstile | `UsernameChangeRequest` | 200 `UsernameChangeResponse` | 422 `username_too_short|too_long|bad_chars|reserved|retired|taken|unchanged`, `challenge_failed`; 429 |
| `GET /internal/v1/profiles/{username}` | service JWT | - | 200 `ProfileResolveResponse` | 401 |
| `GET /internal/v1/profiles:public?cursor=&limit=` | service JWT | - | 200 list of `{username, updatedAt}` | 401 |
| `GET /internal/v1/profiles/by-user/{user_id}` | service JWT | - | 200 `{username}` (first-login handoff, feature 01) | 404 `profile_absent`; 401 |

`user_id` comes from the session only - never from a request body (BR-08).

## Kotlin DTOs (data classes; source of truth for the SPA type mirror)

```kotlin
data class UpdateProfileRequest(
    @field:Size(max = 80) val displayName: String? = null,
    @field:Size(max = 500) val bio: String? = null,
    val isPublic: Boolean? = null,
    val showResume: Boolean? = null,
    @field:Size(max = 70) val seoTitle: String? = null,
    @field:Size(max = 160) val seoDesc: String? = null,
)

data class ProfileResponse(
    val id: String, val username: String, val displayName: String, val bio: String,
    val avatarKey: String?, val themeId: String,
    val isPublic: Boolean, val showResume: Boolean,
    val seoTitle: String?, val seoDesc: String?, val updatedAt: java.time.Instant,
)

data class UsernameCheckRequest(@field:NotBlank val username: String)
data class UsernameCheckResponse(val username: String, val available: Boolean, val reason: String? = null)

data class UsernameChangeRequest(@field:NotBlank val newUsername: String)
data class UsernameChangeResponse(val username: String)

enum class ResolveResult { PROFILE, REDIRECT, MISSING }

data class ProfileResolveResponse(
    val result: ResolveResult,
    val profile: PublicProfileResponse? = null,
    val newUsername: String? = null,
)

data class PublicProfileResponse(
    val username: String, val displayName: String, val bio: String,
    val avatarKey: String?, val themeId: String, val showResume: Boolean,
    val seoTitle: String?, val seoDesc: String?,
)
```

## Problem body (RFC 9457) and code catalog

```json
{ "type": "https://api.example.com/problems/username_retired",
  "title": "Username retired", "status": 422,
  "detail": "That name was used before and stays reserved.", "code": "username_retired" }
```

| `code` | HTTP | UI mapping |
|---|---|---|
| `username_too_short`, `username_too_long`, `username_bad_chars`, `username_reserved`, `username_retired`, `username_taken`, `username_unchanged` | 422 | rename wizard field; `username_taken` on confirm returns to screen 1 |
| `display_name_too_long`, `bio_too_long`, `seo_too_long` | 422 | the respective field under `/app/profile` |
| `profile_limit_reached`, `challenge_failed`, `validation_failed` | 422 | claim screen notice / submit alert |
| `profile_absent` | 404 | first-run claim variant |
| `throttled` | 429 | cooldown text from `Retry-After` |
| `forbidden` / `unauthorized` | 403 / 401 | re-login |

## Public read contract (normative)

Resolution order for `/:username` (executed by feature 10 against the resolve API): cache -> `result=profile` (SSR 200) -> `result=redirect` (301 to `/{newUsername}`) -> `result=missing` (404). Private profiles resolve as `missing` - identical shape and timing class as unknown (BR-04). Deactivated accounts (feature 02) resolve as `missing` within the 60 s `profile.acctstat` bound; the sitemap applies the same filter. Sitemap consumes `profiles:public` (live, `is_public=TRUE` only).

## Events and governance

| Event | Payload | Consumers |
|---|---|---|
| `profile.published.v1` | `username, updatedAt` | feature 10 (page + sitemap purge) |
| `username.renamed.v1` | `oldUsername, newUsername, at` | feature 10 (old-URL 301 cache + sitemap) |

Events are emitted after commit; a lost event heals via the 60 s cache TTL (BR-09). OpenAPI is generated from springdoc; the CI gate is additive-only (no removed fields, no renames); every code above is contract-tested (task T-13). Internal list pagination: opaque cursor, `limit` 1-1000, default 100.
