# Course Builder - Non-Functional Requirements (feature 24)

Phase 1 BA · Every threshold numeric.

## Performance

| # | Requirement |
|---|---|
| NFR-01 | Stream-ticket mint p95 ≤ 300 ms; ticket TTL ≤ 4 h (video, range-friendly); material links single-use ≤ 15 min. |
| NFR-02 | Heartbeat accept p95 ≤ 150 ms (Redis write); PostgreSQL flush ≤ 30 s; resume read p95 ≤ 300 ms merged. |
| NFR-03 | Syllabus render p95 ≤ 300 ms (SSR, cached on the product page key); builder save p95 ≤ 500 ms for a full tree. |
| NFR-04 | Certificate page p95 ≤ 200 ms; revocation visible ≤ 60 s (cache TTL). |

## Limits and quotas

| # | Requirement |
|---|---|
| NFR-05 | Lesson video ≤ 2 GB / ≤ 240 min; course video total ≤ 20 GB; materials ≤ 10 x 500 MB per lesson; ≤ 50 sections, ≤ 200 lessons, 0-10 previews. |
| NFR-06 | Heartbeat envelope 600/h per enrollment; stream mints 120/h per buyer; builder saves 120/h per course. |
| NFR-07 | Progress rows = O(enrolled students x lessons); no per-second writes; flush coalesces by `(product, buyer, lesson)`. |
| NFR-08 | Courtesy window 90 days; progress purged after it; audit retention 24 months. |

## Security and data

| # | Requirement |
|---|---|
| NFR-09 | Deny-by-default; owner + buyer scoping with RLS; IDOR returns 403 with zero rows (drill AC-06). |
| NFR-10 | Gated bytes never stream without an enrollment check; preview responses carry no gated media; tickets bound to enrollment + lesson; anomalies flag (feature 23 pattern). |
| NFR-11 | Uploads: bounded reads, container/magic validation, random keys; sanitized lesson bodies; no scriptable mime. |
| NFR-12 | Audit + revocation: enrollment, revocation, certificate mint/revoke append-only; certificates revoked on refund ≤ 60 s. |

## Sessions, localization, accessibility

| # | Requirement |
|---|---|
| NFR-13 | Sessions cookie-based at the edge (SSO-only); heartbeat authenticated per request; no guest path. |
| NFR-14 | vi/en copy; VND integers; durations shown `h:mm:ss`; dates localized. |
| NFR-15 | Player keyboard-operable (play/pause/seek, captions track slot); syllabus rows screen-reader labeled; certificate page readable without JS. |
