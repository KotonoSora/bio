# Resume Format Sharing (author layouts, share, reuse)

PLAN ref: P2-1 (templates live in `src/templates/`, screen + `@media print` A4, `window.print()` only). This doc extends that scope with community authoring and sharing: formats remain token/layout configs — never uploaded CSS/JS, never user data. Snapshot semantics in `documents/resume-snapshot.md`; gallery rendering in `templates-print/resume-templates.md`.

## Purpose

Let users define PDF resume formats (section layout + design tokens + A4 print rules) and share them for anyone to reuse, growing the template gallery without code deploys. The privacy invariant is absolute: a shared format carries layout only — zero personal information from the author's resume — while the author's identity stays publicly visible as attribution.

## Users

- Format author: *As a Format author, I want to build a format from a placeholder preview, publish it to the shared gallery, and version or unpublish it so that others can reuse my layout.* Builds a format from a placeholder preview, publishes it to the shared gallery, versions or unpublishes it.
- Reuser: *As a Reuser, I want to browse the gallery, preview with placeholder data, and clone a format into my own resumes so that I can reuse shared layouts.* Browses the gallery, previews with placeholder data, clones a format into their own resumes.
- Moderator/ops: *As a Moderator/ops, I want to remove abusive or infringing formats and answer attribution questions so that the gallery stays safe.* Removes abusive or infringing formats; support answers "whose format is this" from attribution alone.

## Functional requirements

1. Format definition (closed schema): `{sections order/visibility, per-section layout variant, tokens (font, scale, accent, density), A4 print rules (margins, page-break hints)}`. Allowlist-validated on write — snapshot data keys (experience text, contact details, links), inline `style`, scripts, and external URLs are rejected. No per-user CSS/JS, ever.
2. Authoring tool (dashboard `/app/*`, SPA): form-based builder only (section picker, order drag, token swatches, density toggle). Live preview renders exclusively with built-in placeholder content (fictional person), never the author's own snapshot — the author cannot preview or publish with real data.
3. Publish flow: draft → validate (schema + placeholder-only proof) → publish (immutable version `v1`, then `v2…` on edit; edits never mutate a published version). Unpublish hides from the gallery but preserves existing reusers' clones. Clone (= copy layout config into the reuser's own template set) is the only reuse path — there is no "link to author's live format."
4. Privacy scrub (enforced, not promised): publish pipeline re-validates the payload against the layout-only schema and strips anything else; stored format rows are independently re-scanned by a verification job. Any detection of personal-data keys blocks publish with an actionable error.
5. Attribution (public): gallery card and detail show the author's public identity (username + profile link) permanently, including on clones' "based on" line. Author username change follows the 301 profile rule; deleted accounts render attribution as a non-linked former-username string (never resurrected, never reassigned).
6. Safety: report-abuse action on every format; moderator takedown hides the format gallery-wide within the purge SLA while preserving reusers' existing clones (their copies are already their own data). Malware/HTML-injection attempts in format fields are rejected at validation.
7. Viewing and export parity: shared formats render through the same snapshot + `window.print()` A4 pipeline as built-in templates (Chrome/Safari/Firefox/Edge print preview). Export from the browser is unlimited — no quotas, credits, or gates on preview, print, or clone.

## Data / API touches

- New table `resume_formats{id, user_id TEXT NOT NULL + index + RLS, name, version INT, layout_json (layout-only schema), thumbnail_r2_key?, status (draft|published|unpublished|removed), author_username_cached, created_at, updated_at}` + `format_clones{format_id, user_id, cloned_at}` counts. Public gallery reads scope `status=published`; all other access scopes by session `user_id`. All PG via `worker/db/*`; TEXT ids, ISO8601 timestamps.
- Endpoints (JSON over `/api/v1/*`, cookie session): `GET/POST /api/v1/resume-formats` (mine), `GET /api/v1/gallery/formats` (public list + detail, rate-limited), `PUT /api/v1/resume-formats/:id`, `POST /api/v1/resume-formats/:id/publish|unpublish`, `POST /api/v1/resume-formats/:id/clone`, ops `POST /api/v1/ops/resume-formats/:id/remove` (restricted role, audited). Thumbnails in R2 random keys, placeholder-data renders only.
- Validation runs server-side on every write (schema allowlist + sanitization); publish additionally runs the scrub proof and records it in `audit_log{actor, action, target, at}` with the moderator trail where applicable.

## Acceptance criteria

- Scrub drill: publish attempted with snapshot/personal keys, inline styles, or scripts → rejected, zero rows published; verification job re-scan of all published formats finds zero personal-data keys. *GWT: Given a publish attempt with snapshot/personal keys, inline styles, or scripts; When validation runs; Then it is rejected with zero rows published and the verification re-scan finds zero personal-data keys.*
- Placeholder drill: author preview and gallery thumbnails contain only fictional placeholder content even when the author owns a fully populated resume. *GWT: Given an author with a fully populated resume; When author preview or gallery thumbnails render; Then they contain only fictional placeholder content.*
- Attribution drill: gallery card/detail and clone "based on" line show the author's username + profile link; post-rename follows 301; post-deletion shows the frozen former-username string. *GWT: Given a published format with an author; When gallery card/detail and clone "based on" lines render across rename or deletion; Then they show the author's username + profile link, follow 301 post-rename, and show the frozen former-username string post-deletion.*
- Clone drill: reuser clones a published format → own resumes render snapshot text with the cloned layout; later author edits/unpublish do not alter existing clones. *GWT: Given a published format; When a reuser clones it; Then the reuser's own resumes render snapshot text with the cloned layout and later author edits/unpublish do not alter existing clones.*
- IDOR probe (S-1/S-6): user A edits/publishes/clones-as-owner on user B formats → 403; direct PG as app role denied by RLS; public gallery readable anonymously within rate limits. *GWT: Given user B formats; When user A attempts edits/publishes/clones-as-owner or direct PG as app role; Then access returns 403 / is denied by RLS while the public gallery stays readable anonymously within rate limits.*
- XSS/CSP drill (S-2): format-field injection renders stripped on builder, gallery, dashboard preview, and cv SSR; CSP holds on all four surfaces. *GWT: Given format-field injection input; When rendered on builder, gallery, dashboard preview, and cv SSR; Then it renders stripped and CSP holds on all four surfaces.*
- Abuse drill (S-5): anonymous gallery bursts throttled (Turnstile + WAF + rate rules); report-abuse → takedown hides gallery-wide within purge SLA. *GWT: Given anonymous gallery bursts; When rate rules apply and report-abuse triggers takedown; Then bursts are throttled and takedown hides the format gallery-wide within purge SLA.*
- Secrets drill: no secrets in repo/logs/bundles; uploader rejects non-image bytes and 2MB+ files with magic-byte checks (avatar-baseline mechanics reused for thumbnails). *GWT: Given non-image bytes or 2MB+ files; When the uploader validates; Then they are rejected with magic-byte checks and no secrets appear in repo/logs/bundles.*

## Out of scope

- Snapshot capture/versioning and `resume`-block linkage (see `documents/resume-snapshot.md`); built-in template visuals beyond the shared renderer (see `templates-print/resume-templates.md`); JSON backup and LinkedIn/GitHub adapters (see `import-export/resume-portability.md`).
- Resume sharing link types (see `sharing/resume-sharing-links.md`); shop/leads/domains majors (never merged in); AI tailoring and plan gates (P3); publish/automations/campaigns/agency (P4); replicas/ISR (P5).

## Open questions

- Q1: Moderation model at launch — pre-publish review queue or publish-then-report with fast takedown?
- Q2: Format versioning UX — auto-offer reusers the new version, or silent pin to the cloned version?
- Q3: Author incentives — plain attribution only in v1, or usage counts/leaderboard (P4-growth)?
