# Resume Snapshot Backend Plan (documents + explicit-save versions — server build steps)

PLAN ref: P2-1 (CRUD, rename, duplicate, template select, explicit Save Snapshot; autosave never rewrites frozen versions) + S-1/S-2/S-6/S-7. BA source: `docs/resume-builder/documents/resume-snapshot.md`. Phasing per `docs/platform/delivery/execution-overview.md` (E2). Schema in `docs/resume-builder/documents/resume-snapshot-database-plan.md` (documents + snapshots, written once). Screens in the snapshot frontend plan (splits next to this file).

## Purpose

Give the backend developer an ordered procedure for resume documents: CRUD, explicit-save versioning, duplicate, safe delete with block unlink, publish selection, and the archive job. Follow steps 1–9 in order. Draft edits and frozen snapshots are separate objects at every layer — styling or editing work can never corrupt saved data.

## Users

- Backend developer: executes steps 1–9 in package `com.app.resume.documents`.
- QA: runs immutability, linkage, security, and archive drills.
- Job seeker (via UI): creates, renames, duplicates, freezes versions for applications.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — modular monolith (`resume` module, `resume` schema). Monolith pros: document delete + block unlink commit atomically via service orchestration; snapshot insert + publish-pointer update share one transaction; one RLS set. Microservices cons avoided: a document service would split delete/unlink across module tables (dangling-public risk) with no ≥10x asymmetry (documents scale with profiles 1:N). Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; none fire. Starters: web, validation, data-jpa, security, scheduling, actuator, micrometer. Load posture: version inserts are user-paced (explicit saves, not autosave writes); reads are owner-indexed or public-unique-indexed; archive bounds table size.
2. Step 1 — schema: run the snapshot database plan script once (after profile/blocks scripts). No other DDL in this slice.
3. Step 2 — entities 1:1 with the tables: `DocumentEntity` (fields as DDL; `publishedVersion Int?`) + `SnapshotEntity` (`@IdClass(DocumentVersionId)`, `snapshotJson: Map` via JSONB). No validation annotations on entities.
4. Step 3 — `service/SnapshotCapture.kt` (pure): `capture(profile, blocks, templateId): SnapshotJson` assembles `{basics, experience[], education[], skills[], links[], template_id, captured_at, source_profile_rev}` from the current profile row + linked content; `HtmlSanitizer.strip` on every string field (S-2 at capture time — stored snapshot is already clean, so every renderer inherits safety). Unknown/extra keys rejected (closed schema, versioned by `schema_version` for the portability slice).
5. Step 4 — `service/DocumentService.kt`: `create(userId, title)` → document v0 without snapshot (draft state lives in the editor, not in PG); `rename`, `setTemplate` (re-renders only, never mutates snapshots); `saveSnapshot(userId, documentId)` `@Transactional` = lock document → capture → `INSERT snapshots(version = max+1)` → audit (autosave drafts can never call this path — separate DTOs, separate endpoint); `duplicate` = copy document row (new id, `title + " copy"`) + copy latest snapshot as v1 → audit; `delete` `@Transactional` = if linked visible `resume` block exists require `confirmed=true` else `422 delete_needs_confirm` → delete document (snapshots cascade) + unlink block (payload cleared/hidden via block-service port) → audit; `publish(userId, documentId, version)` = assert ownership → clear other `is_public` on the profile (single-public rule) → set `is_public + published_version` → audit → publish purge event (cv SSR re-renders).
6. Step 5 — `api/DocumentController.kt`: `GET/POST /api/v1/documents`, `GET/PUT/DELETE /api/v1/documents/:id`, `POST /:id/snapshot` → `201 {version}`, `PATCH /api/v1/blocks/:id/link-document` (bind/unbind `{documentId?}`). Session `userId` only; `@ControllerAdvice` → RFC 9457 problems; cross-user → 403. Error codes: `document_not_found` (404), `delete_needs_confirm` (422), `version_not_found` (404), `already_public` (200 idempotent — publish is safe to retry).
7. Step 6 — `job/SnapshotArchiveJob.kt` `@Scheduled(cron="0 20 3 * * *")`: select snapshots `created_at < 30d` excluding each document's `published_version` → append to `resume-archives/{document_id}.ndjson` blob → delete moved rows (move-then-delete order) → metric `archive.moved/deleted`. Retrieval of archived versions reads the blob (endpoint `GET /:id/versions/:v` falls through to archive with `archived:true` flag — specified, not a silent miss).
8. Step 7 — security: owner scoping + RLS (S-1/S-6); capture-time sanitization + CSP on dashboard and cv SSR (S-2); Turnstile + rate limits on create/import-adjacent writes (S-5); 2MB-equivalent snapshot size cap (oversized payload → 422 before insert). Secrets via env only.
9. Step 8 — observability: `@Timed` on save/duplicate/publish; `archive.moved/deleted` gauges; Actuator health includes `db`.
10. Step 9 — tests + container: immutability test (edit profile → old version bytes identical); linkage test (delete with visible block without confirm → 422 + block intact; with confirm → both gone, no dangling-public); single-public test (second publish flips, old clears, partial-unique holds under race); archive test (31d row moves then deletes; retrieval flag); `@WebMvcTest` per error code; Testcontainers IT (RLS deny, CHECK-equivalent closed-schema rejection); same image via `docker run` + `podman run` per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`.

## Data / API touches

- Schema: database plan above (only writer here). Endpoints: `GET/POST /api/v1/documents` → list/create; `GET/PUT/DELETE /:id`; `POST /:id/snapshot` → `201 {version}`; `PATCH /api/v1/blocks/:id/link-document` (bind/unbind). Public reads: anonymous `GET /:username/cv` renders the published version (SSR owned by templates/sharing plans; version resolution owned here: `published_version` lookup, else 404).

## Acceptance criteria

- Immutability drill: save → edit profile → reprint old version byte-identical; new snapshot needed for edits.
- Persistence drill: reload after reorder/save → linked block + selected snapshot persist.
- Public drill: cold `curl /:username/cv` returns published snapshot text + tags; private/unpublished → 404.
- Linkage drill: delete-confirm matrix above; no dangling-public block in any path.
- Security drill: cross-user 403 + RLS deny; XSS stripped at capture; writes throttled; secrets grep clean.
- Archive drill: old versions retrievable with `archived:true`; PG holds published + recent only.
- Container drill: digest pair boots, compose green, rollback green, contract diff clean.

## Out of scope

- Table DDL (database plan above); editor screens (frontend plan); template visuals/print CSS (templates plans); five-link contract (sharing plans); backup/adapters/fixture (portability plans); block payload validation (link-blocks plans); AI JD-tailor (P3); publishing/agency (P4); replicas/ISR (P5).

## Open questions

- Q1: Multi-snapshot picker (see database plan Q1 — single enforced for v1)?
- Q2: Manual snapshot only, or auto-snapshot on publish too?
