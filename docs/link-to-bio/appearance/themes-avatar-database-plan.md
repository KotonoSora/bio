# Themes Avatar Database Plan (consumed columns + blob keys — no new tables)

PLAN ref: P1b-3/P1b-4 (token themes, avatar pipeline) + S-4. BA source: `docs/link-to-bio/appearance/themes-avatar.md`. Consumed by the themes-avatar backend plan (registry + upload pipeline; splits next to this file). Profile columns live in `docs/link-to-bio/profile/public-profile-database-plan.md` (single `CREATE TABLE`, never altered here). No migration — this slice creates zero tables.

## Purpose

Document the storage this slice touches so developers know exactly where theme choice and avatar bytes live: which profile columns, which blob key namespace, what is code instead of rows, and which cache headers apply. No `CREATE TABLE` here by design — that fact itself is the contract (a table appearing for themes would be a layering violation).

## Users

- Backend developer: reads columns, writes blob keys per the namespace below.
- QA: verifies key randomness, cache headers, and the no-new-table rule.
- Reviewer: rejects any PR adding theme/avatar tables (code + columns cover it).

## Functional requirements

1. Consumed columns (defined once in the profile database plan): `profiles.theme_id` (FK-by-convention to `ThemeRegistry` ids in code — deliberately not a DB FK, since themes ship as code and a FK would couple deploys to data); `profiles.avatar_key` (nullable blob key, NULL = default glyph). Both update through the backend plan's endpoints only.
2. Theme definitions live in code (`com.app.bio.appearance.themes.ThemeRegistry`: `{id, name, tokens{bg,text,font,radius,linkStyle}, thumbnail, proOnly}`), never as rows — adding a theme is a code change + review, queryable by no SQL. Rationale: token sets are deploy-versioned with the renderer that interprets them; rows would allow theme/render skew.
3. Avatar blob namespace: keys `avatars/{ulid}.{ext}` (server-generated, non-sequential, unguessable); content types JPEG/PNG/WebP + short looping MP4 only where the theme allows; SVG/HTML/scripts never stored. Objects are immutable (new upload = new key; old key deleted after the profile row update commits). Serving: blob/CDN with immutable cache headers keyed by content key; direct key guessing does not enumerate users (ulid entropy + no listing endpoint).
4. Verification evidence blobs (trust slice) use a separate namespace `verification/{ulid}.*` — named here so the two namespaces are never confused; lifecycle owned by the verification plans.
5. Read patterns: public SSR resolves avatar in the same profile-row read as everything else (no extra query); dashboard gallery reads `ThemeRegistry` from memory (zero PG). Write pattern: blob PUT first, then profile-row update, then old-key delete (crash between PUT and row update orphans one object — swept by the orphan-sweep job below, never by blocking the request).
6. Orphan sweep: scheduled job lists blob objects under `avatars/` older than 24h with no referencing `avatar_key` and deletes them (dry-run metric first). This is storage hygiene, not a data pipeline — no PG table backs it.

## Data / API touches

- DDL: none — assertion instead: `grep -rn "CREATE TABLE" docs/link-to-bio/appearance/` returns nothing (gate in review). Columns consumed: `profiles.theme_id`, `profiles.avatar_key` (profile database plan). Blob namespaces: `avatars/{ulid}.{ext}` (immutable, CDN-cached); verification namespace explicitly out of this slice.
- Cache headers: avatar objects `Cache-Control: public, max-age=31536000, immutable`; theme thumbnails likewise (fingerprinted by theme version).

## Acceptance criteria

- No-table drill: schema diff before/after this slice shows zero new tables.
- Key drill: 100 uploads → 100 unique non-sequential keys; guessing a neighboring key returns nothing enumerable.
- Orphan drill: interrupted upload leaves one unreferenced object; sweep deletes it after 24h; referenced keys untouched.
- Cache drill: avatar URL responses carry immutable headers; theme switch needs no avatar re-fetch (key unchanged).
- Violation drill: any migration adding a theme/avatar table fails review by rule 1.

## Out of scope

- Registry code and upload validation (backend plan); gallery/uploader screens (frontend plan); profile DDL (profile database plan); badge/evidence lifecycle (verification plans); SSR inlining of tokens (share-seo plans).

## Open questions

- Q1: Orphan sweep 24h — shorter with CDN propagation risk, or longer with storage cost?
- Q2: Video-avatar objects under the same namespace with duration caps in key metadata, or a separate prefix?
