# Resume Portability (execution plan R2 — backup, restore, adapters, fixture)

PLAN ref: P2-5 scoped to resume (lossless round-trip, versioned schema, fixture gate) + S-1/S-2/S-4. BA source: `docs/resume-builder/import-export/resume-portability.md`. Builds on `docs/platform/delivery/resume-documents.md`; rendering unaffected (imports land as draft — see rule 3). DEVIATION CARRIED: adapter fetches and file handling run in the Spring Boot service (bounded server-side clients), not Workers; same JSON/cookie/`user_id` contract.

## Purpose

Close resume lock-in: back up, restore, and bootstrap resumes without touching public state by accident. R2 covers JSON export/import of resume documents plus linked `resume` blocks, one-shot LinkedIn/GitHub adapters that land as draft, and the checked-in fixture that gates every schema change. R2 exits with round-trip, atomicity, and adapter-draft drills green in one container image.

## Users

- Job seeker: downloads backups, restores after delete, moves accounts, bootstraps from LinkedIn/GitHub.
- New user: starts from an adapter draft, reviews, then explicitly saves and publishes.
- Developer/CI: runs the fixture through migrations to catch breaking schema changes.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6`, Boot `4.1.1`, Kotlin `2.4.21`, Gradle `9.8.1`. Architecture verdict for R2 — monolith, not microservices. Monolith pros: import validation + row creation share the `resume` transaction (all-or-nothing), reuse the same Bean Validation/sanitization/RLS/audit path as dashboard writes, and keep the fixture gate inside the standard `./gradlew build`. Microservices cons avoided: an import-worker service would split the atomicity boundary (partial-apply risk), duplicate schema-version logic, and add queue/ops surface for bursty-but-small P2 file volumes. Recorded split trigger: if third-party adapter fetch volume or latency (rate limits, bulk backfills) sustains worker-backlog pressure at P4, adapter fetching alone extracts behind a queue — R2 keeps fetchBehind an interface so the move is wiring-only.
2. Backup scope guard: export/import covers `documents[]` (with `template_id`, `snapshot_json`, `schema_version`) + linked `resume`-type block rows only. Product/course/tip/form blocks, `products`, `leads`, `custom_domains` are never smuggled in (their majors own them; grep + review gate).
3. Draft-only landing: every import path (file restore, LinkedIn, GitHub) creates draft state and never mutates a frozen snapshot nor publishes `/:username/cv`. Public CV changes only after explicit Save Snapshot + publish. Overwrite-in-place requires explicit confirm; default restore creates new rows.
4. Adapters (one-shot v1, no continuous sync): LinkedIn — paste URL or upload export → map positions/education/skills into resume schema as draft for review. GitHub — handle-only, public data, no token in v1 → map pinned/top repos + bio into projects/links as draft. All third-party fetches are server-side with allowlist, timeout, and size bounds; client never sees third-party tokens; imported strings go through the same HTML sanitization as dashboard input; imported links restricted to `https:` allowlist.
5. Validation and atomicity: `schema_version` on every export; unknown/newer versions rejected with actionable error before any write; 2MB file cap; JSON-schema check; malformed/oversized/newer-version input → zero rows written (single transaction rollback). Fixture `fixture-resume.json` checked in; every schema change keeps it importable (migration or documented break) and CI imports it against a clean PG `18.6` (Testcontainers) on every build.
6. Audit and abuse: import/export events in `audit_log{actor, action, target, at}`; import endpoints authenticated, owner-scoped, rate-limited; oversized/malicious uploads rejected per S-4 mechanics (magic-byte where files apply, sanitization everywhere).

## Data / API touches

- Tables: no new tables — reuses `documents` and `profile_blocks(type=resume)` from R0. All PG via Spring Data JPA + Flyway; RLS + `WHERE user_id = session.userId` on every read/write; public CV untouched by these paths.
- Endpoints: `GET /api/v1/export/resume` (download), `POST /api/v1/import/resume` (validated dry-run + confirm, atomic), `POST /api/v1/import/linkedin`, `POST /api/v1/import/github`. Adapter responses return draft documents/blocks + review checklist; a separate explicit snapshot call freezes them.
- Blobs: avatar bytes excluded from resume JSON in v1 (reference by key, re-upload separately — decides BA Q1 with a concrete default); no imported binary blobs stored except through the existing avatar/thumbnail pipelines.

## Acceptance criteria

- Round-trip drill: export → fresh-account import → re-export is semantically identical except ids/timestamps (fixture + one real resume).
- Atomicity drill: malformed, oversized, or newer-version file → actionable error, zero rows written.
- Draft drill: LinkedIn/GitHub/file imports create draft only; public `/:username/cv` unchanged until Save Snapshot + publish.
- Scope drill: export contains only resume documents + `resume` blocks; shop/leads/domain rows never appear (grep + payload audit).
- IDOR drill (S-1/S-6): user A export/import against user B `profile_id` → 403; app-role PG reads denied by RLS.
- XSS drill (S-2): malicious strings in imported payload render stripped on dashboard preview, public CV, and re-export.
- Fixture drill: fixture imports clean on empty PG `18.6` after every migration; CI fails on break without migration note.
- Container drill: R2 tag runs under Docker AND Podman from empty PG via Flyway from V1; OpenAPI diff additive-only; rollback green.

## Out of scope

- Snapshot semantics and rendering (see `docs/platform/delivery/resume-documents.md`, `docs/platform/delivery/resume-render-share.md`); full profile+blocks backup and non-resume majors; continuous LinkedIn/GitHub sync; paid digital delivery via `orders{product_id}` (shop + P3 billing); P4/P5.

## Open questions

- Q1: Avatar bytes in export vs key-reference + re-upload (propose reference-only in v1)?
- Q2: Fixture path — co-located `fixture-resume.json` next to the BA portability doc vs a shared `fixtures/` root (propose co-located with the BA doc)?
