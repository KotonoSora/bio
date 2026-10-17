# Resume Documents - Step-by-Step Implementation Plan (feature 17)

Phase 2 TL · Dependency-ordered chain; each task's acceptance goal gates the next. Criteria cited from `docs/01-requirements-ba/17-resume-documents/06-acceptance-criteria.md`.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | Service scaffold (`resume-service`, port 8086) + `V1__resume_core.sql` + RLS + grants (snapshots: no UPDATE) | Baseline scaffold + feature 06 (profile internals exist) | TL 02 drills 1, 2, 5, 7 green |
| T-02 | BE | Document CRUD + duplicate (latest snapshot as v1 / empty draft) + rename/template update | T-01 | AC-03, AC-10 (data half) green |
| T-03 | BE | Linkage rows in profile-service: `PATCH .../link-document`, `by-document` read, idempotent `unlink-document`; delete orchestration (unlink first) | T-01; feature 08 codebase | AC-09 (matrix + crash direction) green |
| T-04 | BE | `SnapshotCapture` + save endpoint: draft merge, profile fill (`by-user` read, 2 s timeout), sanitize, closed schema, `source_profile_rev`, 2MB cap, insert N+1 under lock | T-01 | AC-01, AC-02, AC-05 green |
| T-05 | BE | Version reads: timeline summaries + single version (PG), archived read path via the stored archive file with `archived: true` | T-04 | AC-07 (read half) green |
| T-06 | BE | Publish/unpublish: single-public transaction, partial-unique guard, idempotent retry, `resume.cv_changed.v1` | T-04 | AC-08 green |
| T-07 | BE | `ArchiveJob`: daily 03:20 UTC, Redis db 8 single-flight, move-then-delete with write verification, metrics `archive.moved/deleted` | T-01 | AC-07 (move half) green |
| T-08 | BE/DevOps | Public read `GET /internal/v1/resumes/{username}/published` (parity 404) + public-site consumption edits (feature 10 rows) + purge consumer | T-06 | AC-04 green; `/cv` drill flips from parity 404 |
| T-09 | FE | Editor surfaces: list, draft/frozen pills, save, timeline + restore, publish bar, delete confirm, template picker embed, archived/capture/oversize notices | T-02..T-06 | UI halves of AC-01..AC-10 green |
| T-10 | BE/DevOps | Purge participant handler + drill wiring (AC-01..AC-10 in G5/G7), rate envelopes, Turnstile on create | T-03..T-09 | G5 probes + G7 spot-check green; feature 02 drill includes a resume fixture |

## Notes

- Rollout order: T-01..T-07 can ship dark; T-08 flips `/cv` from parity 404 to real snapshots - the first user-visible moment. Nothing before T-08 depends on feature 10 changes.
- Feature 21 (portability) builds on T-04/T-05 read shapes; its export contract cites `schema_version` from here.
- The legacy per-module "one script, applied once" doctrine survives as `V1` + conventional Flyway versioning for later resume-batch features (18-21 add their own migrations in this database).
- Print output is a browser concern (feature 18: `window.print()` A4) - no server-side PDF exists in this feature.
- R0 delivery plan folded (legacy execution plan, file deleted with this batch): DEVIATION CARRIED - Boot `4.1.1` + containerized PG `18.6` replace the Workers Hono + Neon default; the frozen contract (JSON REST, cookie session, server `user_id`, TEXT ids, ISO8601) stands. The `resume` module boundary (package + schema) keeps a later extraction a move; container/rollback drills ride the baseline release gates.
