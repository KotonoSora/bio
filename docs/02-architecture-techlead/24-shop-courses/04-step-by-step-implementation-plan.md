# Course Builder - Step-by-Step Implementation Plan (feature 24)

Phase 2 TL · Dependency-ordered; acceptance goals cite `docs/01-requirements-ba/24-shop-courses/06-acceptance-criteria.md`.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `V3__shop_courses.sql` + RLS + grants; dense-position rebuild helpers (feature 22 pattern) | Feature 23 T-01 | TL 02 drills 1-2, 5-6 green |
| T-02 | BE | Curriculum CRUD (`PUT` full tree, limits, `curriculum_conflict` CAS on `updated_at`), owner + svc reads | T-01 | AC-08 (data half) green |
| T-03 | BE | Uploads: MP4 parse in-service (box + duration, ≤ 2 GB / 240 min), materials allowlist (≤ 500 MB x 10), random `shop-courses/` keys | T-02 | AC-04 (upload half) green |
| T-04 | BE | Publish precondition hook (feature 22 cross-edit): course completeness, lesson ids in the problem body | T-03 | AC-04 green |
| T-05 | BE | Enrollment: free idempotent path; paid via `order.granted.v1` (forward); refund consumer revoke + `courtesy_until`; sweep job | T-01; 32-40 forward seams | AC-03, AC-07, AC-11 green |
| T-06 | BE | Progress pipeline: Redis coalescing, ≤ 30 s flusher (`GREATEST`, sticky completed, auto90), merged resume read, heartbeat envelope | T-05 | AC-02, AC-05 green |
| T-07 | BE | Tickets: video (TTL ≤ 4 h, multi-request) + material (single-use ≤ 15 min) mints with enrollment checks; anomaly hooks | T-05 | AC-01, AC-09 green |
| T-08 | BE | Certificate mint (mint-once) + internal read; public-site `/c/{code}` row (feature 10 cross-edit); revocation on refund | T-06 | AC-10, AC-03 (cert half) green |
| T-09 | FE | Builder (outline drag, uploads, preview toggle, rule select), player (video, resume banner, lesson list, materials, cert banner), product-page syllabus states | T-02..T-08 | BA 04 states render; AC-08 UI half |
| T-10 | DevOps/QA | Drill wiring AC-01..AC-12; container drill (Docker + Podman; V2→V3 replay; rollback); OpenAPI diff; secrets grep | T-04..T-09 | G4 + G5 + G7 green |

## Notes

- Build-order gate: the paid path + refund consumer enable only when 32-40 ships the forward seams; the free path, builder, and player ship independently (AC-07 free half testable early).
- No transcode worker, no email-claim worker, no drip scheduler in this change (D-4/D-5 and BR-05 note v2 items).
- Progress I/O budget: one Redis write per heartbeat; one PostgreSQL upsert batch per flush per enrollment; resume read = one merge.
- Legacy S0 exit-drill mapping: resume-last-lesson drill → AC-02 + AC-05; gate/refund/IDOR drills → AC-01/AC-03/AC-06.
- Redis db 9 keys ship here: `course:progress:{productId}:{buyerUserId}` (hash); purge deletes the keys with the rows.
