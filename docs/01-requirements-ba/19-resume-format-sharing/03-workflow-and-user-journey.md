# Resume Format Sharing - Workflow and User Journey (feature 19)

Phase 1 BA · Flows executed by `docs/02-architecture-techlead/19-resume-format-sharing/`. Rules cited as `BR-nn` from `docs/01-requirements-ba/19-resume-format-sharing/02-user-stories-and-business-rules.md`.

## Author: build, publish, version

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Format author | Creates a format; uses the form builder (sections, variants, tokens, A4 rules) | Draft row saved; every write validated; live preview renders the placeholder fixture only | BR-01, BR-02 |
| 2 | Format author | Saves an invalid field combination | Field-mapped error names the offending key path (for example `sections.experience.items.text is resume data, not layout`) | BR-01 |
| 3 | Format author | Publishes | Pre-publish checklist (layout-only attestation + placeholder confirmation) -> validate + scrub proof -> insert `v{N+1}` published row, delete source draft, audit; gallery purge event | BR-03, BR-06 |
| 4 | Format author | Edits after publish | New draft seeded from the latest version; card shows `vN live - vN+1 draft` distinctly | BR-03 |
| 5 | Format author | Unpublishes | Status flip; card states `existing clones keep working`; gallery hides the format | BR-03, BR-04 |

## Reuser: browse, clone, use

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Visitor/Reuser | Opens the public gallery / a detail page | Published formats listed (cards: thumbnail, name, author line, clone count, version); detail shows layout summary + versions + report link | BR-07 |
| 2 | Reuser | Clicks `Use this format` | Confirm names the pinned version (`v3 - later author updates won't change your copy`) | BR-04 |
| 3 | Reuser | Confirms | `layout_json` copied into their set; clone row written; `201`; format becomes selectable like a built-in on their documents (`fmt:{formatId}`) | BR-04, BR-08 |
| 4 | Reuser | Clones again | `200 {already:true}`; the existing copy opens instead of duplicating | BR-04 |
| 5 | Reuser | Prints their resume with the cloned format | Same snapshot + `window.print()` A4 pipeline as built-ins; no quotas | BR-09 |

## Attribution across account states

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Author | Renames username | Gallery/detail links resolve live to the 301 target | BR-05 |
| 2 | Author | Deactivates (grace) or becomes hidden | Links stop resolving; the frozen cached string renders non-linked (`former member` styling) | BR-05 |
| 3 | Author | Account purged (deletion completes) | Rows deleted by the feature 02 participant; gallery items vanish; the username is never resurrected in attribution | BR-05 |

## Moderation

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Anyone | Reports a format (Turnstile + reason) | `202` intake to the ops queue; reporter identity never exposed to the author | BR-06 |
| 2 | Moderator/ops | Removes (`reason` mandatory, audited) | Status `removed`; gallery-wide hide within the purge SLA; clones untouched; detail shows `This format was removed` | BR-06 |
| 3 | System (weekly) | Re-scan of published rows | Hits flagged for human review + `formats.rescan_hit` metric; no automatic takedown | BR-06 |

## Privacy probes

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Attacker | Publishes layout JSON with resume data keys, inline styles, scripts, or URLs | Rejected with field-mapped codes; zero rows published | BR-01 |
| 2 | Attacker | Attempts to edit/publish another author's format | `403`; RLS denies direct database access; anonymous reads stay published-only | BR-07 |
| 3 | Auditor | Inspects published rows and assets | No personal-data keys; placeholder-only thumbnails; no PII columns exist | BR-02, BR-10 |
