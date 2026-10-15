# Resume Render and Share (execution plan R1 — templates, print, links, formats)

PLAN ref: P2-1 (screen + `@media print` A4, `window.print()` only, token templates) + P1c-1/P1c-2 (SSR tags, cache-first `s-maxage=60-300` + SWR, purge, miss-storm guard) + S-1/S-2/S-5/S-6. BA source: `docs/resume-builder/templates-print/resume-templates.md`, `docs/resume-builder/sharing/resume-sharing-links.md`, `docs/resume-builder/templates-print/resume-format-sharing.md`. Builds on `docs/platform/delivery/resume-documents.md`. DEVIATION CARRIED: server rendering runs in the Spring Boot artifact (SSR origin) behind cache, not Workers Hono; PDF stays browser `window.print()` with zero server PDF engines.

## Purpose

Ship everything the recruiter and the reuser see: token-based templates, the A4 print pipeline, the five sharing link types over one frozen snapshot, and the community format gallery with its layout-only privacy invariant. R1 adds no new snapshot semantics — it renders and shares R0 revisions through one pipeline with text parity on every path.

## Users

- Job seeker: previews templates, prints/saves PDF, copies any of the five link types, authors or clones formats.
- Recruiter/crawler: reads identical snapshot text on every link type with no login (crawler needs no JS).
- Moderator/ops: removes abusive formats; purges cache on publish events.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6`, Boot `4.1.1`, Kotlin `2.4.21`, Gradle `9.8.1`. Architecture verdict for R1 — monolith with edge cache in front, not microservices. Monolith pros: template renderer, CV SSR controller, share-link derivation, and gallery CRUD share the `resume` module transaction and RLS baseline; no distributed text-parity problem (five link types read the same revision in-process). Microservices cons avoided: splitting SSR, gallery, or print into services would multiply IDOR/CSP surfaces and cache-purge coordination for read paths that differ by far less than 10x at P2 gates. Recorded split trigger: top-slug hot-spot pre-render/ISR extracts at P5 scale-out only, on measured cache-hit collapse — not in R1. Pro-gated premium templates check plan server-side (never UI-only).
2. Templates and print: gallery (`thumbnail, name, template_id`) switching re-renders the same snapshot without mutating it; templates are token/layout configs (font, scale, accent, density) — no per-user CSS/JS, no inline `style` from user fields, no Node-only or headless-browser imports (grep gate). Screen markup stays interactive (collapse, zoom, density); print markup is a separate read-only tree with `@media print` A4, `print-color-adjust: exact`, break-inside avoidance for experience entries. Export is `window.print()` only — no Puppeteer/LibreOffice/server PDF bytes in the image. SSR `/:username/cv` reuses the same renderer server-side so HTML text matches print text.
3. Five sharing link types (one snapshot, uniform revocation): (a) standard `/:username/cv`; (b) SEO variant = same URL with title/meta/canonical/OG/Twitter + JSON-LD `Person`, canonical rewritten per serving host; (c) shortened (shortener mint whose destination is the canonical CV URL, PII-free slugs, expiry per shortener policy, safe-error on dead slugs, QR targets the short URL); (d) custom host (verified-only serving, canonical = custom host, downgrade detaches serving with DNS untouched); (e) Link-to-Bio `resume`-block embed (hide/unpublish removes without breaking the page). All require `is_public=1` + selected public snapshot; flipping either 404s/safe-errors every type within the purge SLA. Sharing/views/browser-PDF export are unlimited — no quotas or plan gates on reads. Read-only helper `GET /api/v1/documents/:id/share-links` derives the five URLs for copy buttons (owner-scoped).
4. Format sharing (layout only): closed schema `{section order/visibility, per-section layout variant, tokens, A4 print rules}` allowlist-validated on every write — snapshot keys, inline styles, scripts, external URLs rejected. Form-based builder previews with fictional placeholder content only (never the author's snapshot); publish runs a scrub proof (draft → validate → immutable `v1`, `v2…` on edit; unpublish preserves clones; clone = copy config, never a live link). Verification job re-scans stored formats for personal-data keys. Attribution (username + profile link, 301-following; frozen non-linked string after account deletion) on cards, detail, and clone "based on" lines. Report-abuse + restricted moderator takedown hides gallery-wide within purge SLA while preserving existing clones. Thumbnails: 2MB cap + magic-byte image check → random blob key, placeholder renders only.
5. Cache and abuse: CV HTML keyed per host + snapshot revision, `public, s-maxage=60-300, SWR=600`, purge on snapshot/template/username/domain/format-clone-affecting changes, single-flight on miss storms. Anonymous CV/gallery/short/custom paths rate-limited with bot-defense challenge; legitimate unlimited views unaffected.

## Data / API touches

- Tables: no new tables except `resume_formats{id, user_id TEXT NOT NULL + index + RLS, name, version INT, layout_json, thumbnail_key?, status draft|published|unpublished|removed, author_username_cached, created_at, updated_at}` + `format_clones{format_id, user_id, cloned_at}`. Reads `documents{snapshot_json, template_id}` + `profiles{username, is_public}` + `profile_blocks(type=resume)`; short-link and `custom_domains` rows owned by their majors. App role + RLS; gallery public reads scope `status=published`.
- Endpoints: template selection reuses `PUT /api/v1/documents/:id`; formats `GET/POST /api/v1/resume-formats`, `GET /api/v1/gallery/formats` (public, rate-limited), `PUT /api/v1/resume-formats/:id`, `POST /api/v1/resume-formats/:id/publish|unpublish|clone`, ops `POST /api/v1/ops/resume-formats/:id/remove` (restricted, audited). SSR `/:username/cv` served by the Spring artifact behind cache/CDN proxy.
- Blobs: thumbnails under random keys; no PDF bytes stored in v1.

## Acceptance criteria

- Content drill: same snapshot text-identical across all templates (layout differs only); print renders frozen snapshot, not draft, unless explicitly saved.
- Print drill: A4 single-column flow, no dashboard chrome, colors exact, verified in Chrome + Safari print preview (Firefox/Edge for shared formats).
- Parity drill: identical snapshot text via curl (no JS) on all five link types; template switch changes layout only; draft edits invisible until save.
- Privacy drill: private/unlinked → 404 (safe-error on short); re-publish restores within purge SLA; username change 301s; custom unverified host never renders.
- Format drills: scrub (personal keys/styles/scripts → publish rejected, zero rows; job re-scan finds zero personal keys); placeholder (previews/thumbnails fictional only); attribution (rename/deletion rules); clone isolation (author edits/unpublish never alter clones).
- Security drills: S-1/S-6 IDOR 403 + RLS deny (formats included, gallery anonymous-readable within limits); S-2 injection stripped on builder/gallery/preview/CV with CSP holding; S-5 anonymous bursts throttled; thumbnail upload rejects non-image/2MB+; secrets grep clean.
- Container drill: R1 tag runs under Docker AND Podman from empty PG via Flyway from V1; OpenAPI diff additive-only; rollback green.

## Out of scope

- Snapshot versioning/block linkage (see `docs/platform/delivery/resume-documents.md`); JSON backup/adapters (see `docs/platform/delivery/resume-portability.md`); shortener/domain/bio-block internals (owning majors — consumed); plan gates/AI (P3); P4/P5 except the recorded P5 pre-render trigger.

## Open questions

- Q1: v1 built-in template count — 3 classic layouts vs single layout + density options?
- Q2: Moderation at launch — pre-publish review queue vs publish-then-report with fast takedown?
- Q3: Cover-letter print — second page of the same template vs separate `template_id`?
