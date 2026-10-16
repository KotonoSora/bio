# Themes and Avatar - Workflow and User Journey (feature 09)

Phase 1 BA · Flows are normative; rules cite `02-user-stories-and-business-rules.md`, errors cite `05-exception-handling-and-errors.md`.

## Journey 1 - Browse, preview, save a theme (creator)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Opens `/app/appearance` | Gallery renders from `GET /api/v1/themes` (thumbnail, name, pro badge; no hardcoded themes) | BR-02 |
| 2 | Creator | Clicks a permitted theme | `previewThemeId` set; page chrome re-skins via token CSS variables; `Unsaved` pill appears; nothing persisted | BR-01 |
| 3 | Creator | Clicks a pro theme without entitlement | Upgrade sheet opens with the static thumbnail; no `PUT` is sent | BR-03, BR-09 |
| 4 | SPA | `Save theme` -> `PUT /api/v1/profile/theme {themeId}` | `200` promotes preview to active, clears pill, sets purge chip | BR-09 |
| 5 | Profile service | Registry lookup -> plan gate -> row update -> audit -> publish event | Public page catches up <= 2 min | BR-03, BR-09 |
| 6 | SPA | `403 theme_forbidden` (forced call / plan changed) | Rolls back to the active theme + upgrade prompt | BR-03 |
| 7 | SPA | `404 theme_not_found` (registry changed) | Refreshes the gallery | BR-02 |

## Journey 2 - Upload an image avatar

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Picks a file (accept JPEG/PNG/WebP) | Client pre-checks <= 2 MB before a byte is sent | BR-04 |
| 2 | SPA | Multipart `POST /api/v1/profile/avatar` | Progress bar; button disabled while in flight | BR-12 |
| 3 | Profile service | Bounded read -> magic-byte check -> generate `profile/avatars/{ulid}.{ext}` -> file write -> row update -> delete previous file -> audit -> event | `200 {avatarKey}` swaps the avatar everywhere (card, preview, header) | BR-04, BR-05, BR-07 |
| 4 | SPA | `422 avatar_bad_type` / `avatar_too_large` | Inline error naming formats/cap; nothing changed | BR-04 |

Alternative: crash between file write and row update -> one orphan file; swept after 24 h; user sees no error.

## Journey 3 - Video avatar where the theme allows

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Active theme has `videoAvatar=true` | File input additionally accepts MP4; hint names the loop cap | BR-06 |
| 2 | Profile service | MP4 without `ftyp`, > 2 MB, or > 10 s | `422 avatar_video_not_allowed` / `avatar_too_large` / `avatar_video_too_long`; zero file writes | BR-04, BR-06 |
| 3 | Profile service | Valid loop | Same pipeline as images; stored under the same `avatars/` namespace | BR-05, BR-06 |
| 4 | System | Theme switched away from `videoAvatar` later | Public page falls back to the default glyph (or a static poster if provided); no stale video breaks the page | BR-06 |

## Journey 4 - Delete avatar

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | `Delete avatar` (confirm) | `DELETE /api/v1/profile/avatar` | BR-10 |
| 2 | Profile service | Clear row key -> audit -> event -> best-effort file delete | `204`; default glyph returns everywhere; missing file still `204` | BR-10 |

## Journey 5 - Publish and public render (visitor + renderer)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Public-site service | Cache miss for `/:username` | Resolves profile (gets `themeId`, `avatarKey` from the feature 06 payload) -> reads catalog via `GET /internal/v1/themes` | BR-02 |
| 2 | Public-site service | Unknown `themeId` (stale catalog) | Retries a fresh catalog fetch; still unknown -> default theme; page never breaks | BR-02 |
| 3 | Visitor | Loads the page | Token-derived CSS inlined; avatar served immutable by content key | BR-01, BR-08 |
| 4 | SPA | Purge chip polls public URL while pending | Chip clears on fresh content or after 2 min with `still publishing` | BR-09 |
