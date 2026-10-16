# Resume Format Sharing - Context and Scope (feature 19)

Phase 1 BA · Feature 19 of 53 (resume) · Implemented by `docs/02-architecture-techlead/19-resume-format-sharing/` (5 files); extends `resume-service` (`resume_db` V2) with a public gallery surface. Renders through feature 18's pipeline; selection rides feature 17's document update.

## Purpose

Let users define PDF resume formats (section layout + design tokens + A4 print rules) and share them for anyone to reuse, growing the template gallery with zero code deploys. Formats are layout-only configs - never uploaded CSS/JS, never user data. The privacy invariant is absolute: a shared format carries no personal information from the author's resume, while the author's identity stays publicly visible as attribution.

## Objectives

- O-1 Layout-only by construction: a closed schema (sections order/visibility, layout variants, tokens, A4 rules) is the only thing that can be stored; personal-data keys, inline styles, scripts, and external URLs are rejected at validation.
- O-2 Placeholder forever: author preview and gallery thumbnails render a fictional placeholder even when the author owns a fully populated resume; the preview accepts no snapshot input.
- O-3 Immutable versions: publishing inserts vN+1; published rows never update; existing viewers and clones are never destabilized.
- O-4 Clone is ownership: cloning copies the layout into the reuser's own set; author edit/unpublish/takedown never propagates (no live reference exists).
- O-5 Moderation with teeth: report on every format, ops takedown hides gallery-wide within the purge SLA, clones untouched; a weekly privacy re-scan flags regressions.

## Personas and RBAC

| Role | CRUD Permissions | Description |
|---|---|---|
| Format author | Full CRUD on own formats (draft, publish, version, unpublish) | Builds from a placeholder preview, publishes, versions, unpublishes. |
| Reuser | Create (clone own copy); Read gallery | Browses the gallery, clones a format pinned to a version; owns the copy. |
| Visitor (anonymous) | Read: published formats only | Browses the gallery within rate limits; can report abuse. |
| Moderator/ops | Update: status flip to `removed` (reason mandatory), audited | Removes abusive/infringing formats; answers attribution from public columns alone. |
| Attacker (adversarial) | none | Cannot smuggle personal data, CSS/JS, or foreign-format control. |

## Resolved open questions

| Q | Decision |
|---|---|
| Q-01 Moderation model | Publish-then-report with fast takedown + weekly privacy re-scan; no pre-publish queue (no staffed moderation exists; a queue requires a staffing amendment). |
| Q-02 Version upgrade offers | Silent pin: clones store the layout JSON; existing clones are never offered or changed by author updates in v1. |
| Q-03 Author incentives | Plain attribution only in v1; usage counts/leaderboards require an amendment. |
| Q-04 Builder density | Single-page form builder (no stepped wizard in v1). |
| Q-05 Clone-count display | Exact counts (query-time with short cache); `100+` bucketing is deferred. |
| Q-06 Author thumbnail uploads | Deferred: v1 thumbnails are auto-generated placeholder renders only - the upload attack surface is not opened. DEVIATION FLAGGED: the legacy plans listed author-uploaded thumbnails; this deferral is deliberate (amendment required to enable). |

## In scope

- Format authoring (form builder server contract), closed-schema validation with actionable field errors, draft/publish/unpublish lifecycle, immutable version rows.
- Public gallery (list + detail, rate-limited, anonymous-readable), attribution rendering rules, report-abuse intake.
- Clone mechanics (copy-not-pointer), selection of cloned formats by the owner, weekly privacy re-scan job, ops takedown path.

## Out of scope

- Built-in template registry and render contract (feature 18); document/version storage (feature 17); share links (feature 20); portability (feature 21).
- Author thumbnail uploads (deferred, Q-06); pre-publish moderation queues (Q-01); leaderboards/usage counts (Q-03); payments for formats (never in v1; monetize features own any future pricing).
- AI-generated formats (feature 16, gated).

Related: rules in `docs/01-requirements-ba/19-resume-format-sharing/02-user-stories-and-business-rules.md`; contracts in `docs/02-architecture-techlead/19-resume-format-sharing/03-api-contracts-and-payloads.md`; error surface in `docs/01-requirements-ba/19-resume-format-sharing/05-exception-handling-and-errors.md`.
