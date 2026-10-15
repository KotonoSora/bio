# Share SEO Database Plan (public read patterns — no new tables)

PLAN ref: P1c-1/P1c-2 (SSR reads must be indexed, zero writes per hit) + S-6. BA source: `docs/link-to-bio/public-page/share-seo.md`. Tables owned by `docs/link-to-bio/profile/public-profile-database-plan.md` (profiles, redirects) and `docs/link-to-bio/blocks/link-blocks-database-plan.md` (blocks). Read logic in the share-seo backend plan (splits next to this file). No migration — this slice creates zero tables.

## Purpose

Document the exact read path the public page takes through other slices' tables: which queries, which indexes serve them, what the SSR handler is forbidden from doing, and why the path holds at viral load. No `CREATE TABLE` here by design — a table for rendering state would mean per-hit writes, which this architecture forbids.

## Users

- Backend developer: implements SSR reads using only the patterns below (all index-served).
- QA: asserts zero-write behavior and index usage under load.
- Reviewer: rejects any PR adding writes to the render path.

## Functional requirements

1. Read 1 — profile resolution: `SELECT id, username, display_name, bio, avatar_key, theme_id, is_public, show_resume, seo_title, seo_desc, verification_status, follower_total_cached FROM bio.profiles WHERE username = :u AND is_public = TRUE`. Served by the `username` unique index (exact match, single row). Unknown or private slug → zero rows → 404 (never distinguish "missing" from "private" — no existence oracle).
2. Read 2 — rename resolution (only when read 1 misses): `SELECT new_username FROM bio.username_redirects WHERE old_username = :u`. Served by the `old_username` PK. Hit → 301 (cacheable redirect); miss → 404.
3. Read 3 — blocks: `SELECT id, type, position, payload_json, visible_from, visible_until FROM bio.profile_blocks WHERE profile_id = :pid AND is_visible = TRUE ORDER BY position ASC, id ASC`, with the schedule window (`visible_from/visible_until`) applied as query predicates. Served by `idx_blocks_profile_pos` (one ordered range scan, no sort step). The window lives in the query, not RLS (RLS policy `b_public` covers `is_visible`; time predicates are explicit).
4. Forbidden on this path: any `INSERT/UPDATE/DELETE` (counting goes through the beacon pipeline in the analytics plans — the SSR handler holds no write transaction); `SELECT *` (columns are enumerated so a future wide column cannot leak into HTML or bloat cache); cross-profile reads (every query is slug-bound; no `user_id` parameter exists on this path at all).
5. Query budget: 5s statement timeout on render queries; 3 round-trips max per render (profile → [redirect?] → blocks), issued sequentially because each depends on the last (no N+1: blocks come in one scan, never per-block queries). Snapshot text for `/:username/cv` arrives via the resume module's read interface (one call, owned there).
6. Cache relationship: query results feed the SSR cache entry keyed `ssr:{host}:{slug}:{themeRev}:{snapshotRev}` (mechanics in the backend plan). Stale cache means stale reads, never extra queries — thundering-herd protection is the single-flight lock, not more indexes.
7. Sitemap reads: `SELECT username, updated_at FROM bio.profiles WHERE is_public = TRUE ORDER BY username` paginated (uses `idx_profiles_user`? No — needs its own path: acceptable sequential scan on a narrow projection, cached 1h; at 10M rows this becomes the one query revisited at P5 pre-render — flagged, not solved here).

## Data / API touches

- DDL: none — assertion instead: `grep -rn "CREATE TABLE" docs/link-to-bio/public-page/` returns nothing. Indexes consumed: username unique, `old_username` PK, `idx_blocks_profile_pos`, `idx_profiles_user`. Verify with `EXPLAIN`: read 1 → Index Scan using unique; read 3 → Index Scan, no Sort node.
- Write prohibition is structural: the SSR datasource user is granted `SELECT` on these tables only in production topology (writes would fail closed even if coded).

## Acceptance criteria

- Index drill: `EXPLAIN` on reads 1–3 shows index scans; read 3 shows no Sort node.
- Zero-write drill: sustained SSR load → zero data-modifying statements in query logs (verified, not just code-reviewed).
- Oracle drill: private vs missing slugs indistinguishable (same status, same timing class).
- Sitemap drill: only `is_public` slugs emitted; paginated; cached.
- Violation drill: any render-path write fails closed (grant) and fails review (rule 4).

## Out of scope

- Table definitions (profile + blocks database plans); render/tag/cache logic (backend plan); preview/publish UI (frontend plan); counting and aggregates (analytics plans); resume snapshot semantics (resume major).

## Open questions

- Q1: Sitemap sequential scan at 10M rows — acceptable hourly cost, or keyset cursor now?
- Q2: Read-only SSR role in v1, or single app role until P5 replica split?
