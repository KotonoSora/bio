# Course Builder - User Stories and Business Rules (feature 24)

Phase 1 BA · Realized by `docs/02-architecture-techlead/24-shop-courses/` (BR-01..BR-17).

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Creator | build curriculum, upload video/materials, and preview as a student | courses host without a third-party platform | Must |
| US-02 | Student | enroll (free one click or after purchase), watch in order, and resume across devices | progress is retained | Must |
| US-03 | Visitor | see preview lessons and the syllabus | I can evaluate the course | Should |
| US-04 | Student | download lesson materials while enrolled | the course is usable offline for resources | Should |
| US-05 | Graduate | show a verifiable certificate | completion is provable | Should |
| US-06 | Creator | choose the completion rule (90%-watched or explicit) | the course fits the audience | Should |

## Business rules

- BR-01 Curriculum model: a `course` product owns ≤ 50 sections → ≤ 200 lessons; dense positions; titles ≤ 120 chars; lesson = video (optional), text body (≤ 50 000 chars sanitized), materials list; the course product payload stays `{completionRule}` with curriculum in its own tables.
- BR-02 Uploads: lesson video MP4 ≤ 2 GB, container + duration validated (readable required); materials via the digital allowlist ≤ 500 MB each, ≤ 10 per lesson; random blob keys; no SVG/HTML.
- BR-03 Previews: 0-10 `is_preview` lessons; preview lesson titles/metadata are SEO-visible on the product page; preview streams need no enrollment; every other lesson byte requires enrollment.
- BR-04 Enrollment: free course (price 0) → logged-in one-click, idempotent; paid → only via grant-confirmed order (same forward seam as feature 23); unique `(product_id, buyer_user_id)`; price changes never alter existing enrollments.
- BR-05 Progress: the player sends a heartbeat every 15-30 s and on pause/seek; server coalesces in Redis and flushes to PostgreSQL ≤ 30 s — never per-second DB writes; effective position = max(stored, coalesced); idempotent per `(product_id, buyer_user_id, lesson_id)`; position clamped to duration + 5 s.
- BR-06 Completion: rule `auto90` (position ≥ 90%) or `explicit`; section/course percentages derive from completed lessons; 100% mints the certificate exactly once (`completed_at` set with the mint).
- BR-07 Certificate: `/c/{code}` public page (display name, course title, completion date); tamper-evident random code; no PDF in v1; revoked (404) on refund; revocation visible ≤ 60 s.
- BR-08 Refunds: enrollment `revoked`; certificate revoked; progress retained for a 90-day courtesy window, then purged; re-enrollment within the window resumes the retained progress.
- BR-09 Deletion: seller purge deletes curriculum, materials, video objects, and dependent enrollments/progress with counts; buyer purge deletes their enrollments + progress and revokes their certificates.
- BR-10 Stream tickets: video tickets are TTL-bound (≤ 4 h) and multi-request (range requests must work); material links are single-use ≤ 15 min; tickets bound to enrollment + lesson; anomaly flagging mirrors feature 23.
- BR-11 Download posture: no download button for video (offline out of v1); materials are download-intended; sharing risk bounded by ticket TTLs + anomaly flags, not DRM promises.
- BR-12 Ownership: all reads/writes scope to owner `user_id` or buyer `buyer_user_id` + RLS; no cross-user path exists (IDOR drill).
- BR-13 Audit: enrollment, revocation, certificate mint/revoke, curriculum publish attempts append-only audit rows; ids only.
- BR-14 Publish precondition: `draft→live` requires ≥ 1 section, ≥ 1 lesson, and every video lesson readable — 409 `publish_precondition_unmet` names the offending lessons.
- BR-15 Rate limits: heartbeat 600/h per enrollment (coalescing keeps DB writes ≤ flush cadence); stream mints 120/h per buyer; enrollments idempotent.
- BR-16 Progress visibility: students read only their own progress; creators see aggregate completion % per course, never lesson positions of named students.
- BR-17 Free-claim abuse: logged-in single-click is the only free path; repeated enroll/revoke cycles rate-limited and audited.
