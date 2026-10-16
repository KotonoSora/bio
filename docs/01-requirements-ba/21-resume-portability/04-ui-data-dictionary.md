# Resume Portability - UI Data Dictionary (feature 21)

Phase 1 BA · Backup UI, restore wizard, and adapter flows in the SPA. Contracts: `docs/02-architecture-techlead/21-resume-portability/`. Rules: `docs/01-requirements-ba/21-resume-portability/02-user-stories-and-business-rules.md`.

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Download backup | Button | No | Per document and whole-library entry points; no options dialog (format is fixed-versioned - options would imply choices that don't exist) | Streams with progress for large libraries |
| Export toast | Toast | Auto | States what left the building: `{N} documents, {M} snapshots downloaded` | After download completes |
| Restore entry | Button | No | Opens the wizard (`Import backup`) | Beside the backup button |
| Wizard step A (upload) | Dropzone | Yes to start | Client pre-checks extension + 2MB before upload; server gates repeat both | Reset after any rejection |
| Dry-run preview | Summary panel | Auto | Documents-to-create with titles, snapshot counts, warnings list (`path` + `reason`), `public_conflict` list when present | Auto-called (`?dry_run=true`); no writes |
| Warnings | List rows | Auto | Reviewable items, never silent fixes: unknown keys, clamped URLs, sanitized fields each named by path | Collapsible after first view |
| Confirm step | Dialog | Yes to import | Names the blast radius: `Will create {N} documents. Nothing will be overwritten.` (copies-only, Q-05) | Cancel default focus |
| Done screen | Screen | Auto | Created documents with `Open` links + audit-visible timestamp | Terminal state |
| Avatar note | Inline text | Auto | `Profile photos aren't included` (Q-03) | On the wizard and adapter review |
| Adapter cards | Cards | No | LinkedIn + GitHub on the new-user empty state AND in settings (existing users move accounts too) | Both cards always present |
| LinkedIn upload tab | File input | Yes to import | Export-file only (`Most reliable` label); no URL tab exists (Q-02); Turnstile inline | Single tab |
| GitHub handle input | Text field | Yes to import | `^[a-zA-Z0-9-]+$` validated client- and server-side; progress `Fetching public repos...` stated upfront | Placeholder `octocat` |
| ImportReview | Review component | Auto | One component, three callers (restore, LinkedIn, GitHub); mapped fields with `Needs review` flags | Every adapter-filled field flagged |
| `Create draft` action | Button | Yes to land draft | Creates `is_public=false` draft; editor opens with review flags inline | Disabled until review acknowledged |
| Provenance banner | Banner | Auto | `Imported from {LinkedIn\|GitHub} - review before saving` until first Save Snapshot | Draft documents only |
| Token input | - | - | Does not exist anywhere in this flow (asserted absent in review - a paste-token input here would be phishing-shaped) | Absent by design |
| Rejection states | Inline errors | Auto | `import_too_large` -> size text (pre- and post-upload, same message); `import_malformed` -> `Couldn't read this file` + position; `import_version_unsupported` -> `Made with a newer app version (vX). Supported: vY.` | Wizard resets to step A cleanly |
| Session-recovery banner | Banner | Auto | Expired session mid-wizard: the uploaded file is preserved client-side (memory-safe, `< 2MB`) for re-submit post-login | Only after expiry |

Copy discipline: no overwrite promises (copies-only), no sync language (one-shot imports), no token surfaces, no quota text on export (unlimited by design).
