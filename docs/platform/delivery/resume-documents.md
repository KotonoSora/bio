# Resume Documents (execution plan R0 — snapshot kernel)

PLAN ref: P2-1 (documents + `resume` block linkage) + P2-5 scoped to resume (fixture guard) + Security checklist S-1/S-2/S-6. BA source: `docs/resume-builder/documents/resume-snapshot.md`. Rendering in `docs/platform/delivery/resume-render-share.md`; portability in `docs/platform/delivery/resume-portability.md`. DEVIATION CARRIED: Spring Boot `4.1.1` service on containerized PG `18.6` replaces Workers Hono + Neon default per `docs/platform/architecture/architecture-decision.md`; contract (JSON REST, cookie session, server `user_id`, TEXT ids, ISO8601) frozen.

## Purpose

Build the resume data kernel first: versioned `documents` rows, explicit Save Snapshot semantics, `resume`-block linkage, and the R2 archive rule. Every template, share link, and import path consumes frozen snapshots from this kernel and never redefines versioning. R0 exits with a container image where dashboard CRUD, snapshot immutability, and reprint stability are green before any gallery or adapter work starts.

## Users

- Backend developers: implement the `resume` Gradle module against the frozen snapshot contract.
- QA / release: verify R0 with reprint, privacy, and probe drills before render/share work.
- Support: answers "which version did the recruiter see" from snapshot revisions, not draft state.

## Functional requirements

1. Stack and topology (mandatory): PostgreSQL LTS `18.6`, Spring Boot `4.1.1`, Kotlin `2.4.21`, Gradle `9.8.1` wrapper-only. Architecture verdict for R0 — modular monolith, not microservices. Monolith pros here: single transaction for document + block-link writes, one RLS policy set over `documents`/`profile_blocks`, joins `users→profiles→blocks→documents` stay local, one audit log, one release train. Microservices cons avoided: a documents-service split would add cross-service ownership checks (wider IDOR surface), distributed draft-vs-snapshot consistency, and a second backup/audit trail with zero scale asymmetry at P2 volume. Split requires ≥2 triggers from `docs/platform/architecture/architecture-decision.md` (none fire in R0); module boundary (`resume` package + `resume` PG schema) is enforced by ArchUnit so a later extract stays a move.
2. Domain kernel: Flyway `V2x__resume_documents.sql` creates `documents{id, profile_id, user_id TEXT NOT NULL + index + RLS, template_id, snapshot_json JSONB, rev INT, is_public_snapshot BOOL, captured_at TIMESTAMPTZ}` where `snapshot_json{basics, experience[], education[], skills[], links[], template_id, captured_at, source_profile_rev, schema_version}`. Dashboard flows: create, rename, duplicate, delete, select `template_id`, edit draft fields, explicit Save Snapshot → new immutable revision (autosave touches draft only, never rewrites a frozen revision). One `resume`-type `profile_blocks` row links via `payload_json{document_id, show_contact, section_order?}`; deleting a linked document requires explicit confirm and hides (never orphans to public) the block. Cover letter is a `template_id` variant, not a second table.
3. Growth and privacy rules: revisions older than 30 days archive to blob store (R2-compatible object storage in compose/prod manifests; PG keeps current + recent only) with retrievable restore; public `/:username/cv` renders only the selected public snapshot with `is_public=1`, otherwise 404; username change keeps 301 old→new behavior owned by the profile major.
4. Security baseline: session `user_id` scoping AND RLS on every document/block row (S-1/S-6); HTML sanitization on all resume fields + CSP `default-src 'self'` on dashboard and CV surfaces (S-2); rate limits on anonymous CV reads; `audit_log{actor, action, target, at}` on create/save/delete/link/publish actions; secrets via environment only.
5. Container per release: multi-stage build (Gradle `9.8.1` → Eclipse Temurin JRE, non-root), OCI image tagged `:<semver>-<sha>` runnable via both `docker run` and `podman run` against empty PG `18.6` (Flyway migrates on boot, fixture imports clean) per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`.

## Data / API touches

- Schema: `resume` schema owns `documents` (+ revision history either as revision rows or JSONB history per implementation note, archive pointer columns for blob-offloaded revisions). Least-privilege app role vs migration role; 5s statement timeout; Hikari pool. No cross-schema writes.
- Endpoints (versioned `/api/v1`, additive-only, OpenAPI diff gate): `GET/POST /api/v1/documents`, `GET/PUT/DELETE /api/v1/documents/:id`, `POST /api/v1/documents/:id/snapshot`, `PATCH /api/v1/blocks/:id/link-document` — all owner-scoped `WHERE user_id = session.userId`; public reads resolve by `username + is_public=1` and never accept a user id from the client.
- Blobs: snapshot archives addressed by content hash under a random key prefix; dashboard preview state stays uncached SPA state (cache rules belong to the render plan).

## Acceptance criteria

- Reprint drill: edit profile bio/links, reprint saved resume → byte-identical modulo print header/footer; new snapshot required for edits to appear.
- Persistence drill: reload after reorder/save → linked block and selected snapshot persist; delete-with-link requires confirm and hides the block.
- Privacy drill: private/unlinked document → 404 publicly; curl (no JS) on public CV returns snapshot text + title/meta/canonical/OG + JSON-LD `Person`.
- IDOR drill (S-1/S-6): user A `GET/PUT/DELETE` on user B document → 403; direct PG as app role denied by RLS.
- XSS drill (S-2): script/iframe/event-handler in any resume field stripped on dashboard and CV; CSP holds.
- Archive drill: revision >30d served from blob archive, not PG; fixture resume imports on a clean database after every migration.
- Container drill: same digest boots under Docker AND Podman, compose cold boot green, previous-tag rollback green, contract diff clean.

## Out of scope

- Template visuals, screen/print split, gallery (see `docs/platform/delivery/resume-render-share.md`); share-link matrix, shortener, custom hosts (same doc); JSON backup/restore, LinkedIn/GitHub adapters (see `docs/platform/delivery/resume-portability.md`); shop/leads/domains, AI tailoring (P3), P4/P5.

## Open questions

- Q1: One public snapshot per profile in v1 vs multi-snapshot picker (BA open — propose one-public for R0, picker only if profile major requires it)?
- Q2: Manual snapshot button only vs auto-snapshot on publish (propose manual-only in R0)?
