# Course Builder - Acceptance Criteria (feature 24)

Phase 1 BA · BDD; legacy drills preserved by name.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Gate drill | an unenrolled visitor, a preview lesson, and a gated lesson | lesson bytes are fetched | preview streams free; the gated fetch is 403 `enrollment_required` with zero bytes |
| AC-02 | Resume drill | a mid-lesson stop on device A | the same account opens device B | playback resumes at the stored position (± heartbeat interval); Redis-coalesced state is merged |
| AC-03 | Refund drill | a refunded enrollment | access + certificate checks run | access revoked; `/c/{code}` 404 within 60 s; progress retained 90 days, then purged |
| AC-04 | Publish drill | a course with an unreadable video; a course with no sections | publish is attempted | 409 `publish_precondition_unmet` naming the lessons; zero status change |
| AC-05 | Heartbeat flood drill | 1 s-interval heartbeats for 10 minutes | progress endpoint runs | DB writes ≤ flush cadence (≤ 30 s); envelope 429s beyond 600/h; resume position intact |
| AC-06 | IDOR drill | student B's enrollment + progress rows | student A requests them | 403 + RLS denies; zero rows |
| AC-07 | Enrollment drill | a free course and a paid course | enroll is attempted (free; paid without an order; paid after grant) | free: idempotent 200; without order: 409 `payment_required`; after grant: `paid` enrollment |
| AC-08 | Preview-SEO drill | a published course with 3 preview lessons | the product page is fetched | syllabus + preview metadata SSR-rendered; locked rows show no gated media |
| AC-09 | Materials drill | an enrolled student and a non-student | the material link endpoint is called | enrolled: single-use ≤ 15 min link; non-student: 403; a used link fails |
| AC-10 | Completion/cert drill | `auto90` and `explicit` courses; position at 89% then 91% | completion is evaluated | 89% stays incomplete; 91% completes under `auto90`; certificate mints exactly once |
| AC-11 | Price-change drill | an enrollment followed by a course price edit | the enrollment is re-checked | access unchanged; no re-charge; audit shows the edit |
| AC-12 | Purge drill | a seller with a course; a buyer with enrollments + progress + certificate | each purge runs | seller: curriculum + objects + dependent rows deleted with counts; buyer: enrollments/progress deleted; certificates revoked |
