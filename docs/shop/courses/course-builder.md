# Course Builder (free + paid courses, hosted content)

PLAN ref: P2-3 (course sections/lessons with `mp4_r2_key` + materials, buyer-email claim, progress resume). Access control follows the entitlement pattern in `digital/digital-delivery.md`.

## Purpose

Let creators host and sell courses without a third-party platform: outline of sections and lessons, hosted video + materials, free or paid enrollment, and progress that resumes where the student stopped. The course is the product; lessons are its gated content; progress is the retention engine.

## Users

- Creator: builds curriculum (sections → lessons), uploads video/materials, prices the course (free or paid), previews as a student.
- Student: enrolls (buy or free-claim), watches in order, resumes last position across devices, downloads lesson materials.
- Buyer-email claimant (P2-3 pattern): accesses with email + claim token where account-less purchase must still resolve.

## Functional requirements

1. Curriculum model: `course` product → ordered `sections` → ordered `lessons{title, video_r2_key?, materials[], duration_s?, is_preview}`. Lessons support video (MP4 in R2, streamed via signed URLs), text body, and file attachments (same validated-upload pipeline as digital). Preview lessons (configurable count) are free and SEO-visible; the rest gate on enrollment.
2. Free vs paid: free courses enroll by claim (logged-in one click, or buyer-email claim with token); paid courses enroll via standard checkout (order grant → enrollment row). Price changes never alter existing enrollments.
3. Progress: `lesson_progress{enrollment_id, lesson_id, position_s, completed}` updated by the player heartbeat (throttled, queued — never per-second PG writes; roll up like beacons). Resume-last-lesson banner on the course page; cross-device resume via server-stored position. Completion marks (explicit or 90%-watched rule, creator-chosen) drive section/course completion %.
4. Certificates (v1 lite): completion state + verifiable certificate page (`/c/:cert_code`, name + course + date) — no PDF in v1 (print via `window.print()` CSS if needed, consistent with the resume-print precedent). Tamper-evident codes, revocation on refund.
5. Access lifecycle: paid-course refunds revoke enrollment (progress retained for re-enrollment courtesy window per policy); account deletion purges progress per retention rules. Drip scheduling (unlock over days) is named v2 — v1 unlocks all enrolled lessons immediately.
6. Quality floor: video upload caps + duration limits (see Q1); failed-transcode or missing-file lessons block course publishing with the lesson named (no half-built courses go live).

## Data / API touches

- New tables: `course_sections{course_product_id, position, title}`, `course_lessons{section_id, position, title, video_r2_key?, body?, materials[], duration_s, is_preview}`, `enrollments{course_product_id, student_key, status, cert_code?}`, `lesson_progress{enrollment_id, lesson_id, position_s, completed}` (standard `user_id`/RLS or buyer-email-claim scoping per P2-3; progress writes queued/batched, idempotent on `(enrollment_id, lesson_id)`).
- Endpoints: curriculum CRUD + publish (owner, validates completeness), enroll/claim, lesson stream (entitlement-checked signed URLs), progress heartbeat (throttled queue), certificate page (public, code-addressed). All PG via `worker/db/*`.
- Player: lightweight HTML5 player page; heartbeat every 15–30 s + on pause/seek (never per-second); offline viewing explicitly out of v1.

## Acceptance criteria

- Gate drill: unenrolled fetch of gated lesson bytes gets 403; preview lessons stream free; enrollment (free-claim and paid-grant) unlocks within pipeline lag.
- Resume drill: stop mid-lesson on device A, resume on device B at stored position (±heartbeat interval tolerance).
- Refund drill: refunded enrollment revokes access and certificate validity; progress rows retained per courtesy window, then purged.
- Publish drill: course with missing video files refuses publish naming the lessons; heartbeat flood (1 s interval) throttled without PG write amplification.
- IDOR drill: student A progress/enrollment reads for student B return 403.

## Out of scope

- Catalog/listing (see `products/product-catalog.md`); fees (see `pricing/sales-fees.md`); one-time file delivery without progress (see `digital/digital-delivery.md`).
- Checkout/refunds/receipts (`monetize/payment/`, `monetize/billing/`); drip schedules, quizzes, cohort features (v2); native mobile players (PWA + web player only).

## Open questions

- Q1: Video caps at launch (per-lesson minutes/MB, per-course storage quota per tier)?
- Q2: Certificate weight (verifiable page enough, or PDF + unique seal expected by VN employers)?
