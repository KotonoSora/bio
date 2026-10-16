# Resume Documents - Architecture and Tech Stack (feature 17)

Phase 2 TL · Service: new `resume-service` (port 8086, `resume_db`, Redis db 8); no new container kind beyond one service image. Implements `docs/01-requirements-ba/17-resume-documents/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | `resume-service` owns documents, snapshots, the archive job, and the public CV read contract. DEVIATION CARRIED: the legacy modular-monolith verdict is superseded by the baseline microservices + database-per-service (flagged, in force); the `resume` module becomes a service boundary. | The baseline's extraction triggers no longer decide this - the platform pattern does; the delete/unlink atomicity concern is handled by D-3's ordering, not by co-location. |
| D-2 | Immutability is structural: `snapshots` has no UPDATE grant and no re-save route; a save inserts version `max+1` under a row lock. | Reprint stability is a database property, not a service promise. |
| D-3 | Document delete with a linked block orchestrates: hide/unlink block first (profile-service internal route, idempotent), then delete the document (snapshots cascade). Crash between the two leaves a hidden block + document - retry completes; the reverse order would leave a visible dangling link. | Ordering direction makes the crash state safe in one direction only; no distributed transaction needed. |
| D-4 | Publish is one transaction: clear any other public document on the profile, set `is_public + published_version`; guarded by the partial unique index; idempotent on retry. | Legacy single-public rule survives the service split verbatim. |
| D-5 | The public read contract lands feature 10's reserved route: `GET /internal/v1/resumes/{username}/published`; absent/private/unpublished -> `404 resume_absent` parity. | Feature 10's `/cv` SSR already expects exactly this hook; no tag work is duplicated here. |
| D-6 | Capture model: the save request carries the client draft; the server fills profile-derived fields (`basics` contact/name, `links[]` from profile/blocks) at save time, sanitizes every string, validates the closed schema, records `source_profile_rev`. | Drafts stay client-side (legacy: "draft state lives in the editor, not in PG"); the snapshot is complete and clean at rest. |
| D-7 | Archive: `resume/archives/{document_id}.ndjson` on the private uploads volume, move-then-delete (file write verified before PG delete), `>= 90 days` retention, server-side reads only. | Growth rule preserved without new infrastructure; retrieval honesty by contract. |

## Boundaries

`resume-service` owns: document CRUD, versions, publish flags, archive, audit rows, the public read contract, the `resume.cv_changed.v1` event. It does not own: block payload shape (feature 08; linkage rows land there in this change), SSR tags/caching (feature 10), template visuals/print (feature 18), share links (20), portability (21), purge execution (feature 02 runner - this service supplies a participant handler).

## Consumers

| Consumer | Contract |
|---|---|
| public-site-service (feature 10) | `GET /internal/v1/resumes/{username}/published`; `resume.cv_changed.v1` purges `ssr:*:cv` (10's table row updated in this change) |
| profile-service (feature 08) | Additive rows landed there: `PATCH /api/v1/blocks/{id}/link-document`, `GET /internal/v1/blocks/by-document/{documentId}`, `POST /internal/v1/blocks/{id}/unlink-document` |
| Feature 21 (portability) | Reads snapshots/versions for export; restore path reuses the snapshot schema (closed, versioned by `schema_version`) |
| Feature 02 (purge) | Participant handler: rows + `resume/archives` files; counts in the certificate |

## Stack notes

Spring Boot `4.1.1` / Kotlin `2.4.21` / Gradle `9.8.1` unchanged; baseline starters only (`@Scheduled` is core Spring - no new dependency). New internals: `DocumentService`, `SnapshotCapture` (pure), `ArchiveJob` (single-flight lock in Redis db 8), `PublicResumeReadController`. File writes via `java.nio.file` streaming (temp + rename); JSONB via JPA `Map` mapping. No new Redis key families beyond the job lock.
