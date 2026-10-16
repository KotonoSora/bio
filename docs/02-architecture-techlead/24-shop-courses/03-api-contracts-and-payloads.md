# Course Builder - API Contracts and Payloads (feature 24)

Phase 2 TL · Endpoints in `shop-service`; forward money events from 32-40; security: `docs/02-architecture-techlead/24-shop-courses/05-security-logging-and-rate-limiting.md`.

## Endpoint map

| Method & path | Auth | Request | Success | Errors (code) |
|---|---|---|---|---|
| `PUT /api/v1/courses/{productId}/curriculum` | session + CSRF (owner) | `CurriculumSave` (full tree) | 200 `SyllabusResponse` | 404; 409 `curriculum_conflict`; 422 `curriculum_invalid`, `lesson_limit_reached`; 429 |
| `POST /api/v1/courses/{productId}/lessons/{lessonId}/video` | session + CSRF | multipart MP4 | 201 `{videoKey, durationS}` | 404; 422 `video_too_large`, `video_unreadable`; 429 |
| `POST /api/v1/courses/{productId}/lessons/{lessonId}/materials` | session + CSRF | multipart `file` | 201 `MaterialView` | 404; 422 `material_too_large`, `material_bad_type`; 429 |
| `GET /api/v1/courses/{productId}` | session | - | 200 `CourseView` (syllabus + own enrollment + own progress) | 404; 401 |
| `POST /api/v1/courses/{productId}/enroll` | session + CSRF | - | 200/201 `EnrollmentResponse` (idempotent) | 404; 409 `payment_required`; 429 |
| `POST /api/v1/courses/{productId}/lessons/{lessonId}/stream` | session (preview: optional) | - | 200 `StreamTicket` (`singleUse=false`, TTL ≤ 4 h) | 403 `enrollment_required`; 404; 503 `stream_unavailable` |
| `POST /api/v1/courses/{productId}/progress` | session | `ProgressHeartbeat` | 202 accepted (coalesced) | 403; 422 `progress_invalid`; 429 |
| `POST /api/v1/courses/{productId}/lessons/{lessonId}/materials/{materialId}/access-link` | session | - | 200 `StreamTicket` (`singleUse=true`, ≤ 15 min) | 403; 404; 429 |
| `GET /internal/v1/courses/by-product/{productId}/syllabus` | service JWT | - | 200 `SyllabusResponse` (preview fields only; no gated media) | 401 |
| `GET /internal/v1/certificates/{code}` | service JWT | - | 200 `CertificateView` | 404 `certificate_not_found`; 401 |

## Kotlin DTOs

```kotlin
data class SectionView(val id: String, val position: Int, val title: String, val lessons: List<LessonView>)
data class LessonView(val id: String, val position: Int, val title: String, val isPreview: Boolean,
  val durationS: Int?, val hasVideo: Boolean, val materials: List<MaterialView>)
data class MaterialView(val materialId: String, val name: String, val sizeBytes: Long)
data class SyllabusResponse(val productId: String, val sections: List<SectionView>)
data class CourseView(val syllabus: SyllabusResponse, val enrollment: EnrollmentResponse?,
  val progress: List<ProgressRow>)
data class EnrollmentResponse(val id: String, val productId: String, val status: String,
  val source: String, val certCode: String?, val completedAt: Instant?)
data class ProgressRow(val lessonId: String, val positionS: Int, val completed: Boolean)
data class ProgressHeartbeat(val lessonId: String, val positionS: Int, val completed: Boolean? = null)
data class StreamTicket(val url: String, val expiresAt: Instant, val singleUse: Boolean)
data class CertificateView(val code: String, val displayName: String, val courseTitle: String, val completedAt: LocalDate)
```

## Rules

- Enrollment: free path writes the row directly (idempotent on `(product_id, buyer_user_id)`); paid path only via `order.granted.v1` (forward, 32-40); a paid enroll attempt returns 409 `payment_required`.
- Progress: heartbeat clamps `positionS` to `duration + 5`; `completed` is sticky; `auto90` computes completed server-side at flush; flush wakes every ≤ 30 s or on explicit complete.
- Stream: gated lessons require an active enrollment (checked per mint); preview lessons skip the check; tickets carry the lesson id + (when gated) enrollment id; `singleUse` distinguishes video vs materials.
- Certificate: minted in the completion flush (`cert_code` once); revocation on refund (`order.refunded.v1`, forward 38) sets enrollment `revoked` and makes the internal read 404.
- Refund courtesy: `courtesy_until = now + 90 days`; the purge sweep deletes progress only after it passes; re-enrollment resumes (D-8).
- Publish precondition (feature 22 cross-edit): `course` requires ≥ 1 section, ≥ 1 lesson, every video lesson readable — 409 `publish_precondition_unmet` with lesson ids.

## Events

| Direction | Event | Notes |
|---|---|---|
| consumes | `order.granted.v1` | forward, 32-40; paid enrollment |
| consumes | `order.refunded.v1` | forward, 38; revoke + courtesy window |
| emits | none | certificate revocation heals via the 60 s cache TTL |

## Cross-edits landed in this change

| File | Change |
|---|---|
| `docs/02-architecture-techlead/22-shop-catalog/` | registry note (`course {completionRule}` with curriculum tables); publish precondition extended |
| `docs/02-architecture-techlead/10-share-and-seo/` | `/c/{code}` route row; `ssr:cert:{code}` cache key; certificate + syllabus internal consumption rows; read-path note |

## Governance

- Codes appended to the shop catalog; forward event names/shapes confirmed with 32-40 and recorded here.
- Contract tests: enrollment idempotency, gate drill, ticket model (range vs single-use), heartbeat coalescing, mint-once, revocation TTL.
