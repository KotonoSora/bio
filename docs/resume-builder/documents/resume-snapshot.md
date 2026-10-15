# Resume Snapshot (documents + resume block)

PLAN ref: P2-1 (data + block linkage). Template rendering detail lives in `templates-print/resume-templates.md`.

## Purpose

Define the resume document model for the Resume Builder major. A resume is one `resume`-type `profile_blocks` entry pointing at a versioned `documents` row. Rendering always uses `snapshot_json`, so a saved resume reprints identically even after the profile is later edited. This protects job applications from silent content drift.

## Users

| Role | Permissions | Notes |
|---|---|---|
| Job seeker | Manage own documents and linked resume blocks scoped by session `user_id` (RLS) | Creates, edits, duplicates, and freezes resume versions for applications |
| Viewer / recruiter | Read public CV at `/:username/cv` scoped by `username + is_public=1` (no login) | Reads the public CV at `/:username/cv` (SSR, no login) |
| Future importer (P2-5 companion doc) | — (reported: no access-control statement in this file) | Restores a resume from JSON backup losslessly |

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Job seeker | create, edit, duplicate, and freeze resume versions | I can prepare job applications | High |
| US-02 | Viewer / recruiter | read the public CV at `/:username/cv` without login | I can review applications | High |
| US-03 | Future importer | restore a resume from JSON backup losslessly | backups remain usable | Medium |

## Functional requirements

1. Dashboard (`/app/*`, SPA) resume section allows: create document, rename, duplicate, delete, select `template_id`, edit resume fields, explicit Save Snapshot.
2. Saving writes a new `snapshot_json` captured from current profile + linked block content at save time. Autosave edits draft state only; it never rewrites a frozen snapshot without explicit save.
3. Snapshot is immutable once saved except by a new explicit save (new version). Keep edit state separate from frozen snapshot object.
4. One `resume` block per document link (`payload_json.document_id`). Deleting a document with a linked visible block requires explicit confirm; link becomes hidden, never dangling-public.
5. Snapshot versions older than 30 days are archived to R2; PG keeps current + recent only (per PLAN growth rule).
6. Public `/:username/cv` renders the selected public snapshot server-side. Private or unlinked documents never render publicly.
7. Cover letter is a `template_id` variant of the same document model, not a separate table.
8. Security posture (no new surface): dashboard access scopes by session `user_id` with RLS denial and public reads scope by `username + is_public=1`, with resume-field rendering XSS-stripped and CSP holding, per the IDOR and XSS probes in Acceptance criteria.

## Data / API touches

- Table `documents{profile_id, template_id, snapshot_json}` plus standard `user_id TEXT NOT NULL` + index + RLS policy (per AGENTS.md data rules). All dashboard access scopes `WHERE user_id = session.userId`; public reads scope `username + is_public=1`.
- `profile_blocks` row of `type = resume` carries `payload_json{document_id, show_contact, section_order?}`. No new block type is introduced.
- Backend-agnostic JSON over `/api/v1/*`, cookie session (Option A Hono or Option B proxy — same contract): `GET/POST /api/v1/documents`, `GET/PUT/DELETE /api/v1/documents/:id`, `POST /api/v1/documents/:id/snapshot`, `PATCH /api/v1/blocks/:id/link-document`.
- `snapshot_json` schema: `{basics, experience[], education[], skills[], links[], template_id, captured_at ISO8601, source_profile_rev}`. TEXT ids, ISO8601 timestamps throughout.

## Acceptance criteria

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Edit profile bio/links, reprint saved resume: output byte-identical (excluding print header/footer); new snapshot required to reflect edits | a saved resume snapshot | profile bio/links are edited and the saved resume is reprinted | output is byte-identical (excluding print header/footer) and edits require a new snapshot to reflect |
| AC-02 | Reload dashboard after reorder/save: linked block and selected snapshot persist | a reorder/save | the dashboard is reloaded | the linked block and selected snapshot persist |
| AC-03 | Public `/:username/cv` with curl (no JS) returns snapshot content with title/meta/canonical/OG + JSON-LD `Person`; private document returns 404 | a public or private document | `/:username/cv` is fetched with curl (no JS) | public returns snapshot content with title/meta/canonical/OG + JSON-LD `Person` and private returns 404 |
| AC-04 | IDOR probe: user A `GET/PUT/DELETE /api/v1/documents/<B-id>` returns 403; direct PG as app role also denied by RLS | user B documents | user A attempts `GET/PUT/DELETE /api/v1/documents/<B-id>` or direct PG as app role | access returns 403 / is denied by RLS |
| AC-05 | XSS probe: script/iframe/event-handler in any resume field is stripped; CSP holds on dashboard and public CV | script/iframe/event-handler input in any resume field | rendered on dashboard and public CV | the payload is stripped and CSP holds |
| AC-06 | Snapshot history older than 30 days is retrievable from R2 archive, not PG | snapshot history older than 30 days | retrieval is requested | it is retrievable from R2 archive, not PG |

## Out of scope

- Template visual design and print CSS (see `templates-print/resume-templates.md`).
- JSON backup/restore and LinkedIn/GitHub adapters (see `import-export/resume-portability.md`).
- Shop/products, courses, tips, leads/CSV, custom domains (separate majors).
- AI JD-tailoring and credit caps (P3), Post-to-All/publishing/automations/campaigns/agency (P4), scale-out/ISR/replicas (P5).

## Open questions

- Q1: One public resume per profile in v1, or a picker among several public snapshots?
- Q2: Manual snapshot button only, or also auto-snapshot on publish?
