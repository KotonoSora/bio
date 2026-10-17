# Lead Search - Architecture and Tech Stack (feature 28)

Phase 2 TL · Feature 28 of 53 — leads 2/2; a module of `profile-service` (port 8082, `profile_db`, Redis db 4) — no new service. Implements `docs/01-requirements-ba/28-lead-search/`. Host: `docs/02-architecture-techlead/27-lead-forms/` (capture, status machine, consent classes — consumed, never redefined). Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | The contact workspace extends `profile-service` on `profile_db` (V7): search, notes/tags, suppression actions, re-invite, export jobs. The L0 verdict (DEVIATION CARRIED, see feature 27 D-1) holds: no new service, no broker; split trigger unchanged. | Same transaction domain and RLS baseline as the rows it reads. |
| D-2 | Search = `pg_trgm` GIN indexes on `email`/`name` + exact-first ranking + newest-captured default; every query is pre-scoped (`user_id` + RLS); 2-char minimum server-enforced; the creator's own recent queries (`lead_queries`, last 20) are the only history kept. | Owner scoping is structural; no wildcard or platform-wide path exists to secure. |
| D-3 | Count and export share ONE predicate builder; the count on screen and the frozen export count come from the same code path; export rows reconcile exactly (AC-02). | No count/export skew by construction, not by discipline. |
| D-4 | The detail timeline is assembled server-side from `lead_events` + consent history + notes/tags; read-only forward references (orders 32-40, bookings 25, automation contacts 41-44) render only when their seams exist and label themselves "not yet connected" otherwise — never stubbed, never faked. | Honest record; forward batches plug in without rework. |
| D-5 | Notes and tags are append-only and attributed; the tag vocabulary is closed per account (cap 50, tag ≤ 40 chars, auto-add on first use); no rename/delete in v1; note/tag bodies are sanitized server-side. | Tag-sprawl and edit-history ambiguity are design-excluded. |
| D-6 | Suppression is rank-guarded through feature 27's strictest-wins machine (canonical), requires a reason, is idempotent, and propagates via the shared eligibility check (`GET /internal/v1/leads/check`). Suppressed contacts cannot be re-invited. | One state machine, one eligibility surface. |
| D-7 | Re-invite/unsubscribe: stateless HMAC tokens (`leadId|profileId|exp|purpose`), single-purpose, 72 h; the state change requires an explicit POST; the preference-center superset (classes, frequency) is the monetize batch's forward extension of the same page; sends go via the notification pipeline as transactional-class. | No token table to purge; scanner-safe like the confirm flow. |
| D-8 | Export: always async — one `lead_export_jobs` row → worker streams the fixed CSV to `profile/exports/leads/{userId}/{jobId}.csv` on the uploads volume → owner-bound HMAC grant (15 min) served service-mediated (never a direct storage URL). Quota (20/day, 2 concurrent) in Redis db 4 + DB checks; job + file purge at 7 days and on account deletion. Export is the ONLY egress (surface-review gate). | PII leaves only through audited, expiring, owner-bound paths. |
| D-9 | Every action appends actor-stamped events (`creator:{userId}`; `support:{seatId}` when agency seats 45-47 land); purge extends feature 02's participant with notes/tags/vocabulary/queries/jobs/files. | Attribution is the audit trail; purge completeness is part of the participant contract. |

## Component map

| Piece | Home | Note |
|---|---|---|
| Search + filters + counts (one predicate builder) | `profile-service` | trigram GIN; exact-first ranking; scoped predicates |
| Detail timeline assembly | `profile-service` | events + consent + notes/tags; forward refs labeled |
| Notes / tags / vocabulary | `profile-service` | append-only; cap 50/account |
| Suppression + re-invite + unsubscribe | `profile-service` | rank-guarded; HMAC tokens 72 h |
| Export worker + grants | `profile-service` + uploads volume | streamed CSV; 15-min owner grant; quota Redis db 4 |
| Re-invite/unsubscribe email | notification pipeline (forward) | transactional class |

## Boundaries

Owns: search, filters/counts, timeline assembly, notes, tags/vocabulary, suppression actions, re-invite/unsubscribe tokens, export jobs/files/grants, query history, read state, purge extension. Does not own: capture/status machine/consent classes (27), list hygiene/preference center/broadcasts (monetize batch), campaigns (gated), automation contacts rows (41-44 — correlated read-only), orders/bookings rows (32-40 / 25 — read-only references), agency seats (45-47).

## Consumers

| Consumer | Contract |
|---|---|
| agency support seats (45-47, forward) | same view; `actor` attribution; seat-scoped session |
| monetize/audience batch (forward) | export surface consumed (DEVIATION FLAGGED, BA 28 Q3); preference-center superset extends the unsubscribe page |
| notification pipeline (forward) | re-invite/unsubscribe transactional sends |
