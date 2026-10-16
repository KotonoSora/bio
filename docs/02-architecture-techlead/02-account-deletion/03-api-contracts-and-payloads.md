# Account Deletion - API Contracts and Payloads (feature 02)

Phase 2 TL · Browser + internal REST contracts, plus the platform purge participant contract. Auth/rate rules: `docs/02-architecture-techlead/02-account-deletion/05-security-logging-and-rate-limiting.md`. JSON only; problems follow RFC 9457. Step-up code reused from `docs/02-architecture-techlead/01-account-and-access/03-api-contracts-and-payloads.md`.

## Endpoint map (browser-facing)

| Method & path | Auth | Success | Errors (code) |
|---|---|---|---|
| `POST /api/v1/account/delete-request` | session + CSRF + step-up `<= 10 min` | 200 `DeleteStatusResponse` (grace) | 403 `step_up_required`; 422 `confirmation_mismatch`; 429 `throttled` |
| `POST /api/v1/account/delete-cancel` | session + CSRF | 200 `DeleteStatusResponse` (cancelled) | 409 `delete_not_requested`; 410 `purge_completed`; 401 |
| `GET /api/v1/account/delete-status` | session | 200 `DeleteStatusResponse` | 401 |

## Internal endpoints (account-service)

| Method & path | Auth | Success | Errors |
|---|---|---|---|
| `GET /internal/v1/account/status/{userId}` | service JWT | 200 `AccountStatusResponse` | 401 |
| `GET /internal/v1/account/deletion-status/{userId}` | service JWT, ops scope, audited | 200 `DeletionStatusResponse` | 404 `deletion_not_found`; 401 |

## Platform purge participant contract (every service with user rows)

| Method & path | Auth | Success | Errors |
|---|---|---|---|
| `POST /internal/v1/account/purge` | service JWT | 200 `PurgeResponse` (idempotent per `runId`) | 409 `purge_in_progress` (different active run); 401 |
| `GET /internal/v1/account/purge-probe/{userId}` | service JWT | 200 `PurgeProbeResponse` | 401 |

| Participant | Handler scope (each owns its SQL) | Counts |
|---|---|---|
| account-service | `users`, `linked_accounts`, `sessions` (feature 01); concierge contracts/tasks/approvals + staff rows (feature 05) | per-table rows |
| profile-service | `profiles` + retirement tombstone insert for its username; avatar object(s); leads + lead events/notes/tags + export objects (features 27-28 extend) | rows + objects (`docs/02-architecture-techlead/06-public-profile/`) |
| link-service | `links` + QR render rows + cached resolves | rows + objects incl. `qr-renders/` (`docs/02-architecture-techlead/29-short-links/`, `docs/02-architecture-techlead/30-qr-codes/`) |
| analytics-service | day buckets, click rows, owner rollups, link-hit aggregates (feature 31), Redis markers | rows (`docs/02-architecture-techlead/12-profile-analytics/`, `docs/02-architecture-techlead/31-short-link-analytics/`) |
| resume-service | `documents`, `snapshots`, `resume-archives` NDJSON objects (feature 17); `formats`, `format_clones` + thumbnail objects (feature 19) | rows + objects (`docs/02-architecture-techlead/17-resume-documents/`) |
| shop-service | `products`, `product_price_history` + cover objects (feature 22); fulfillment, delivery, courses, booking, and fee rows + objects (23-26 extend) | rows + objects (`docs/02-architecture-techlead/22-shop-catalog/`) |
| billing-service | `orders` + `audit_log` + `ops_config` (feature 33); ledger, refunds, promo, and rail state (35-40 extend); financial retention: PII scrubbed, rows kept | rows (`docs/02-architecture-techlead/32-monetize-overview/`, `docs/02-architecture-techlead/33-checkout-session/`) |
| public-site-service | none (stateless; cache only) | - |
| Later majors (17-47) | register per own spec at migration; appended to this roster | - |

## Kotlin DTOs (source of truth for the SPA type mirror)

```kotlin
data class DeleteRequest(val confirmation: String)   // username exact, else "DELETE"
data class DeleteStatusResponse(val status: String,  // none | grace | cancelled
    val requestedAt: java.time.Instant?, val purgeDueAt: java.time.Instant?, val certificateRef: String?)
data class AccountStatusResponse(val status: String, // active | deactivated | purged
    val purgeDueAt: java.time.Instant?)
data class PurgeRequest(val userId: String, val runId: String)
data class PurgeResponse(val runId: String, val tables: Map<String, Long>, val objects: Long)
data class PurgeProbeResponse(val userId: String, val residualRows: Map<String, Long>, val residualObjects: Long)
data class DeletionStatusResponse(val userId: String, val status: String, val requestedAt: java.time.Instant?,
    val purgeDueAt: java.time.Instant?, val purgedAt: java.time.Instant?, val certificateRef: String?)
```

## Certificate shape (private bucket object)

```json
{ "userId": "u_123", "runId": "2027-03-14T03:30Z", "startedAt": "...", "finishedAt": "...",
  "tables": { "users": 1, "linked_accounts": 2, "sessions": 4, "profiles": 1, "links": 17, "daily_buckets": 240 },
  "objects": 3, "legalHold": false }
```

## Code catalog

| `code` | HTTP | UI mapping |
|---|---|---|
| `step_up_required` | 403 | step-up dialog (feature 01) |
| `confirmation_mismatch` | 422 | confirmation field error |
| `delete_not_requested` | 409 | cancel no-op notice |
| `purge_completed` | 410 | stale tab notice |
| `purge_in_progress` | 409 (internal) | runner retries next cycle |
| `deletion_not_found` | 404 (internal) | support tooling empty state |
| `throttled` | 429 | cooldown text |

## Events

| Event | Payload | Consumers |
|---|---|---|
| `account.deletion_requested.v1` | `userId, username?, at, purgeDueAt` | profile-service (suspend + resolve/sitemap parity), public-site-service (best-effort `ssr:*` purge), features 41-44 (cancel queued work), 36 (stop billing), 45-47 (org path) - forward registrations |
| `account.deletion_cancelled.v1` | `userId, at` | profile-service (unsuspend), public-site-service (purge) |
| `account.purged.v1` | `userId, at, certificateRef` | profile-service (tombstone verification), ops tooling, future consumers |

Events are emitted after commit, at-least-once; lost events self-heal via the 60 s account-status cache bound; consumers are idempotent. Roster discipline: participant list is configuration; dev boot fails on registered-but-unreachable participants.
