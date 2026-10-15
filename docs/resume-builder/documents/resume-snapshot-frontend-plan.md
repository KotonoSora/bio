# Resume Snapshot Frontend Plan (editor + versions + publish — UI build steps)

PLAN ref: P2-1 dashboard UX (resume section: create/rename/duplicate/delete, template select, field editing, explicit Save Snapshot). BA source: `docs/resume-builder/documents/resume-snapshot.md`. Server truth in `docs/resume-builder/documents/resume-snapshot-backend-plan.md` (versioning, codes, publish rule — never redefined here).

## Purpose

Give the frontend developer an ordered UI procedure for the resume editor: document management, draft-vs-snapshot discipline, explicit save, version history, publish selection, and delete-with-unlink safety. Follow steps 1–7 in order. Draft edits and frozen snapshots are visually distinct at all times — the user always knows which one they are looking at.

## Users

- Frontend developer: builds `/app/resumes/*` against the backend plan's contracts.
- QA: executes the immutability, linkage, and confirm-matrix drills.
- Job seeker: edits freely, freezes versions deliberately, publishes one resume.

## Functional requirements

1. Routes and visual contract: `/app/resumes` (document list: title, template chip, public badge, updated time) → `/app/resumes/:id` (editor: field forms + template picker + preview + version timeline + publish bar). Global rule: draft areas carry a `Draft — unsaved changes` pill; frozen snapshot views carry a `Snapshot vN · frozen` pill with a distinct background. The two pills never appear together — ambiguity here is the content-drift bug wearing UI clothes.
2. Step 1 — create/open flow: `New resume` → `POST documents {title}` → editor opens empty draft → field editing (basics/experience/education/skills/links sections, add/remove/reorder entries) autosaves to draft state only (in-memory + session recovery, never a snapshot endpoint — the client literally cannot call save accidentally because autosave targets a different store than the save button).
3. Step 2 — explicit save flow: `Save Snapshot` (always visible, disabled when no draft changes) → confirm-free single click → `POST /:id/snapshot` → `201 {version}` → version timeline prepends vN (frozen pill, timestamp, `source_profile_rev`) → draft pill clears. Double-click safety: button disables while pending (a double-submit would still be additive server-side, but the UI never sends it).
4. Step 3 — version timeline flow: click any version → read-only frozen view (fields locked, `frozen` pill, `Restore as draft` button) → restore copies version content into the draft (does NOT rewrite history — restoring then saving creates vN+1) → archived versions show `Archived — retrieved from backup` with a slower-load notice stated upfront (retrieval may take seconds).
5. Step 4 — publish flow: `Publish` on a version → sets document public + `published_version` (server clears any other public document on the profile — single-public rule surfaces here as `This replaces "Q3 Resume" as your public CV` confirm text naming the displaced document) → public badge + `View live CV` link → unpublish clears both flags (cv URL 404s within purge SLA; copy states this before confirm).
6. Step 5 — rename/duplicate/delete flow: rename inline (server-validated length); duplicate → `Copy of …` document with latest snapshot as v1 (lands in list, toast with `Open` action); delete → if a visible `resume` block links this document, a blocking confirm names the consequence (`Also hides the resume section on your public page`) with one explicit checkbox + Delete (informed consent without typing-name toil) → confirmed delete removes document + versions + unlinks block atomically (one reload shows all three gone).
7. Step 6 — template select flow: picker (gallery owned by the templates frontend plan — embedded here as a component, not reimplemented) → selection calls `PUT documents/:id {template_id}` → re-renders preview instantly, snapshots untouched (switching templates never creates a version — stated under the picker so users don't fear losing history).
8. Step 7 — edge cases: expired session mid-draft (draft preserved in session storage, re-login resumes); oversized snapshot (server 422 → `Resume too large` with section-level guidance, draft intact); two tabs saving (versions N+1, N+2 both kept, timeline shows both — no silent overwrite, ever).

## Data / API touches

- Screen → endpoint map: list → `GET /api/v1/documents`; create/rename/template → `POST / PUT`; editor save → `POST /:id/snapshot`; history → `GET /:id` (+ `GET /:id/versions/:v` with `archived` flag handling); publish/unpublish → backend publish path; delete → `DELETE /:id` (+ `confirmed` on linkage). Session cookie + CSRF; non-2xx parsed as problem JSON.
- Type mirror: `Document {id, title, templateId, isPublic, publishedVersion, updatedAt}`; `Snapshot {version, json, createdAt, archived?}`. Code → UI map: `document_not_found` → list refresh; `delete_needs_confirm` → linkage confirm dialog; `version_not_found` → timeline refresh.

## Acceptance criteria

- Pill drill: draft and frozen pills never co-occur; expired-session resume preserves draft.
- Save drill: explicit click → new timeline entry; autosave bursts create zero versions (network-log proof).
- Restore drill: restore-then-save appends vN+1; history intact; archived version loads with backup notice.
- Publish drill: publish names the displaced document; unpublish warns of 404; live CV reflects the published version within purge SLA.
- Delete drill: linked-visible delete requires the checkbox confirm; reload shows document + versions + link all gone.
- Template drill: switching re-renders without creating a version (version count unchanged).

## Out of scope

- Versioning and publish logic (backend plan above); table DDL (database plan above); template visuals/print CSS (templates plans); five-link matrix (sharing plans); backup/adapters (portability plans).

## Open questions

- Q1: Version timeline UI — side rail vs bottom drawer on small screens?
- Q2: Delete confirm — checkbox sufficient, or typed title for resumes with many versions?
