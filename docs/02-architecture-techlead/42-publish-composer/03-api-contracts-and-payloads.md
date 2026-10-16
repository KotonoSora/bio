# Publish Composer - API Contracts and Payloads (feature 42)

Phase 2 TL · Owner routes + ops console contracts. Auth/rate rules: `docs/02-architecture-techlead/42-publish-composer/05-security-logging-and-rate-limiting.md`. Problems follow RFC 9457.

## Endpoints

| Method & path | Auth | Request | Success | Errors (code) |
|---|---|---|---|---|
| `POST /api/v1/posts/uploads` | session + CSRF | `UploadInitRequest` | `200 UploadInitView` (server-issued presigned parts) | 422 `media_invalid`; 429 |
| `POST /api/v1/posts/uploads/{uploadId}/complete` | session + CSRF | - | `200 {videoKey, thumbKey?}` (gates verified) | 409 `upload_incomplete`; 422 `media_invalid` |
| `POST /api/v1/posts` | session + CSRF | `PostCreateRequest` | `201 PostView` (draft or scheduled) | 422 `media_invalid`, `caption_too_long`, `validation_failed`; 403 `quota_exhausted`; 409 `connection_dead`; 429 |
| `GET /api/v1/posts/{id}` | session | - | `200 PostView` (computed rollup) | 404; 401 |
| `GET /api/v1/posts?from=&to=` | session | - | `200 List<PostView>` (calendar reads post rows) | 401; 429 |
| `PATCH /api/v1/posts/{id}` | session + CSRF | `PostPatchRequest` | `200 PostView` (revision++) | 404; 409 `lock_window`, `dispatch_started`; 422; 429 |
| `DELETE /api/v1/posts/{id}` | session + CSRF | - | `200 {cancelledTargets: Int}` | 404; 409 `dispatch_started`; 429 |
| `POST /api/v1/posts/{id}/targets/{targetId}/retry` | session + CSRF | - | `200 TargetView` | 404; 409 `target_not_retryable`, `confirm_required`; 429 |
| `POST /api/v1/posts/{id}/targets/{targetId}/confirm-repost` | session + CSRF | `{acknowledged: true}` | `200 TargetView` (retry unblocked) | 404; 409 `target_not_retryable`; 429 |
| `POST /api/v1/ops/publish/network-pause` | ops role | `NetworkPauseRequest` | `200 {network, paused}` (audited) | 403; 422; 429 |
| `GET /api/v1/ops/publish/queue`, `/dlq` | ops role | - | `200` depth/lag views / `List<DlqEntry>` | 403; 429 |
| `POST /api/v1/ops/publish/dlq/{id}/replay` | ops role (dual-control on mass) | `{reason, secondOperator?}` | `202` (audited) | 404; 409; 403; 429 |
| `POST /webhooks/social/{network}` | provider scheme (verified) | provider payload (revoke + publish-confirmation event types) | `200` after durable handling | 401 bad signature; 5xx transient |

```kotlin
data class UploadInitRequest(val fileName: String, val sizeBytes: Long, val durationMs: Long)
data class UploadInitView(val uploadId: String, val parts: List<String>) // presigned, server-issued
data class TargetRef(val network: String, val accountId: String)
data class PostCreateRequest(val videoKey: String, val thumbKey: String? = null,
  val caption: String, val trims: Map<String, String> = emptyMap(),
  val targets: List<TargetRef>, val mode: String, val runAt: Instant? = null, val timezone: String)
data class PostPatchRequest(val caption: String? = null, val trims: Map<String, String>? = null,
  val targets: List<TargetRef>? = null, val runAt: Instant? = null)
data class PostView(val id: String, val status: String, val late: Boolean, val runAt: Instant?,
  val revision: Int, val publishedCount: Int, val targetCount: Int, val targets: List<TargetView>)
data class TargetView(val id: String, val network: String, val accountRef: String, val status: String,
  val reason: String?, val errorClass: String?, val confirmRequired: Boolean,
  val providerRef: String?, val attempts: Int)
data class NetworkPauseRequest(val network: String, val paused: Boolean, val reason: String)
```

## Events (publish-service stream `publish.events`)

| Event | Payload | Emitted by | Consumers |
|---|---|---|---|
| `post.published.v1` | `{postId, profileId, late, outcomes[{network, accountId, status}]}` | parent reaching terminal | notification enqueue (creator), ops metrics |
| `post.late.v1` | `{postId, profileId, runAt, lateAt}` | late dispatch | notification enqueue |

Post-commit, at-least-once, idempotent consumers (baseline 03).

## Rules

- Upload: parts are server-issued presigned URLs; object keys server-random; gates (duration ≤ 3 min, size ≤ 300 MB, MP4/H.264 allowlist, magic-byte) verified at init and complete; incomplete uploads expire unpublishable.
- Schedule: synchronous validation is complete — media gates, healthy targets (41), caps, quota, caption limits; success creates the post + one target row per selection; dispatch is async (stream).
- Edit/cancel: allowed until `run_at − publish.policy.lockMinutes`; `dispatch_started` once the scheduler has flipped the parent; cancel flips parent + queued children in one transaction (no dispatch possible).
- Retry: server-side eligibility (`failed|retrying-exhausted`); `confirm_required` on ambiguous-timeout targets with no provider idempotency — the confirm route unblocks exactly one manual retry; `unknown` never auto-retries.
- Ops: pause/resume flips `publish.network_pause` (fresh-read ≤ 5 s, audited); resume replays in `run_at` order; DLQ replay is dual-control on mass (reason + second operator), single replay otherwise; queue/lag views are aggregates + ids only.
- Webhooks: provider publish-confirmation events reconcile target status through the same guarded transitions (idempotent); revoke signals route to 41's health path.
- No token material appears in any DTO, event, message, or problem detail; media preview URLs are signed ≤ 15 min.

## Error catalog additions

| Code | Status | UI Behavior |
|---|---|---|
| `media_invalid` | 422 | Specific limit cited (duration/size/codec/type) |
| `caption_too_long` | 422 | Base 2,200 or per-network trim named |
| `quota_exhausted` | 403 | Reset/upgrade path shown |
| `connection_dead` | 409 | Reconnect CTA (41) |
| `lock_window` | 409 | "Edits freeze 5 minutes before send." |
| `dispatch_started` | 409 | "This post is already going out." |
| `target_not_retryable` | 409 | Disabled-state reason |
| `confirm_required` | 409 | Confirm-before-repost gate |
| `upload_incomplete` | 409 | Resume upload |

Reused: `throttled`, `validation_failed`, `account_not_found` (41).
