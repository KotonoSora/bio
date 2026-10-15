# Public Profile Frontend Plan (identity screens + API integration — UI build steps)

PLAN ref: P1a-2/P1a-3 (dashboard UX for claim, rename, visibility) + S-2/S-5. BA source: `docs/link-to-bio/profile/public-profile.md`. Server truth in `docs/link-to-bio/profile/public-profile-backend-plan.md` (endpoints, codes, DDL — never redefined here; this file owns screens, flows, and the integration sequence).

## Purpose

Give the frontend developer an ordered UI procedure for profile identity: routes first, then components, then the three integration flows (load/edit, rename wizard, visibility toggle) with exact request order, state transitions, and error rendering. Follow steps 1–7 in order. Framework-agnostic: maps to whatever SPA stack is chosen; the server is the validator, the client mirrors only maxlength hints.

## Users

- Frontend developer: builds `/app/profile` and the rename wizard against the backend plan's contracts.
- QA: executes the click-path drills in Acceptance criteria.
- Creator: claims a username, edits identity, renames with 301 safety, toggles visibility with no surprises.

## Functional requirements

1. Routes and shell: `/app/profile` (owner-only; unauthenticated → login redirect, never a half-rendered form). Layout: identity card (avatar slot owned by the themes-avatar plan, username row, displayName/bio/seo fields, visibility + resume toggles, save bar) plus `Change username` wizard entry. Shared shell provides `ProblemBanner` (renders RFC 9457 `code` + human text), `FieldError` (inline per-field), and `PendingChip` (purge-pending state). Tap targets ≥44px; all inputs labeled; no username regex in client code beyond maxlength.
2. Step 1 — load flow: on mount `GET /api/v1/profile` with `credentials:include` → populate form (skeleton while pending) → store `profile` + `etag`-less version stamp. `403` → re-login; network failure → retry button (no cached impersonation of another user's data — the store holds exactly one profile, the owner's).
3. Step 2 — edit/save flow: user edits displayName/bio/seo/toggles → client trims, checks maxlengths locally (instant hint, not a verdict) → `PUT /api/v1/profile` with `X-CSRF-TOKEN`, submit disabled while pending → `200` updates store + toast `Saved` + triggers publish-availability (purge chip, see step 5) → `422` maps `code` to the exact field (`display_name_too_long` under displayName, etc.) and focuses the first invalid field → `429` shows cooldown text from the problem detail.
4. Step 3 — rename wizard flow (the safety-critical path): screen 1 input → debounce 300ms → `POST username-check` per keystroke-pause, rendering normalized echo (`Alice!` → `alice!`) plus rule verdict (`available` green / `reason` red with the rule named); screen 2 confirm shows old → new, the 301 guarantee text, and permanent-reservation notice → `POST username-change` → `200` swaps store username, shows `Your old link now redirects (301)` + purge-pending chip → poll `GET /api/v1/profile` until username matches (max 2min, then show `still publishing — safe to leave`). `username_taken` on confirm (raced) returns the wizard to screen 1 with the name marked taken.
5. Step 4 — visibility toggle flow: switch flips optimistically with immediate `PUT {isPublic}` → `200` confirms and shows `Hidden — public page returns 404` (or `Live`) → failure rolls the switch back with the problem code toasted. The toggle copy states sitemap exclusion so private-by-accident is impossible to misunderstand.
6. Step 5 — publish/purge integration (UI side only; mechanics in the share-seo plan): any successful mutation sets `purgePending=true` and starts the HEAD poll of the public URL; chip clears when the live page reflects the change or after 2min with `still publishing` text. The frontend never purges cache itself — it only reflects backend purge state.
7. Step 6 — states and edge cases: empty (first-run claim variant of this screen when no profile exists — `PUT` becomes create per backend `profile_limit_reached` guard), loading skeletons, offline (submit queued? No — disabled with `You're offline` since mutations need fresh CSRF; drafts stay in-memory only), session expiry mid-wizard (re-login preserves wizard input in session storage, resumes at screen 1).

## Data / API touches

- Screen → endpoint map: load → `GET /api/v1/profile`; save → `PUT /api/v1/profile`; wizard → `POST /api/v1/profile/username-check` then `POST /api/v1/profile/username-change`; toggle → `PUT /api/v1/profile`. All with `credentials:include` + CSRF header; all responses parsed as problem JSON on non-2xx.
- Type mirror (docs only, kept in sync with backend DTOs): `Profile {id, username, displayName, bio, avatarKey, themeId, isPublic, showResume, seoTitle, seoDesc}`; `UsernameCheck {username, available, reason?}`. No client-side copies of reserved-slug list or regex — server verdicts only.
- Code → UI map: `username_too_short|too_long|bad_chars|reserved|taken|unchanged` → wizard field; `display_name_too_long|bio_too_long|seo_too_long` → respective field; `profile_limit_reached` → claim screen notice; `403` → re-login; `429` → cooldown text.

## Acceptance criteria

- Load drill: cold open renders skeleton then the owner's data; expired session redirects to login with wizard input preserved.
- Save drill: valid edit toasts `Saved` + purge chip; overlong bio focuses the bio field with the exact code-mapped message (no generic `error`).
- Wizard drill: typing `Alice!` shows normalized `alice!` + `username_bad_chars`; taken name blocks confirm; raced-taken on confirm returns to screen 1 marked taken; success shows 301 notice + chip clearing within 2min.
- Toggle drill: flip shows correct `Hidden/Live` copy; failed PUT rolls back with toast.
- Offline drill: airplane mode disables submit with `You're offline`; no phantom success.
- Security drill: no `user_id` in any request body; no username rules hardcoded in client bundle (grep).

## Out of scope

- Server DDL/logic (backend plan above); avatar upload UI (themes-avatar plans); block editor UI (blocks plans); badge/socials UI (verification plans); SSR templates and purge mechanics (share-seo plans); analytics dashboards (analytics plans).

## Open questions

- Q1: Claim variant — separate first-run screen or the same form in create mode?
- Q2: Wizard copy tone for permanent reservation — warning or plain notice?
