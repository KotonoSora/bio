# Resume Documents - User Stories and Business Rules (feature 17)

Phase 1 BA · Enforced by `docs/02-architecture-techlead/17-resume-documents/`. Scope: `docs/01-requirements-ba/17-resume-documents/01-document-context-and-scope.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Job seeker | create, edit, duplicate, and freeze resume versions | I can prepare job applications with stable content | High |
| US-02 | Viewer / recruiter | read the public CV at `/:username/cv` without login | I can review applications | High |
| US-03 | Future importer | restore a resume from JSON backup losslessly | backups remain usable (forward contract to feature 21) | Medium |

## Business rules

- **BR-01 Snapshot immutability.** Versions are insert-only: no update path exists (no UPDATE grant, no re-save route). A save creates version N+1; reprinting version N is byte-identical, excluding print header/footer, after any subsequent profile edit.
- **BR-02 Explicit-save boundary.** Autosave writes client-side draft state only; the snapshot endpoint is the sole version writer, and drafts reach it only as part of the explicit save payload. Two tabs saving produce N+1, N+2 - both kept, never silently overwritten.
- **BR-03 One public CV.** At most one document per profile is public (partial unique). Publishing version V clears any other public document in one transaction and the confirm copy names the displaced document; publishing is idempotent on retry. Unpublishing clears `is_public` and the published pointer; the `/cv` URL serves parity `404` within the purge bound (`<= 60 s`).
- **BR-04 Linkage and safe delete.** One `resume` block links one document (`payload_json.documentId`). Deleting a document with a linked visible block requires explicit confirm (`422 delete_needs_confirm` without it); the confirmed delete hides/unlinks the block first, then deletes the document - crash order leaves a hidden block, never a visible block pointing at a deleted document. No dangling-public state exists in any path.
- **BR-05 Archive honesty.** Snapshots older than 30 days that are not the document's published version move to the local archive volume (one NDJSON file per document, move-then-delete order) and are deleted from PG. Retrieval of an archived version succeeds with an `archived: true` flag and stated slower latency - never a silent miss.
- **BR-06 Public read discipline.** `/:username/cv` renders only the selected published version via the internal read contract; private, unlinked, or absent documents resolve as parity `404`; the page is `noindex` with canonical to the bio URL (feature 10's tag contract).
- **BR-07 Owner scoping and safety.** Every dashboard operation scopes by server-set `user_id` with RLS behind it (foreign ids: `403`, unknown: `404`); public reads scope by `username + is_public=1`; all resume strings are sanitized at capture time so stored snapshots are already clean for every renderer; CSP holds on dashboard and CV.
- **BR-08 Capture content.** `snapshot_json` is the closed schema `{schema_version, basics, experience[], education[], skills[], links[], template_id, captured_at, source_profile_rev}`; profile-derived fields (name, contact, links) are filled server-side from current profile data at save time; unknown keys are rejected; payload cap 2MB before insert.
- **BR-09 Template switching.** Selecting a template re-renders and never creates a version or mutates one; it is safe at any time.
- **BR-10 Duplicate.** Duplicating copies the document row (new id, `"{title} copy"`) and copies the latest snapshot as version 1; a draft-only document (no snapshot yet) duplicates as an empty draft. Duplicates start unpublished.

## Constraints carried

- Draft state is client-side only (session recovery on expiry); no server draft table exists.
- Cross-service reads/writes only via internal APIs (profile capture; block linkage); no cross-database access.
- Events follow the platform rule (after-commit, at-least-once, idempotent consumers; purge self-heals via the 60 s cache TTL).
- No pricing or plan values appear here; entitlement gates for resume features belong to features 32-40 if ever added.
