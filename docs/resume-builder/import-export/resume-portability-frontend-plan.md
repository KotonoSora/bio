# Resume Portability Frontend Plan (backup wizard + adapters — UI build steps)

PLAN ref: P2-5 UX (download backup, restore with confirm, adapter bootstrap with review). BA source: `docs/resume-builder/import-export/resume-portability.md`. Server truth in `docs/resume-builder/import-export/resume-portability-backend-plan.md` (atomicity, draft-only rule, codes — never redefined here).

## Purpose

Give the frontend developer an ordered UI procedure for portability: one-click backup, the three-step restore wizard (upload → dry-run preview → confirm), adapter bootstrap flows that end in review (never in publishing), and the error states that keep hostile files outside. Follow steps 1–6 in order.

## Users

- Frontend developer: builds `/app/resumes/*` backup UI + `/app/import/*` wizard against the backend plan's contracts.
- QA: executes the round-trip, rejection-preview, and draft-only drills.
- Job seeker / new user: backs up, restores, moves accounts, bootstraps from LinkedIn/GitHub.

## Functional requirements

1. Surfaces: backup button (per document + whole-resume-library); restore entry (`Import backup` opening the wizard); adapter cards (LinkedIn, GitHub) on the new-user empty state AND in settings (existing users move accounts too). All three share the `ImportReview` component (dry-run summary renderer) — one component, three callers.
2. Step 1 — backup flow: `Download backup` → `GET export/resume` streams the file (progress for large libraries; filename dated) → success toast with file size + version count (the toast states what left the building — exfiltration honesty for shared machines: `12 documents, 34 snapshots downloaded`). No options dialog (format is fixed-versioned; options would imply choices that don't exist).
3. Step 2 — restore wizard flow: step A upload (drag-drop or picker, client pre-checks extension + 2MB before a byte uploads) → step B dry-run preview (auto-called `?dry_run=true`: documents-to-create with titles, snapshot counts per document, warnings list — unknown keys, clamped URLs, sanitized fields each shown as reviewable warnings, not silent fixes) → step C confirm (names the blast radius: `Will create N documents. Nothing will be overwritten.` — imports never overwrite in place without a second explicit per-document confirm, which v1 does not offer at all) → progress → done screen (created documents with `Open` links + audit-visible timestamp).
4. Step 3 — rejection presentation: malformed → `Couldn't read this file` + position detail + `Pick a different file` (wizard resets to step A, zero server state exists by atomicity); oversized → size text pre-upload AND server code post-upload (both layers, same message); newer-version → `Made with a newer app version (vX). Supported: vY.` + docs link (never a partial import behind the user's back).
5. Step 4 — LinkedIn adapter flow: paste-URL tab + upload-file tab (file preferred, labeled `Most reliable`) → submit → `POST import/linkedin` → lands in `ImportReview` showing mapped positions/education/skills with `Needs review` flags on every adapter-filled field → `Create draft` (document created `is_public=false`, zero snapshots published) → editor opens with review flags inline (adapter data is visually marked until the first manual save — provenance the user can see).
6. Step 5 — GitHub adapter flow: handle input (validated `^[a-zA-Z0-9-]+$` client-side) → submit → server fetches (progress state `Fetching public repos…`, may take seconds — stated upfront) → same `ImportReview` → same draft landing. Token fields do not exist anywhere in this flow (v1 has no token; a `paste token` input appearing here is a phishing-shaped bug — assert its absence in review).
7. Step 6 — post-import honesty: every adapter-created draft carries `Imported from {LinkedIn|GitHub} — review before saving` until first Save Snapshot; nothing imported ever touches the public cv without the standard save + publish path (the draft-only rule rendered visible — step 4/5 end in the editor, never on the live page).

## Data / API touches

- Screen → endpoint map: backup → `GET export/resume`; wizard → `POST import/resume` (dry-run then confirm); adapters → `POST import/linkedin|github`. Session cookie + CSRF; Turnstile challenge embedded in wizard submit + adapter submits (abuse-shaped endpoints get the widget, backup download does not).
- Type mirror: `Envelope {schemaVersion, exportedAt, documents[], blocks[]}`; `DryRun {wouldCreate{documents, snapshots}, warnings[{path, reason}], errors[]}`. Code → UI map: `import_too_large|import_malformed|import_version_unsupported` → wizard step A/B states; `review_required` paths → inline flags.

## Acceptance criteria

- Backup drill: one click downloads dated file; toast states counts; no options, no failures on large libraries (streaming proven).
- Wizard drill: upload → dry-run preview with warnings → confirm names blast radius → done screen links open the created documents; re-export diffs ids/timestamps only.
- Rejection drill: each bad file shows its exact actionable state; wizard resets clean; server holds zero rows.
- Adapter drill: both adapters land in flagged drafts; public cv curl unchanged until save + publish; no token input exists anywhere (grep).
- Session drill: expired mid-wizard preserves the uploaded file client-side for re-submit post-login (file <2MB, memory-safe).

## Out of scope

- Export/import/adapter logic (backend plan above); envelope/fixture contracts (database plan above); editor and version UI (snapshot frontend plan); template rendering; share links; non-resume majors; continuous sync.

## Open questions

- Q1: In-place overwrite import (merge into existing document) — v2, or never (copies-only forever)?
- Q2: Adapter field mapping preview — per-field accept checkboxes, or whole-draft accept (current: whole + inline flags)?
