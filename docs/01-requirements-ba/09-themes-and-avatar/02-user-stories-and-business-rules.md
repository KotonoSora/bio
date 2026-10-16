# Themes and Avatar - User Stories and Business Rules (feature 09)

Phase 1 BA · Stories seed `06-acceptance-criteria.md`; rules `BR-xx` are quoted by every later file.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Creator | browse a gallery of platform themes with pro badges | I can choose a look without writing code | Must |
| US-02 | Creator | preview a theme instantly before it is saved | I see the result before committing | Must |
| US-03 | Creator | save a theme and have the server enforce plan gating | broken or unpaid selections are impossible | Must |
| US-04 | Creator | upload an avatar image and see it everywhere | my page carries my face or logo | Must |
| US-05 | Creator | upload a short looping video where my theme allows it | my page feels alive without breaking others | Should |
| US-06 | Creator | delete my avatar and get the default glyph back | I can reset presentation idempotently | Must |
| US-07 | Visitor | a fast page that honors exactly what the creator previewed | the promise of the preview is kept | Must |
| US-08 | Public-site service | read the theme catalog and token map internally | SSR inlines token CSS without hardcoding themes | Must |

## Business rules

- **BR-01 Token-only.** Exactly one styling input exists (`theme_id`). No custom CSS, font-URL, HTML-head, or color-picker field may exist in UI, API, or schema; grep + ArchUnit gate the build. Themes carry tokens `{bg, text, font, radius, linkStyle, animated, customLogo}`.
- **BR-02 Code-shipped registry.** Theme definitions live in code, never as rows; adding a theme is a deploy + review event (versioned with the renderer that interprets it). `GET /api/v1/themes` serves the registry.
- **BR-03 Server-side plan gate.** `proOnly` themes are rejected for unentitled accounts with `403 theme_forbidden` even on direct API calls; UI hiding is honesty, not the gate. If entitlement status cannot be resolved, pro themes fail closed; standard themes are unaffected.
- **BR-04 Upload validation.** Single file, <= 2 MB (enforced while reading, never unbounded buffering); magic-byte verification (JPEG `FF D8 FF`, PNG `89 50 4E 47`, WebP `RIFF....WEBP`, MP4 `ftyp`); extension and claimed MIME are never trusted. Rejected uploads leave zero objects in storage.
- **BR-05 Keys and namespaces.** Server-generated random keys `avatars/{ulid}.{ext}`; objects immutable (new upload = new key; the previous key is deleted only after the profile row update commits). Verification evidence uses the separate `verification/` namespace (feature 07).
- **BR-06 Video rules.** MP4 accepted only when the active theme's `videoAvatar` is true (else `422 avatar_video_not_allowed`), loop <= 10 s (else `422 avatar_video_too_long`), same 2 MB cap as images.
- **BR-07 Write ordering.** Blob PUT -> profile row update -> old-key delete; a crash between steps leaves one orphan object, swept after 24 h by the hygiene job - never blocking the request.
- **BR-08 Caching.** Avatar objects and theme thumbnails carry `Cache-Control: public, max-age=31536000, immutable` (thumbnails fingerprinted by theme version); `GET /api/v1/themes` is cacheable 300 s. A theme switch never changes the avatar key.
- **BR-09 Publish honesty.** Saving a theme/avatar persists immediately and publishes `profile.published.v1`; the shared purge chip shows `publishing...` until the public page catches up (<= 2 min, TTL-healed). The unsaved preview is client-side only; the draft preview iframe is a feature 10 `private, no-store` mode. A pro theme a free user cannot save previews only via its static thumbnail - never via client-side recoloring.
- **BR-10 Delete idempotency.** `DELETE /api/v1/profile/avatar` returns 204 even when the object is already gone; the row key clears unconditionally.
- **BR-11 No new tables.** This feature adds zero tables (a theme/avatar table is a layering violation); a grep gate and review rule enforce it.
- **BR-12 Scoping and abuse control.** Session `user_id` only (no ids in bodies); CSRF on mutations; Turnstile + rate limits on upload paths.
