# Themes Avatar Backend Plan (registry + upload pipeline — server build steps)

PLAN ref: P1b-3 (token themes, server-side pro gates, no custom CSS) + P1b-4 (2MB + magic-byte + random key, video avatar) + S-2/S-4/S-5/S-7. BA source: `docs/link-to-bio/appearance/themes-avatar.md`. Phasing per `docs/platform/delivery/execution-overview.md` (B0). Storage in `docs/link-to-bio/appearance/themes-avatar-database-plan.md` (consumed columns + blob namespace; zero new tables). Screens in the themes-avatar frontend plan (splits next to this file).

## Purpose

Give the backend developer an ordered procedure for appearance: theme registry, plan-gated switching, the validated avatar pipeline, and tests. Follow steps 1–7 in order. No custom CSS, font-URL, or head fields may exist in UI, API, or schema — an ArchUnit test fails the build if they appear.

## Users

- Backend developer: executes steps 1–7 in package `com.app.bio.appearance`.
- QA: runs token-only, upload-rejection, gating, and abuse drills.
- Reviewer: rejects any user-CSS-shaped field or theme/avatar table.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — modular monolith. Monolith pros: theme switch is one `profiles.theme_id` update on the existing RLS/audit/purge path; avatar metadata rides the same transaction as the key record. Microservices cons avoided: a media service would split the publish transaction (row + blob + purge) for rare writes. Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; none fire. Starters: web, validation, data-jpa, security, actuator, micrometer.
2. Step 1 — `themes/ThemeTokens.kt` (`{bg, text, font, radius, linkStyle, animated, customLogo}`) + `themes/ThemeRegistry.kt` (hardcoded `Map<id, ThemeDef{id, name, tokens, thumbnail, proOnly}>`, validated at boot: no duplicate ids, every token renders in the SSR fragment test). `GET /api/v1/themes` serves this map. Adding a theme = code change + thumbnail asset + registry test update.
3. Step 2 — `service/ThemeService.kt#setTheme(userId, themeId)`: registry lookup (unknown → `404 theme_not_found`) → `PlanGate.canUseTheme(userId, theme)` against the entitlement ledger (pro-only on free → `403 theme_forbidden`, even on direct API calls — the UI hiding is not the gate) → update `profiles.theme_id` → audit → publish purge event.
4. Step 3 — `service/AvatarService.kt#upload(userId, bytes, claimedMime)`: enforce 2MB cap while reading (never buffer unbounded) → sniff magic bytes (JPEG `FF D8 FF`, PNG `89 50 4E 47`, WebP `RIFF....WEBP`, MP4 `ftyp` only when the profile's theme allows video, else `422 avatar_video_not_allowed`) → reject SVG/HTML/scripts by content (`422 avatar_bad_type`; extension and claimed MIME are never trusted) → generate `avatars/{ulid}.{ext}` → `BlobPort.put` → update `profiles.avatar_key` → delete previous key → audit → purge event. `delete(userId)` clears the key + deletes the object (idempotent: missing object is still 204).
5. Step 4 — `api/AppearanceController.kt`: `PUT /api/v1/profile/theme`, `POST /api/v1/profile/avatar` (multipart, validated before any blob write — a rejected upload leaves zero objects), `DELETE /api/v1/profile/avatar`. Session `userId` only. `@ControllerAdvice` → problem JSON with exact codes.
6. Step 5 — abuse and edge wiring: Turnstile + WAF/rate on upload/theme endpoints (S-5); 5MB/renamed-script/SVG probes rejected with nothing stored; rapid-upload bursts throttled per user; secrets via env only.
7. Step 6 — rendering contract (for the share-seo plans): public page inlines only token-derived CSS; avatar served immutable by key. Preview-before-publish: changes stage in dashboard, go public on publish + purge (<2min SLA).
8. Step 7 — tests + container: registry test (ids unique, tokens render); upload matrix (valid PNG/JPEG/WebP/allowed-loop pass; renamed-script/SVG/HTML/5MB/video-where-forbidden fail with exact codes + zero blob writes verified on the blob mock); gating test (free direct PUT of pro theme → 403); ArchUnit (no `customCss|fontUrl|headHtml` identifiers; no new tables under `bio` from this module); same image via `docker run` + `podman run` per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`.

## Data / API touches

- Schema: none new — database plan above. Endpoints: `GET /api/v1/themes` → `200 [{id, name, tokens, thumbnail, proOnly}]`; `PUT /api/v1/profile/theme {themeId}` → `200` / `404 theme_not_found` / `403 theme_forbidden`; `POST avatar` (multipart) → `200 {avatarKey}` / `422 avatar_too_large|avatar_bad_type|avatar_video_not_allowed`; `DELETE avatar` → `204`.

## Acceptance criteria

- Token-only drill: zero user-CSS fields in UI, API, schema (grep + ArchUnit).
- Upload drill: each hostile file rejected with exact code + zero blob writes; keys non-sequential, unguessable.
- Gating drill: free direct pro-theme PUT → 403; post-upgrade succeeds.
- Publish drill: theme/avatar change public within purge SLA.
- Abuse drill: upload bursts throttled (S-5); secrets grep clean.
- Container drill: digest pair boots, compose green, rollback green, contract diff clean.

## Out of scope

- Storage namespaces (database plan above); gallery/uploader screens (frontend plan); block model; resume print; SSR mechanics (share-seo plans); analytics; shop/campaign/agency theming; native apps; AI assist (gated plan).

## Open questions

- Q1: v1 theme count and one video-avatar-capable theme at launch?
- Q2: Avatar crop — client cropper with server re-validation, or upload-as-is with CSS crop?
