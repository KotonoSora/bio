# Resume Format Sharing - User Stories and Business Rules (feature 19)

Phase 1 BA · Enforced by `docs/02-architecture-techlead/19-resume-format-sharing/`. Scope: `docs/01-requirements-ba/19-resume-format-sharing/01-document-context-and-scope.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Format author | build a format from a placeholder preview, publish it to the shared gallery, and version or unpublish it | others can reuse my layout | Medium |
| US-02 | Reuser | browse the gallery, preview with placeholder data, and clone a format into my own resumes | I can reuse shared layouts | Medium |
| US-03 | Moderator/ops | remove abusive or infringing formats and answer attribution questions | the gallery stays safe | Medium |
| US-04 | Visitor (anonymous) | browse and preview published formats without an account | I can evaluate layouts before signing up | Low |

## Business rules

- **BR-01 Closed layout schema.** A format is `{sections order/visibility, per-section layout variant, tokens{font, scale, accent, density}, A4 rules{margins, page-break hints}}` - allowlist keys only. Personal-data keys (resume text, contact details, links), inline `style`, scripts, and external URLs are rejected with actionable field paths (`format_personal_data`, `format_bad_style`, `format_bad_script`, `format_bad_url`). Drafts are validated on every write (always publishable-shaped); no raw-JSON editing mode exists client-side.
- **BR-02 Placeholder invariant.** Author preview, builder live-preview, and gallery thumbnails render a fictional placeholder (client and server both enforce it); the preview accepts no snapshot input; a fully populated author resume changes nothing about what previews show.
- **BR-03 Immutable versions.** Publishing validates + scrubs, then inserts a new row `vN+1` (slug + version unique) and deletes the source draft in one transaction; published rows never update; edits after publish create a new draft seeded from the latest version. Unpublish flips status only.
- **BR-04 Clone is ownership.** Cloning asserts `status=published`, copies the exact `layout_json` into the reuser's set (pinned version named at confirm), and is idempotent (`already:true` opens the existing copy). Author edit/unpublish/takedown never propagates - clones store JSON, not a pointer; a format row with clones cannot be hard-deleted (`ON DELETE RESTRICT`; takedown is a status flip).
- **BR-05 Attribution.** Gallery cards, detail, and clone `based-on` lines show the author's cached username + profile link; while the account exists the link resolves live (rename -> 301 target); a deactivated/hidden account renders the frozen string non-linked (`former member` styling); purge (account deletion) deletes the rows - attribution never resurrects a purged username.
- **BR-06 Moderation.** Report-abuse exists on every format (Turnstile-protected, reporter-agnostic, anonymous allowed); ops takedown (`reason` mandatory, audited) hides the format gallery-wide within the purge SLA while clones keep working; a weekly re-scan job re-validates every published row against the personal-data key list and flags hits (quarantine stays a human decision).
- **BR-07 Gallery reads.** Public gallery reads scope `status=published` only (RLS `f_public` + query predicate); draft/unpublished/removed are author/ops-only; anonymous bursts are throttled with short-lived gallery caching.
- **BR-08 Selection of clones.** A cloned format is selectable on the owner's documents via `template_id = "fmt:{formatId}"`; selection validates that a clone row exists for the caller (foreign/unknown -> `422 unknown_template`); rendering uses the layout interpreter over the stored JSON; registry ids are unaffected.
- **BR-09 Unlimited parity.** Preview, print, and clone have no quotas, credits, or gates; shared formats render through the same snapshot + `window.print()` A4 pipeline as built-ins (Chrome/Safari/Firefox/Edge print previews in the release fixtures).
- **BR-10 Attribution integrity.** `author_username_cached` is frozen at publish and never rewritten historically; it updates only on the next publish by the same author (new version rows carry the current name); no author PII columns exist (no email, no real name) - privacy is columnar, not promissory.

## Constraints carried

- Formats never carry user data by construction (no column exists to leak; the validator + weekly re-scan are defense in depth, not the guarantee).
- Thumbnails: auto-generated placeholder renders only in v1 (Q-06); random file names; no user content in gallery assets.
- No pricing, plan gating, or paid formats appear here; any future monetization is a monetize-batch amendment.
