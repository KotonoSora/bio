# Resume Documents - Context and Scope (feature 17)

Phase 1 BA · Feature 17 of 53 (resume) · Implemented by `docs/02-architecture-techlead/17-resume-documents/` (5 files); new `resume-service` (port 8086, `resume_db`). Consumers: public-site-service (`/cv` SSR), feature 10 (cache/purge), feature 08 (block linkage rows), feature 21 (export reads), feature 02 (purge participant).

## Purpose

A resume is a document whose saved versions are frozen: saving captures a snapshot, and reprinting that snapshot later is byte-identical even after the profile changes. This protects job applications from silent content drift - the failure mode where a recruiter sees something different from what the candidate sent. Draft edits and frozen snapshots are separate artifacts at every layer: the client literally cannot corrupt a saved version by editing.

## Objectives

- O-1 Frozen snapshots: versions are insert-only; a new save creates N+1; reprint of N is byte-identical by construction.
- O-2 Draft discipline: autosave touches only client-side draft state; only the explicit snapshot endpoint writes a version.
- O-3 One public CV: at most one document per profile is public; publishing names the displaced document; unpublishing clears both flags.
- O-4 No dangling-public: a document linked to a visible `resume` block deletes only after explicit confirm and block unlink - never a visible block pointing at a deleted document.
- O-5 Bounded storage, honest retrieval: versions older than 30 days that are not the published one move to the archive volume; retrieval says `archived` upfront instead of timing out silently.

## Personas and RBAC

| Role | CRUD Permissions | Description |
|---|---|---|
| Job seeker | Full CRUD on own documents/versions (owner-scoped, RLS) | Creates, edits drafts, freezes versions, publishes one CV, deletes with informed consent. |
| Viewer / recruiter | Read (anonymous): `/:username/cv` renders the published snapshot only | Reviews the public CV without login; never sees drafts or unpublished versions. |
| Importer (feature 21) | Read own snapshots for export/restore | Forward consumer; this feature only guarantees snapshot fidelity. |
| Attacker (adversarial) | none | Finds no IDOR path, no XSS vector, no way to publish someone else's document. |

## Resolved open questions

| Q | Decision |
|---|---|
| Q-01 Multiple public resumes | One public resume per profile in v1 (partial unique index); a multi-snapshot picker requires a flagged amendment. |
| Q-02 Manual vs auto snapshot on publish | Manual explicit save only. Publishing selects an already-saved version and never auto-creates one; publish without a saved version is refused `422 snapshot_required`. |
| Q-03 Archive retention | `resume/archives` uploads namespace (private, server-side reads only), one NDJSON file per document, retention `>= 90 days` (retention sweep handles pruning beyond that); retrieval is stated as potentially slow (`<= 5 s`), never silent. |
| Q-04 Cover letter | A `template_id` variant of the same document model; no separate table, no separate lifecycle. |

## In scope

- Document CRUD (create, rename, template select, duplicate, delete-with-confirm) and draft-vs-snapshot separation.
- Explicit-save snapshot versioning, version timeline reads, restore-as-draft, archive job + archived retrieval.
- Publish/unpublish with the single-public rule and the `/cv` public read contract.
- Block linkage safety (bind/unbind rows landing in feature 08's contracts) and delete orchestration.

## Out of scope

- Template visuals and print CSS (feature 18); format/print sharing surfaces (feature 19); share links (feature 20); JSON portability + adapters (feature 21).
- Block payload shape ownership (feature 08) and SSR tag mechanics (feature 10; this feature adds only the internal read contract they already reserved).
- AI tailoring (feature 16, gated), publishing/agency (41-47), scale-out replicas (baseline).

Related: rules in `docs/01-requirements-ba/17-resume-documents/02-user-stories-and-business-rules.md`; contracts in `docs/02-architecture-techlead/17-resume-documents/03-api-contracts-and-payloads.md`; error surface in `docs/01-requirements-ba/17-resume-documents/05-exception-handling-and-errors.md`.
