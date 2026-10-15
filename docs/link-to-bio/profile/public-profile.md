# Public Profile (identity + visibility)

PLAN ref: P1a-2, P1a-3. Auth mechanics (signup/login/session) are a dependency, not specified here.

## Purpose

Define the public identity of a Link-to-Bio page: one profile per user in v1, addressed by a unique lowercase username, with explicit public/private visibility. Username rules and change behavior protect share links (QR, bio links, printed CVs) from breaking or being hijacked by reserved-word collisions.

## Users

- Creator: claims a username, sets display name/bio/avatar, toggles public/private.
- Visitor: opens `/:username` without login; sees the page only when `is_public=1`.
- Returning visitor / QR scanner: follows an old username after a rename and lands on the new URL via 301.

## Functional requirements

1. Username claim at signup/profile-setup: lowercase enforced, uniqueness enforced, reserved slugs rejected (`app, api, admin, static, r, cv, shop, login, signup, pricing, blog`). Reject with a specific error naming the rule violated.
2. Profile fields: `display_name, bio, avatar_r2_key, theme_id, is_public, show_resume, seo_title, seo_desc`. Free plan = one profile, unlimited links, platform badge shown.
3. Visibility toggle: `is_public=0` hides `/:username` and excludes the profile from `sitemap.xml` and search surfaces. Private pages return 404 publicly, remain editable in dashboard.
4. Username change: allowed, records old → new mapping, old URL responds 301 to new permanently. Old username stays reserved (never re-issuable) to block impersonation.
5. Delete flow (30-day per P1a-1): profile deactivates immediately, data purged after grace period; documented in account settings, not detailed here.

## Data / API touches

- Table `profiles{user_id, username UNIQUE lowercase, display_name, bio, avatar_r2_key, theme_id, is_public, show_resume, seo_title, seo_desc}` plus standard `user_id TEXT NOT NULL` + index + RLS policy. DB-level check constraint rejects reserved slugs and non-lowercase values (defense in depth behind app validation).
- Username-change table `username_redirects{old_username UNIQUE, new_username, created_at ISO8601}` for 301 resolution.
- Endpoints (JSON over `/api/v1/*`, cookie session): `GET/PUT /api/v1/profile`, `POST /api/v1/profile/username-check`, `POST /api/v1/profile/username-change`. All dashboard calls scope `WHERE user_id = session.userId`; public reads scope `username + is_public=1`.
- `show_resume` toggles the `resume`-block section on the bio page only; resume content itself is specified in `docs/resume-builder/`.

## Acceptance criteria

- Uppercase, duplicate, or reserved username: rejected at API with 422 and at DB with constraint violation (both verified in tests).
- `is_public=0` profile: public `GET /:username` returns 404, absent from `sitemap.xml`; dashboard still renders it.
- Rename `alice` → `alice-dev`: `GET /alice` returns 301 to `/alice-dev`; claiming `alice` afterward is rejected.
- IDOR probe: user A `GET/PUT /api/v1/profile` with user B identifiers returns 403; direct PG as app role denied by RLS.
- XSS probe: script/iframe/event-handler in `bio`/`display_name` stripped; CSP holds on dashboard and public page.
- Sanitization: stored links require `https:` scheme; rendered anchors carry `rel="noopener ugc"`.

## Out of scope

- Block list management (see `blocks/link-blocks.md`).
- Themes and avatar pipeline detail (see `appearance/themes-avatar.md`).
- Public SSR tags and caching (see `public-page/share-seo.md`); view/click analytics (see `analytics/profile-analytics.md`).
- Resume document model (see `docs/resume-builder/`); shop/products, courses, tips, leads/CSV, custom domains (separate majors); auth session/password-reset internals; AI, publishing, agency (P3–P4); replicas/ISR (P5).

## Open questions

- Q1: Single profile per user in v1 confirmed, or allow vanity second profiles on pro?
- Q2: Grace period on username release — permanent reservation, or release after N months?
