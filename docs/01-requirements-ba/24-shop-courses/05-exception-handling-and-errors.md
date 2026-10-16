# Course Builder - Exception Handling and Errors (feature 24)

Phase 1 BA · Codes are stable; UI copy localizes vi/en.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Curriculum over limits | API | 422 `lesson_limit_reached` (50 sections / 200 lessons) | "Courses allow up to 50 sections and 200 lessons." |
| Malformed curriculum tree | API | 422 `curriculum_invalid`; zero writes | "The course outline could not be saved." |
| Concurrent builder save | API | 409 `curriculum_conflict`; client refetches | "Someone edited this course — refresh and retry." |
| Video over 2 GB | API | 422 `video_too_large`; no blob write | "Lesson videos must be 2 GB or smaller." |
| Unreadable/non-MP4 video | API | 422 `video_unreadable` | "Upload a playable MP4 (H.264/AAC)." |
| Material over 500 MB / bad type | API | 422 `material_too_large` / `material_bad_type` | "Materials must be 500 MB or smaller." / "This file type isn't allowed." |
| Publish with incomplete curriculum | API | 409 `publish_precondition_unmet`; lessons named | "Finish these lessons before going live: {list}." |
| Gated lesson fetch without enrollment | API | 403 `enrollment_required`; zero bytes | "Enroll to watch this lesson." |
| Paid course enroll without order | API | 409 `payment_required`; checkout hinted | "Purchase this course to enroll." |
| Duplicate free enroll | API | 200 with the existing enrollment (idempotent) | (no dialog) |
| Re-enroll after refund within window | API | new enrollment resumes retained progress | "Welcome back — your progress is still here." |
| Heartbeat flood | API | coalesced; rate envelope 600/h per enrollment; 429 beyond | "Slow down — your progress is still saving." |
| Progress for an unknown lesson | API | 422 `progress_invalid`; nothing stored | (silent client retry) |
| Stream ticket for a missing file | API | 503 `stream_unavailable` | "This lesson is temporarily unavailable." |
| Unknown or revoked certificate | Render | 404 parity; no holder data | branded not-found page |
| Certificate after refund | Render | 404 within ≤ 60 s of revocation | branded not-found page |
| Cross-user progress/enrollment read | API | 403 + RLS denies | "You don't have access to this." |
| Material link reuse | API | 403 after first use or expiry | "This link expired — open the lesson again." |
