# Themes and Avatar - API Contracts and Payloads (feature 09)

Phase 2 TL · Public catalog, dashboard, and internal REST contracts. Auth and rate rules: `05-security-logging-and-rate-limiting.md`. JSON only; problem bodies follow RFC 9457 (model of `docs/02-architecture-techlead/06-public-profile/03-api-contracts-and-payloads.md`).

## Endpoint map

| Method & path | Auth | Request | Success | Errors (code) |
|---|---|---|---|---|
| `GET /api/v1/themes` | none (public list) | - | 200 list of `ThemeResponse`; `Cache-Control: public, max-age=300` | - |
| `PUT /api/v1/profile/theme` | session + CSRF | `SetThemeRequest` | 200 `{themeId}` | 404 `theme_not_found`; 403 `theme_forbidden`; 429 |
| `POST /api/v1/profile/avatar` | session + CSRF + Turnstile | multipart `file` | 200 `AvatarUploadResponse` | 422 `avatar_too_large`, `avatar_bad_type`, `avatar_video_not_allowed`, `avatar_video_too_long`; 429 |
| `DELETE /api/v1/profile/avatar` | session + CSRF | - | 204 (idempotent) | 401 |
| `GET /internal/v1/themes` | service JWT | - | 200 `ThemeCatalogResponse` | 401 |

## Kotlin DTOs (data classes; source of truth for the SPA type mirror)

```kotlin
data class ThemeTokens(
    val bg: String, val text: String, val font: String, val radius: String,
    val linkStyle: String, val animated: Boolean, val customLogo: Boolean,
)
data class ThemeResponse(
    val id: String, val name: String, val tokens: ThemeTokens,
    val thumbnail: String, val proOnly: Boolean, val videoAvatar: Boolean,
)
data class SetThemeRequest(@field:NotBlank val themeId: String)
data class AvatarUploadResponse(val avatarKey: String)
data class ThemeCatalogResponse(val registryVersion: String, val themes: List<ThemeResponse>)
```

## Semantics (normative)

- **Theme save.** Registry lookup (`404 theme_not_found`) -> entitlement gate (`403 theme_forbidden` for `proOnly` without an explicit pro snapshot; fail closed) -> write `profiles.theme_id` -> audit -> publish `profile.published.v1`.
- **Upload validation order (before any blob write).** Bounded read with 2 MB + 1 byte ceiling (over -> `avatar_too_large`) -> magic-byte sniff (JPEG `FF D8 FF`, PNG `89 50 4E 47`, WebP `RIFF....WEBP`, MP4 `ftyp`; else `avatar_bad_type`) -> if MP4: active theme must have `videoAvatar` (else `avatar_video_not_allowed`) and loop <= 10 s (else `avatar_video_too_long`) -> generate `avatars/{ulid}.{ext}` -> blob PUT -> row update -> delete previous key -> audit -> event. Extension and claimed MIME are never trusted; a rejected request leaves zero objects.
- **Delete.** Clear `avatar_key` unconditionally -> audit -> event -> best-effort blob delete; `204` even when the object is already missing.
- **Renderer contract.** `GET /internal/v1/themes` returns the whole catalog with `registryVersion`; consumers cache <= 300 s; an unknown `themeId` triggers one fresh fetch and then falls back to the default theme (page never breaks). `themeId`/`avatarKey` already ride the feature 06 resolve payload - no change there. Downgrade fallback (36): a page whose stored `theme_id` is `proOnly` renders the default theme while the owner's snapshot is not pro (fail-closed per render, cached snapshot); the stored `theme_id` is never rewritten - re-upgrade restores it byte-identical.
- **Caching.** Avatar + thumbnail objects immutable, 1-year cache; catalog 300 s; theme switch never changes the avatar key (no re-fetch).

## Code catalog (in addition to previous feature catalogs)

| `code` | HTTP | Meaning | UI mapping |
|---|---|---|---|
| `theme_not_found` | 404 | registry changed since gallery load | refresh gallery |
| `theme_forbidden` | 403 | `proOnly` without entitlement (or gate unavailable) | upgrade sheet + rollback |
| `avatar_too_large` | 422 | over 2 MB | inline cap text |
| `avatar_bad_type` | 422 | sniffed content not JPEG/PNG/WebP/MP4 | formats text |
| `avatar_video_not_allowed` | 422 | MP4 on a non-`videoAvatar` theme | video hint |
| `avatar_video_too_long` | 422 | loop over 10 s | loop cap text |

## Events and governance

Theme/avatar mutations publish `profile.published.v1` (`username, updatedAt`) after commit - no new event; consumers: feature 10 (page purge + preview invalidation). `avatar_video_too_long` is the one code added beyond the legacy catalog (it covered the duration gap left open there). OpenAPI gate: additive-only; every code contract-tested in T-09.
