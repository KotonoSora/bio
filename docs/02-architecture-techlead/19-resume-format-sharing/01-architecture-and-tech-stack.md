# Resume Format Sharing - Architecture and Tech Stack (feature 19)

Phase 2 TL · Service: `resume-service` extension (`resume_db` migration `V2__format_sharing.sql`; no new service). Implements `docs/01-requirements-ba/19-resume-format-sharing/`. Renders through feature 18's pipeline; baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | Formats live in `resume-service`: publish (validate + scrub + insert + audit) commits atomically; clone copies share the same RLS baseline. | Legacy monolith argument survives the service split (single service either way); a separate format service would add gallery-auth plumbing for catalog traffic with no trigger. |
| D-2 | Version rows are immutable: publish inserts `vN+1` under `(slug, version)` unique and deletes the source draft in one transaction; published rows never UPDATE. | Gallery stability for existing viewers; edits after publish seed a new draft. |
| D-3 | The SPA gains a layout interpreter over the closed schema; built-in registry templates (feature 18) express the same schema internally, so one render pipeline serves both. | One screen/print pipeline; community formats never bypass the token-only discipline. |
| D-4 | Clone stores the JSON (copy-not-pointer); selection rides feature 17's `template_id` as `fmt:{formatId}`, validated against the caller's clone rows; foreign/unknown -> `422 unknown_template`. | Ownership is structural; author-side changes cannot propagate because no live reference exists. |
| D-5 | Attribution: `author_username_cached` frozen at publish; links resolve live while the account is public (rename -> 301 target); hidden/deactivated renders the frozen string non-linked; purge deletes rows. DEVIATION FLAGGED: the legacy "deleted accounts keep attribution" wording is decomposed - deactivation keeps the frozen string, account purge (feature 02, absolute) removes rows. | Reconciled with the platform's absolute-purge contract; the username is never resurrected or reassigned. |
| D-6 | Moderation is publish-then-report (Q-01) with a weekly privacy re-scan that flags, never auto-removes; ops removal is an internal route with mandatory reason. | No staffed pre-publish queue exists; humans decide quarantines. |
| D-7 | Thumbnails are auto-generated placeholder renders only (Q-06); no upload route exists. DEVIATION FLAGGED vs the legacy author-thumbnail upload; the upload surface opens only via amendment. | Privacy invariant stays structural; attack surface not opened. |

## Boundaries

This feature owns: format rows, validator + scrub proof, publish/unpublish lifecycle, clone mechanics, gallery read endpoints + attribution rules, report intake, ops removal, re-scan job, layout interpreter contract (SPA). It does not own: snapshot/document storage (feature 17), built-in registry (feature 18), SSR `/cv` mechanics (feature 10), share links (20), portability (21), plan values (32-40; none apply - formats are ungated).

## Consumers

| Consumer | Contract |
|---|---|
| SPA dashboard (author/reuser) | My-formats CRUD, publish/unpublish, clone, builder with placeholder preview |
| SPA render pipeline (feature 18) | Layout interpreter consumes `layout_json` for screen + print trees of cloned formats |
| Public gallery (anonymous) | `GET /api/v1/gallery/formats` (+ detail), edge-cached, throttled |
| Ops tooling | Report queue + `POST /internal/v1/resume-formats/{id}/remove` (audited) |
| Feature 02 (purge) | Participant deletes `formats`, `format_clones` rows + thumbnail files (roster row updated in this change) |
| Profile resolve (feature 06) | Attribution live-link resolution (rename 301, hidden -> frozen string) via the existing internal read |

## Stack notes

Spring Boot `4.1.1` / Kotlin `2.4.21` unchanged; `@Scheduled` re-scan job (core Spring). New internals: `FormatValidator` (pure, table-tested), `PublishService` (tx + scrub proof), `CloneService`, `GalleryController`, `FormatRescanJob`. Thumbnails rendered by the fixture-only pipeline (typed placeholder inputs); file names random; no new Redis keys beyond short-lived gallery cache entries (`gallery:formats:{page}` 60 s, owned here).
