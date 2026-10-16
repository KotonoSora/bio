# Course Builder - Architecture and Tech Stack (feature 24)

Phase 2 TL · Feature 24 of 53 — shop 3/5; extends `shop-service` (port 8087, `shop_db`, Redis db 9) — no new service. Implements `docs/01-requirements-ba/24-shop-courses/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`; prior shop files: `docs/02-architecture-techlead/22-shop-catalog/`, `docs/02-architecture-techlead/23-shop-fulfillment/`.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | Courses are a module of `shop-service` (`course` kind + curriculum tables); no player service, no third-party video host. | One transaction domain with catalog + fulfillment (feature 22 D-1 carries); the S0 verdict's substance holds. |
| D-2 | Progress pipeline: heartbeat → Redis hash `course:progress:{productId}:{buyerUserId}` → flusher writes PostgreSQL ≤ 30 s; resume = merged Redis + DB state. Heartbeats never write PostgreSQL per event (legacy "roll up like beacons" mapped). | The beacon posture (feature 12) reused; flush cadence bounds DB load at scale. |
| D-3 | Stream model: video tickets are TTL-bound (≤ 4 h) and multi-request because HTML5 range requests break under single-use URLs; material links stay single-use ≤ 15 min (feature 23 D-4 pattern). Tickets bound to enrollment + lesson; anomaly flagging reused. | Playback correctness + leak bounds are compatible; DRM is explicitly not promised (BR-11). |
| D-4 | No transcoding in v1: MP4 (H.264/AAC) required, validated by a JVM MP4 box/duration parser in-service (no external binaries in the image); unreadable video blocks publish naming the lesson. | DEVIATION FLAGGED vs the legacy transcode/`failed-transcode` wording — mapped to upload validation; a transcode worker is an amendment if demand appears. |
| D-5 | Buyer-email claims do not ship: checkout is session-bound (SSO-only), so account-less purchases do not exist; enrollment binds `buyer_user_id`. DEVIATION FLAGGED vs the legacy P2-3 claim persona; the claim-token machinery is not stubbed. | Consistent with feature 23 Q5; no guest checkout exists in the numbered register. |
| D-6 | Certificate: minted exactly once at 100% completion (unique `cert_code`, tamper-evident 128-bit base32); the public page `/c/{code}` is SSR-rendered by public-site (feature 10 route + cache row land in this change) over `GET /internal/v1/certificates/{code}`; revocation → 404 ≤ 60 s via cache TTL. | The legacy "verifiable page, no PDF" maps cleanly; the reserved `c` slug already exists. |
| D-7 | Publish precondition extends feature 22's kind-aware check: `course` needs ≥ 1 section, ≥ 1 lesson, every video lesson readable; failures name lessons. | One gate, one code (`publish_precondition_unmet`), no half-built courses live. |
| D-8 | Refund lifecycle: consume `order.refunded.v1` (forward, 38) → enrollment + certificate revoked, progress retained (`courtesy_until = now + 90 d`); a sweep purges after the window; re-enrollment within it resumes progress because progress keys on `(product_id, buyer_user_id, lesson_id)`, not on the enrollment row. | Progress survives the enrollment lifecycle without duplication (legacy idempotency key mapped). |
| D-9 | Purge: seller purge removes curriculum + materials + video objects + dependent enrollments with counts; buyer purge removes their enrollments/progress and revokes certificates. Redis overlay keys deleted on both paths. | Buyer PII-free but stateful; seller objects must not outlive the account. |

## Component map

| Piece | Home | Note |
|---|---|---|
| Curriculum CRUD + validation | `shop-service` | full-tree save; dense positions; limits enforced |
| Video/material uploads | `shop-service` | MP4 parse in-service; digital allowlist for materials; keys under `shop-courses/` |
| Enrollment (free) | `shop-service` | idempotent one-click; paid path via grant consumer |
| Progress pipeline | `shop-service` + Redis db 9 | coalesced flush ≤ 30 s |
| Stream/material tickets | `shop-service` | TTL video tickets; single-use material links |
| Certificate mint + internal read | `shop-service` | unique code; revocation on refund |
| Certificate page | `public-site-service` | `/c/{code}` route + `ssr:cert:{code}` (feature 10 cross-edit) |
| Syllabus on product page | `public-site-service` | preview-only read (feature 10 cross-edit) |

## Boundaries

Owns: curriculum, uploads, enrollment lifecycle, progress, completion, certificates, tickets, purge. Does not own: products/listing/pricing (22), POD/digital delivery (23), fees (26), orders/refunds/ledger (32-40), SSR rendering (10), analytics (12).

## Consumers

| Consumer | Contract |
|---|---|
| public-site | syllabus preview read; certificate page read |
| checkout 32-40 | grant/refund events (forward) |
| creator dashboard | aggregate enrollment/completion reads |
| ops | certificate verification via the public page only |
