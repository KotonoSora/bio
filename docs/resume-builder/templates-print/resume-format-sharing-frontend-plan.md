# Resume Format Sharing Frontend Plan (builder + gallery + clones — UI build steps)

PLAN ref: P2-1 extension UX (form-based builder, placeholder preview, publish/clone/unpublish, report-abuse, attribution). BA source: `docs/resume-builder/templates-print/resume-format-sharing.md`. Server truth in `docs/resume-builder/templates-print/resume-format-sharing-backend-plan.md` (schema validation, scrub, versioning, takedown — never redefined here).

## Purpose

Give the frontend developer an ordered UI procedure for community formats: the form-only builder with fictional preview, publish versioning, gallery with attribution, one-click clones, and abuse reporting. Follow steps 1–7 in order. The builder can never touch real resume data — preview is placeholder by architecture, not by author discipline.

## Users

- Frontend developer: builds `/app/formats/*` and the public gallery views.
- QA: executes the placeholder, attribution, clone-isolation, and takedown drills.
- Format author / reuser / reporter: build, reuse, and report formats.

## Functional requirements

1. Routes: `/app/formats` (my formats: drafts + published + status chips + version lists) → `/app/formats/:id/edit` (builder) → `/app/formats/:id/preview` (placeholder render); public `/gallery/formats` (cards: thumbnail, name, author line, clone count, version) → `/gallery/formats/:slug` (detail: layout summary, versions, `based-on` where applicable, report link).
2. Step 1 — builder flow (form-only, no code editor anywhere): section picker (checkboxes + order drag) → per-section layout variant select → token swatches (font/scale/accent/density — swatches, never text inputs that could smuggle CSS) → A4 rules (margin presets, page-break hint toggles) → live placeholder preview (fictional person fixture, hard-coded client-side AND server-rendered — both ends use the placeholder, so a compromised client still cannot preview with real data because the preview endpoint accepts no snapshot input).
3. Step 2 — validation feedback flow: every builder change runs client-side schema pre-check (instant, same allowlist shape as the server) → server `PUT` on save-draft persists or returns field-mapped errors (`format_personal_data` names the offending key path, e.g. `sections.experience.items.text is resume data, not layout` — actionable, not `invalid input`). No raw-JSON editing mode exists (a JSON textarea would invite pasted resume exports — deliberately absent).
4. Step 3 — publish flow: `Publish` → pre-publish checklist dialog (layout-only attestation checkbox + placeholder-preview confirmation) → `POST publish` → `201 vN` → format appears in gallery with version chip → further edits create a new draft that publishes as vN+1 (the published card shows `vN live · vN+1 draft` states distinctly). Unpublish flips the card to `Unpublished — existing clones keep working` (copy states clone independence so authors don't fear breaking reusers).
5. Step 4 — clone flow (reuser): gallery card/detail `Use this format` → confirm (names the format + version pinned, `v3 — later author updates won't change your copy`) → `POST clone` → `201` lands in the reuser's template set, selected for their resumes like a built-in → `already:true` returns open the existing copy instead of duplicating. Clone `based-on` line (author username + profile link) renders on the reuser's format detail permanently.
6. Step 5 — attribution rendering: gallery cards/detail show author username + profile link (live links while the account exists); post-rename the links follow 301 (resolved at render from current profile rows); post-deletion the frozen string renders non-linked with `former member` styling (never resurrected, never reassigned — the client renders what the API returns, no special-casing).
7. Step 6 — report/takedown UX: `Report` on every gallery card/detail (Turnstile challenge inline, reason select + optional text, anonymous-allowed) → `Report received` (no tracking number theater) → taken-down formats vanish gallery-wide (detail URL shows `This format was removed`, clones untouched — reusers see nothing change). Moderator queue UI is ops-only (separate route, role-gated, reason-mandatory decisions).
8. Step 7 — edge cases: thumbnail upload (2MB + image-accept pre-check, progress, magic-byte errors mapped); publish with warnings (fix-before-publish list blocks the button, not a toast after); offline builder (draft persists in session storage, publish disabled with `You're offline`).

## Data / API touches

- Screen → endpoint map: my formats → `GET/POST /api/v1/resume-formats`; builder saves → `PUT /:id`; publish/unpublish → `POST /:id/publish|unpublish`; gallery → `GET /api/v1/gallery/formats` (public, throttled); clone → `POST /:id/clone`; report → `POST /:id/report` (Turnstile); moderation → ops endpoints (role-gated route only).
- Type mirror: `Format {id, slug, name, version, layout, thumbnail, status, authorUsername, cloneCount}`. No layout free-text editing (form controls generate the JSON; the client never hand-writes it).

## Acceptance criteria

- Placeholder drill: builder preview and gallery thumbnails show fictional content with a populated author resume present (screenshot comparison against the placeholder fixture).
- Publish drill: vN immutable after publish (edit creates draft → vN+1); unpublish copy states clone independence.
- Clone drill: pinned version stated at confirm; post-clone author changes invisible in the copy; `already:true` opens existing copy.
- Attribution drill: rename follows 301; deletion freezes the non-linked string; `based-on` permanent.
- Abuse drill: report completes with Turnstile; takedown hides gallery-wide within SLA; clones unaffected.
- Security drill: no raw-JSON editor exists (grep); no `user_id` in bodies; injections stripped on all surfaces.

## Out of scope

- Validation/scrub/version logic (backend plan above); table DDL (database plan above); snapshot versioning (snapshot plans); built-in registry (templates plans); share links (sharing plans); backup/adapters (portability plans).

## Open questions

- Q1: Builder density — single-page form vs stepped wizard for first-time authors?
- Q2: Clone-count display — exact counts always, or `100+` bucketing past a threshold?
