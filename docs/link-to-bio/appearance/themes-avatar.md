# Themes + Avatar (appearance without custom code)

PLAN ref: P1b-3, P1b-4.

## Purpose

Define how a Link-to-Bio page looks without ever allowing user-supplied code. Themes are platform-owned token sets; avatars are platform-validated image uploads. This gives creators visual identity while making CSS/JS injection structurally impossible.

## Users

- Creator: picks a theme, uploads an avatar, previews the result.
- Visitor: sees a fast, consistent page honoring the chosen tokens and avatar.

## Functional requirements

1. Theme gallery in dashboard: each theme is `{theme_id, name, tokens(bg, text, font, radius, link-style), thumbnail}`. Switching `theme_id` restyles dashboard preview and public page; no other styling input exists.
2. Tokens only: no custom CSS, font-URL, or HTML-head fields anywhere in profile or block editors.
3. Premium theme rules (if applied): animated links and custom-logo themes gated server-side by plan; UI hiding alone is not the gate.
4. Avatar upload: single image, 2 MB cap, magic-byte verification (not extension/MIME-sniffing), stored under an unguessable R2 random key in `RESUME_ASSETS`; hotlinking or SVG/HTML uploads rejected or neutralized.
5. Video-avatar support per PLAN: short looping avatar where the theme allows; same size/type validation as images.
6. Preview-before-publish: theme/avatar changes stage in dashboard and apply publicly on publish (which also purges the public-page cache per `public-page/share-seo.md`).

## Data / API touches

- No new tables. `profiles.theme_id` + `profiles.avatar_r2_key` on the existing profile row (see `profile/public-profile.md`). Theme definitions ship as code in `src/themes/`, never as user rows.
- Endpoints: `GET /api/v1/themes` (public list), `PUT /api/v1/profile/theme`, `POST /api/v1/profile/avatar` (multipart, server-validated), `DELETE /api/v1/profile/avatar`. Upload path validates size + magic bytes before any R2 write; R2 key is server-generated random, never client-chosen.
- Rendering reads tokens + avatar key; public SSR inlines only token-derived CSS. Avatar served from R2/CDN with immutable cache headers keyed by content.

## Acceptance criteria

- Token switch restyles page end-to-end with zero user CSS fields present in UI, API, or DB schema.
- Fake image (renamed script), SVG/HTML, and 5 MB uploads: all rejected; nothing lands in R2.
- Uploaded avatar key is non-sequential and unguessable; direct R2 URL guessing does not enumerate users.
- Pro-only theme requested by free plan via direct API call: rejected 403 (server-side gate, not UI-only).
- Rapid upload abuse throttled (Turnstile + WAF/rate rules on anonymous-adjacent paths).
- Publish after theme/avatar change: public page reflects the change within the cache-purge SLA (<2 min).

## Out of scope

- Block content model (see `blocks/link-blocks.md`); resume templates and print CSS (`docs/resume-builder/templates-print/`); public SSR tag/cache mechanics (see `public-page/share-seo.md`).
- Shop/course/tip rendering themes, email campaign styling, agency white-label (separate majors / P4).
- Native mobile app (PWA install prompt instead, per PLAN).

## Open questions

- Q1: v1 theme count and whether one video-avatar-capable theme ships at launch?
- Q2: Avatar crop — client-side cropper with server re-validation, or upload-as-is with CSS crop?
