# Themes Avatar Frontend Plan (gallery + uploader + preview — UI build steps)

PLAN ref: P1b-3/P1b-4 dashboard UX (gallery, upload, preview-before-publish). BA source: `docs/link-to-bio/appearance/themes-avatar.md`. Server truth in `docs/link-to-bio/appearance/themes-avatar-backend-plan.md` (codes, caps, gates — never redefined here).

## Purpose

Give the frontend developer an ordered UI procedure for appearance: gallery rendering with pro gating, instant token preview, the validated uploader flow, and publish integration. Follow steps 1–6 in order. The client previews freely but persists only through the server (the gate lives there).

## Users

- Frontend developer: builds `/app/appearance` against the backend plan's contracts.
- QA: executes the gallery, upload, and rollback drills.
- Creator: picks a theme, uploads an avatar, previews, publishes.

## Functional requirements

1. Route and state: `/app/appearance` (owner-only) holds the theme gallery grid + avatar card + publish bar (shared with profile/block editors). Client state: `themes[]`, `activeThemeId`, `avatarKey`, `previewThemeId` (unpersisted selection), `upload {progress, error}`. No theme definitions hardcoded — gallery renders from `GET /api/v1/themes` so a code-shipped theme appears with no client change.
2. Step 1 — gallery flow: mount → `GET themes` → cards (thumbnail, name, pro badge where `proOnly`) → free plan clicking a pro card opens the upgrade sheet instead of calling `PUT` (the server would 403 anyway; the sheet is honesty, not the gate) → permitted click sets `previewThemeId` instantly (page chrome re-skins through token CSS variables — this is preview, not saved; an `Unsaved theme` pill appears).
3. Step 2 — persist/rollback flow: `Save theme` → `PUT /api/v1/profile/theme {themeId}` → `200` promotes preview to active, clears pill, sets purge-pending chip → `403 theme_forbidden` (plan changed mid-session, or forced call) rolls back to the active theme with an upgrade prompt → `404 theme_not_found` (registry changed) refreshes the gallery. Switching themes never touches blocks or avatar state.
4. Step 3 — uploader flow: file pick (accept image/* + video where the active theme allows loops, 2MB client pre-check with `avatar_too_large` text before upload) → multipart `POST avatar` with progress bar → `200 {avatarKey}` swaps the avatar everywhere (form, preview, header) → `422 avatar_bad_type` shows inline `Images only (PNG/JPEG/WebP)` — the message names formats, never MIME internals → delete restores the default glyph via `DELETE` (idempotent, missing object still clears).
5. Step 4 — preview-before-publish integration: theme/avatar changes are dashboard-local until the shared publish action; the preview iframe renders the public page with draft tokens + draft avatar (served through the share-seo preview mode, `private, no-store`); publish emits the backend event and the purge chip from the profile frontend plan takes over (this screen does not implement its own purge polling — one chip component, reused).
6. Step 5 — edge cases: upload during theme save (both pending allowed — independent resources, independent rollbacks); expired session mid-upload (re-login keeps the selected file name, requires re-pick for bytes — browsers, not us); 5MB drop shows the size error before a single byte uploads.
7. Step 6 — honesty rules: never fake a pro theme by recoloring client-side (preview of a pro theme a free user cannot save is limited to the upgrade sheet's static thumbnail); never show an avatar as `saved` before the `200` lands.

## Data / API touches

- Screen → endpoint map: gallery → `GET /api/v1/themes`; save → `PUT /api/v1/profile/theme`; upload/delete → `POST|DELETE /api/v1/profile/avatar`. Session cookie + CSRF throughout.
- Type mirror: `Theme {id, name, tokens, thumbnail, proOnly}`. Code → UI map: `theme_not_found` → gallery refresh; `theme_forbidden` → upgrade prompt + rollback; `avatar_too_large|avatar_bad_type|avatar_video_not_allowed` → uploader inline errors.

## Acceptance criteria

- Gallery drill: new code-shipped theme appears with no client deploy; pro card on free opens upgrade, never persists.
- Preview drill: selection re-skins instantly with `Unsaved` pill; save clears pill + sets purge chip; forced-403 rolls back.
- Upload drill: 5MB/renamed-script/SVG blocked with exact inline text; progress bar advances; delete restores glyph.
- Publish drill: draft tokens visible in preview iframe only; public page unchanged until publish.
- Security drill: no theme-token authoring UI exists (no color pickers bound to CSS output); no `user_id` in bodies.

## Out of scope

- Registry/upload logic (backend plan above); namespaces and cache headers (database plan above); profile/block/trust screens; SSR fragment internals (share-seo plans).

## Open questions

- Q1: Gallery layout — grid with large thumbnails, or list with live mini-previews?
- Q2: Uploader crop step — ship cropper v1 or defer per backend Q2?
