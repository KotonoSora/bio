# Themes and Avatar - UI Data Dictionary (feature 09)

Phase 1 BA · React 19.3 components for `/app/appearance` (owner-only). No styling-authoring fields may exist here (BR-01). Topology: `docs/02-architecture-techlead/09-themes-and-avatar/01-architecture-and-tech-stack.md`.

## Screen: Appearance `/app/appearance`

| Field Name | Component Type | Mandatory? | Constraints & Validations | Default/Behavior |
|---|---|---|---|---|
| ThemeGallery | grid | - | renders from `GET /api/v1/themes`; zero hardcoded themes | loading skeleton |
| ThemeCard | card | - | thumbnail, name, `ProBadge` when `proOnly`, selected outline for active theme | - |
| ProBadge | badge | - | visible on `proOnly` themes; click routes to `UpgradeSheet` when unentitled | - |
| UpgradeSheet | modal | - | static thumbnail only (no live recolor of non-savable pro themes) | hidden |
| UnsavedThemePill | pill | - | `Unsaved theme` while `previewThemeId != activeThemeId`; clears on save or revert | hidden |
| TokenPreviewChrome | CSS-variable container | - | applies preview tokens to dashboard chrome only; no free-form color inputs exist | active tokens |
| SaveThemeButton | primary button | - | disabled while saving; rolls back on `403`/`404` with prompt/refresh | `Save theme` |
| RevertButton | secondary button | - | resets `previewThemeId` to active; clears pill | hidden when no preview |
| PurgeChip | shared status chip | - | reused from feature 06 (`publishing...` <= 2 min); not reimplemented here | hidden |
| AvatarCard | card | - | shows `AvatarImage` when key present, else `DefaultGlyph` | - |
| AvatarImage | image | - | immutable media URL by name; `object-fit: cover`; alt = display name | glyph |
| DefaultGlyph | placeholder | - | platform asset; restored on delete | - |
| UploadButton + FileInput | button + file input | - | `accept` JPEG/PNG/WebP (+ `video/mp4` only when active theme `videoAvatar=true`); <= 2 MB pre-check with exact cap text | disabled while pending |
| SizeHintText | static text | - | `Images up to 2 MB (PNG, JPEG, WebP)`; video line adds `loops up to 10 s` when allowed | - |
| UploadProgressBar | progress | - | advances during multipart upload; never fake completion before `200` | hidden |
| UploaderInlineError | inline text | - | maps `avatar_too_large` / `avatar_bad_type` / `avatar_video_not_allowed` / `avatar_video_too_long` | hidden |
| DeleteAvatarButton | danger button | - | confirm dialog; idempotent call; glyph returns on `204` | hidden when no avatar |
| VideoUnsupportedHint | inline hint | - | shown when dropping MP4 while the active theme disallows video | hidden |
| PreviewIframe | iframe | - | feature 10 preview mode (`private, no-store`); renders draft tokens + draft avatar; never cached | hidden until opened |
| SessionExpiryState | redirect | - | re-login keeps selected file NAME; bytes require re-pick (browser security) | - |

## Honesty rules (QA-asserted)

- Never display `saved` before the `200` lands; never recolor a pro theme a free user cannot save beyond the upgrade sheet's static thumbnail.
- The screen contains no color picker, CSS textarea, font-URL input, or head-HTML field - the front-end grep gate fails the build if one appears.
