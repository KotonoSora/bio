# Themes and Avatar - Context and Scope (feature 09)

Phase 1 BA · Feature 09 of 53 (link-to-bio) · Implemented by `docs/02-architecture-techlead/09-themes-and-avatar/` (5 files). Extends the authoring domain of `profile-service`; writes `theme_id` / `avatar_key` on the profile row (feature 06 V1 columns); consumers: feature 10 (SSR token/avatar rendering), feature 12 (theme analytics).

## Business objectives

| # | Objective | Measure |
|---|---|---|
| BO-1 | Visual identity without user code: themes are platform-owned token sets; no custom CSS, font-URL, or head field exists anywhere. | Grep + ArchUnit find zero user-styling fields in UI/API/schema (AC-01). |
| BO-2 | Abuse-proof uploads: hostile files are rejected before any file write; stored names are unguessable. | Upload matrix rejects renamed scripts, SVG/HTML, oversize files with zero file writes (AC-02, AC-10). |
| BO-3 | Server-side plan gate: pro themes are gated by the server, not by hidden UI. | Free plan + direct pro-theme call -> `403 theme_forbidden` (AC-03). |
| BO-4 | Fast, consistent render: avatars serve immutable by content key; token CSS is inlined by SSR. | Immutable headers verified; theme switch causes no avatar re-fetch (AC-07). |
| BO-5 | Honest preview and publish: preview is clearly unsaved; public catches up within the purge SLA. | `Unsaved` pill until save; theme/avatar change public <= 2 min (AC-08). |

## In scope

- Theme gallery (code-shipped registry), instant token preview, plan-gated `theme_id` switching.
- Avatar pipeline: image upload (JPEG/PNG/WebP, 2 MB cap, magic-byte verification) and short looping video where the theme allows; delete restores the default glyph.
- File namespace discipline (`profile/avatars/{ulid}.{ext}`), immutable cache headers, orphan-sweep hygiene.
- Preview/publish integration through the shared purge chip (feature 06 pattern) and the feature 10 preview mode.

## Out of scope

- Block content model (feature 08); profile identity and publish flow (feature 06); SSR fragment internals, preview mode, cache purge (feature 10); verification evidence storage (feature 07 namespace).
- Resume templates/print; shop, email campaign, and agency white-label theming (their majors); native app (PWA instead); AI design assist (feature 16, gated).

## Personas and RBAC matrix

| Role | CRUD Permissions | Description |
|---|---|---|
| Creator | Read themes; update own `theme_id`; create/delete own avatar | Picks a theme, previews instantly, uploads/removes an avatar. |
| Visitor | Read rendered page only | Sees the chosen tokens and avatar; no authoring surface exists. |
| Public-site service | Internal read: theme catalog + avatar file by name | Renders tokens; resolves unknown theme ids to the default theme. |

## Open questions (non-blocking; v1 defaults)

| # | Question | v1 default | Revisit trigger |
|---|---|---|---|
| Q-01 | Theme count and video-capable theme at launch | 8 themes, exactly 1 with `videoAvatar=true` | gallery requests more; video storage grows unexpectedly |
| Q-02 | Avatar crop | upload-as-is + CSS crop (object-fit); no client cropper | creator complaints about wrong framing exceed support budget |
| Q-03 | Video duration enforcement | <= 10 s loop cap checked at upload; same `profile/avatars/` namespace | storage cost or metadata needs favor a separate prefix |
| Q-04 | Orphan sweep window | 24 h | storage cost vs edge-cache propagation proves 24 h wrong |

Related specs: profile row columns `docs/02-architecture-techlead/06-public-profile/02-database-schema-design.md`; publish/purge semantics `docs/01-requirements-ba/06-public-profile/02-user-stories-and-business-rules.md` (BR-09); SSR consumption `docs/01-requirements-ba/10-share-and-seo/`.
