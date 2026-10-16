# Resume Documents - UI Data Dictionary (feature 17)

Phase 1 BA · Fields the SPA renders in `/app/resumes/*`. Contracts: `docs/02-architecture-techlead/17-resume-documents/`. Rules: `docs/01-requirements-ba/17-resume-documents/02-user-stories-and-business-rules.md`.

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| Document list row | Row | Auto | Title, template chip, public badge, updated time; actions rename/duplicate/delete | Sorted by updated desc |
| New resume action | Button | Yes to start | Prompt for title (`<= 120`); creates v0 with empty draft | Hidden when document limit guidance fires |
| Title inline edit | Text field | Yes | `1-120` chars, trimmed; server-validated | Current title |
| Draft pill | Chip | Auto | `Draft - unsaved changes` appears when draft differs from the last snapshot | Hidden when clean; never co-occurs with the frozen pill |
| Frozen pill | Chip | Auto | `Snapshot v{N} - frozen` with distinct background on version views | Only on timeline views |
| Save Snapshot button | Button | Auto | Always visible; disabled with no draft changes; disabled while pending (double-click safe) | Enabled on change |
| Version timeline | List | Auto | Version number, timestamp, source rev; click opens frozen view; archived entries labeled `Archived - retrieved from backup` | Newest first |
| Restore as draft | Button | No | Copies version content into the draft; does not rewrite history | On frozen views |
| Publish bar | Bar | No | `Publish` on a selected version; confirm names any displaced public document; copy for unpublish states the 404 bound | On saved versions |
| Public badge + View live CV | Chip + link | Auto | Shown when `isPublic`; link opens `/:username/cv` | Hidden otherwise |
| Delete confirmation | Dialog | Yes to delete | Checkbox confirm required only when a visible linked block exists; consequence named ("Also hides the resume section on your public page") | Cancel default focus |
| Template picker | Gallery component | No | Embedded from feature 18's gallery (not reimplemented); selection re-renders; copy states versions are untouched | Current template highlighted |
| Oversized notice | Inline error | Auto | `422` mapping: `Resume too large` + section-level guidance; draft intact | Hidden by default |
| Capture error notice | Inline banner | Auto | `503 capture_unavailable`: "Couldn't read your profile - try again"; draft intact | Hidden by default |
| Archived slow-load notice | Banner | Auto | Stated upfront before retrieval completes; never a silent spinner | On archived version open |
| Session-recovery banner | Banner | Auto | Expired session mid-draft: re-login resumes the preserved draft | Only after expiry |

Copy discipline: no template styling promises here (feature 18); no sharing formats (19/20); no backup/import copy (21).
