# Course Builder - Context and Scope (feature 24)

Phase 1 BA · Feature 24 of 53 — shop 3/5. Legacy source: `shop/courses/course-builder.md` (folded here; deleted with this batch). Realized by `docs/02-architecture-techlead/24-shop-courses/`; extends shop-service (features 22-23).

## Purpose

Host and sell courses without a third-party platform: an outline of sections and lessons, hosted video + materials, free or paid enrollment, and progress that resumes where the student stopped. The course is the product; lessons are its gated content; progress is the retention engine.

## Objectives

| # | Objective |
|---|---|
| O-1 | Creators build curriculum (sections → lessons with video, text body, materials) and preview as a student. |
| O-2 | Free courses enroll in one click; paid courses enroll only through a grant-confirmed order; price changes never alter existing enrollments. |
| O-3 | Playback progress resumes across devices from server-stored position with no per-second database writes. |
| O-4 | Preview lessons are free and SEO-visible; gated lesson bytes stream only to enrolled students. |
| O-5 | Completion mints a verifiable certificate page; refunds revoke access and the certificate while preserving progress through a courtesy window. |

## Personas / RBAC

| Role | CRUD Permissions | Description |
|---|---|---|
| Creator (owner) | C/R/U/D on own curriculum + materials; R own enrollments/progress | builds, prices, previews, publishes |
| Student | R own enrollment + progress; W own progress | enrolls, watches, resumes, downloads materials |
| Visitor | R syllabus preview fields only; R preview-lesson streams | sees preview lessons; gated bytes denied |
| Ops / verifier | R certificate public page | verifies certificates, no edit path |
| Anonymous | none beyond the public surfaces | deny-by-default |

## In scope

Curriculum CRUD + publish precondition; video/materials uploads (validated pipeline); preview gating + SEO syllabus; free-claim + paid-grant enrollment; heartbeat progress (queued, coalesced) + resume; completion rules + percentages; certificate mint + public verification page + revocation; refund lifecycle; account-purge participant (seller rows + buyer scrub).

## Out of scope

Catalog/listing/pricing (22); one-time file delivery without progress (23); fees (26); checkout/orders/refunds/ledger (32-40); drip scheduling, quizzes, cohort/community features (v2, named); offline viewing (v1 out); native players (web PWA only); PDF certificates + seals (amendment; print via `window.print()`); buyer-email claims (see Q3).

## Open questions (resolved)

| Q | Resolution |
|---|---|
| Q1 video caps | Lesson video ≤ 2 GB / ≤ 240 min; ≤ 10 materials per lesson (≤ 500 MB each, feature 23 allowlist); ≤ 50 sections and ≤ 200 lessons per course; per-course video total ≤ 20 GB. |
| Q2 certificate weight | Verifiable page only in v1 (name, course, date, tamper-evident code); PDF/envelope seals are a later amendment; print via browser. |
| Q3 buyer-email claims | Not shipped — DEVIATION FLAGGED: checkout is session-bound on an SSO-only platform, so account-less purchases do not exist; enrollment binds `buyer_user_id`. The claim-token pattern returns only if guest checkout ever ships in a numbered feature. |
| Q4 transcoding | No transcode pipeline in v1: MP4 only, validated on upload (container + duration); "failed-transcode" maps to unreadable-video blocking publish, lesson named. |
| Q5 completion rule | Creator-chosen per course: `auto90` (default; position ≥ 90% marks complete) or `explicit` (student taps complete). |
| Q6 preview count | Creator picks 0-10 preview lessons; they stream free and carry SEO metadata on the product page. |
