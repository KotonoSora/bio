# Short Links - API Contracts and Payloads, Part 2 (feature 29)

Phase 2 TL · Ops endpoints, internal events, error catalog, contract governance. Part 1 (`03-api-contracts-and-payloads-part-1.md`): owner + public contracts.

## Ops endpoints (audited; role `ROLE_OPS` re-verified per request)

| Method & path | Request | Success | Errors |
|---|---|---|---|
| `POST /api/v1/ops/links/report` | `ReportRequest` + Turnstile | 202 `{"reportId": "<uuid>"}` | 429 `throttled` |
| `POST /api/v1/ops/links/{code}/suspend` | `SuspendRequest` (reason mandatory) | 200 `LinkResponse` (status `suspended`) | 404 `link_not_found`; 403 |
| `GET /api/v1/ops/links/{code}` | - | 200 full row + audit trail ref | 404 |

```kotlin
data class ReportRequest(val code: String, val reason: String)
data class SuspendRequest(@field:NotBlank val reason: String)
```

Bulk minting is not an endpoint in v1: it runs as an audited ops job (task T-14) through the same validation path as `POST /api/v1/links`; a creator token hitting it gets 403 + audit (AC-06).

## Internal events (queue)

| Event | Queue `links` | Payload (JSON) | Consumer |
|---|---|---|---|
| `link.hit.v1` | `hits` (stream `links.hits`) | `code, ts, ipHash, ipHashSaltId, refererHost, userAgentClass, country, purposeHint?` | feature 31 (analytics) |
| `link.suspended.v1` | `purge` | `code, at, reason` | cache purge (link-service self + Nginx edge purge hook) |

Rules: events are emitted after the response decision; `ipHash` = salted SHA-256 with 24 h window rotation; no full URLs, no raw IPs, no user identifiers on the hit stream (BR-10, NFR security row). `purposeHint` (feature 31 filter input) carries the prefetch/prerender hint when the request declared one.

Inbound (feature 20 extension): `username.renamed.v1` -> rewrite `destination` for rows with `resume_ref_user_id = userId` (old username -> new; canonical cv URL shape `https://{host}/{username}/cv`) + purge `links:code:{code}`; slug never re-minted. Idempotent; the old-username 301 is the lost-event backstop.

## Error code catalog (stable; additive-only changes)

| `code` | HTTP | Meaning | UI message source |
|---|---|---|---|
| `validation_failed` | 422 | field shape invalid | `docs/01-requirements-ba/29-short-links/04-ui-data-dictionary.md` |
| `destination_blocked` | 422 | blocklist hit at mint | BA 05 error matrix |
| `code_taken` | 409 | vanity already used | BA 05 |
| `immutable_field` | 422 | attempted change of `code`/`kind`/owner | BA 05 |
| `link_not_found` | 404 | unknown or not owned (no state oracle) | BA 05 |
| `link_quota_exhausted` | 429 | monthly mint quota hit | BA 05 |
| `throttled` | 429 | rate limit hit | BA 05 |
| `generation_unavailable` | 503 | 5 collision retries exhausted | BA 05 |
| `blocklist_unavailable` | 503 | safety dependency down; fail closed | BA 05 |
| `unauthorized` / `forbidden` | 401 / 403 | session or role missing | `07-security-logging-and-rate-limiting.md` |

## Contract governance

- OpenAPI generated from springdoc; CI gate: diff vs previous release must be additive-only (no removed fields, no new mandatory request fields, no code renames).
- Every error response above is contract-tested (task T-16) against the exact `code` string.
- The SPA type mirror is generated from OpenAPI; the Kotlin DTOs in part 1 are the source of truth.
- Pagination: `cursor` = opaque base64 of the last code; `limit` 1-100, default 20.
