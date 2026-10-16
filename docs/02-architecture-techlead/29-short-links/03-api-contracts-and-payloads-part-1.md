# Short Links - API Contracts and Payloads, Part 1 (feature 29)

Phase 2 TL · Owner + public REST contracts. Part 2 (`04-api-contracts-and-payloads-part-2.md`): ops endpoints, internal events, error catalog. JSON only; problem bodies follow RFC 9457. Auth and rate rules: `06-security-logging-and-rate-limiting.md`.

## Endpoint map

| Method & path | Auth | Request | Success | Errors (code) |
|---|---|---|---|---|
| `POST /api/v1/links` | session + CSRF + Turnstile | `MintLinkRequest` | 201 `LinkResponse` | 409 `code_taken`; 422 `destination_blocked`, `validation_failed`; 429 `link_quota_exhausted`, `throttled`; 503 `generation_unavailable`, `blocklist_unavailable` |
| `GET /api/v1/links?cursor=&limit=` | session | - | 200 `LinkPage` + quota headers | 401 `unauthorized` |
| `PATCH /api/v1/links/{code}` | session + CSRF | `UpdateLinkRequest` | 200 `LinkResponse` | 404 `link_not_found`; 422 `destination_blocked`, `immutable_field` |
| `DELETE /api/v1/links/{code}` | session + CSRF | - | 204 (tombstone) | 404 `link_not_found` |
| `GET /go/{code}` (aliases `/r/`, `/a/`) | anonymous | - | 302 live / 301 permanent vanity | 403 suspension page; 404 branded; 410 gone page |
| `POST /internal/v1/links/resume-share` | service JWT | `ResumeShareRequest` | 200 `ResumeShareResponse` (mint-or-get by ref pair; idempotent; quota-exempt - feature 20) | 422 `destination_blocked`, `validation_failed`; 503 `generation_unavailable`, `blocklist_unavailable`; 401 |
| `GET /internal/v1/links/{code}/owner` | service JWT | - | 200 `LinkOwnerView` (`userId`, `status`; tombstoned included — cacheable 300 s) | 404; 401 |

## Kotlin DTOs (data classes; source of truth for the SPA type mirror)

```kotlin
enum class LinkKind { RANDOM, VANITY }
enum class LinkStatus { LIVE, SUSPENDED, EXPIRED, DORMANT }

data class MintLinkRequest(
    @field:NotBlank @field:Size(max = 2048) val destination: String,
    val kind: LinkKind = LinkKind.RANDOM,
    @field:Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,30}[a-z0-9]$") val vanityCode: String? = null,
    val expiresAt: java.time.Instant? = null,
    @field:Size(max = 2048) val fallbackUrl: String? = null,
)

data class UpdateLinkRequest(
    @field:Size(max = 2048) val destination: String? = null,
    val expiresAt: java.time.Instant? = null,
    @field:Size(max = 2048) val fallbackUrl: String? = null,
)

data class LinkResponse(
    val code: String,
    val shortUrl: String,
    val destination: String,
    val kind: LinkKind,
    val status: LinkStatus,
    val expiresAt: java.time.Instant?,
    val fallbackUrl: String?,
    val createdAt: java.time.Instant,
)

data class LinkPage(val items: List<LinkResponse>, val nextCursor: String?)

data class ResumeShareRequest(
    val userId: String, val documentId: String,
    @field:Size(max = 2048) val destination: String,
)
data class ResumeShareResponse(val code: String, val shortUrl: String, val created: Boolean)
```

## Problem body (RFC 9457) and quota headers

```json
{ "type": "https://api.example.com/problems/destination_blocked",
  "title": "Destination blocked", "status": 422,
  "detail": "Destination is on the platform blocklist.", "code": "destination_blocked" }
```

| Header (mint + list responses) | Meaning |
|---|---|
| `X-Link-Quota-Limit` | monthly mint limit for the account |
| `X-Link-Quota-Used` | mints used in the current period |
| `X-Link-Quota-Reset` | ISO-8601 instant of the next reset (00:00 UTC, 1st) |

## Resolve semantics (normative)

| State | Response | `Cache-Control` | Hit event |
|---|---|---|---|
| live | 302 Location=destination | `private, no-store` | yes |
| vanity permanent move | 301 Location=destination | `private, no-store` | first open only |
| dormant with fallback | 302 Location=fallback_url | `private, no-store` | yes |
| expired / dormant without fallback | 410 branded gone page | `private, no-store` | no |
| suspended | 403 branded suspension page | `private, no-store` | no |
| unknown | 404 branded page | `private, no-store` | no |

Order of checks: cache -> PK read -> blocklist re-check -> state compute -> event -> response. Zero database writes on this path (BR-10).
