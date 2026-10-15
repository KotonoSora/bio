# Deep Links Mobile Database Plan (renderer-only — no stored state)

PLAN ref: P1c-1/P1c-2 (behavior + markup contract on the existing `/:username` page, no new tables). BA source: `docs/link-to-bio/public-page/deep-links-mobile.md`. Logic in the deep-links backend plan; dashboard readouts in the frontend plan (both split next to this file). No migration — this slice stores nothing.

## Purpose

Record the deliberate absence of storage: what the deep-link/mobile slice derives at render time, what it reuses from other slices' tables, and what it is forbidden from persisting (per-visitor variants would fragment the cache). A table appearing for this slice is a design violation.

## Users

- Backend developer: derives everything at render; persists nothing.
- QA: asserts cache-key invariance across webviews.
- Reviewer: rejects per-visitor SSR variants and new endpoints for measurement.

## Functional requirements

1. Stored state: none. Banner eligibility is derived per request from the User-Agent (advisory boolean class, applied client-side); outbound deep-link forms are server-side rewrites of allowlisted `https:` block URLs at render time; tap-target and budget compliance are build-time checks, not rows.
2. Reused state (owned elsewhere, read-only here): block destination URLs (`bio.profile_blocks.payload_json`, validated `https:` at authoring per the blocks backend plan); nothing else. No new indexes, no new queries — the banner and attributes ride the existing SSR reads from the share-seo database plan.
3. Measurement state: banner shows and escape taps ride the existing beacon event types (`banner_show`, `escape_tap` in the beacon payload) — counted by the analytics pipeline, stored as aggregates there. No tables, no new endpoints, zero per-hit PG writes attributable to this slice.
4. Forbidden: per-visitor SSR variants (would multiply cache keys and kill the >95% hit gate); persisting UA strings or webview classes (privacy + cache); accepting new URL schemes from user input (only server-side rewrites of valid `https:` may emit deep-link forms).
5. Cache invariance: banner markup and link attributes are inside the single cached SSR payload — the cache key gains no UA dimension. Detection output is one boolean class toggled by the micro-script, not separate cached pages.

## Data / API touches

- DDL: none — assertion: `grep -rn "CREATE TABLE" docs/link-to-bio/public-page/` returns nothing. Reads reused: block URLs from the share-seo read path. Events reused: beacon `POST /api/t/:username` with `banner_show|escape_tap` types (analytics pipeline owns storage).

## Acceptance criteria

- Absence drill: schema diff before/after this slice shows zero tables, zero columns.
- Invariance drill: same slug from Safari/IG-webview/Zalo-webview returns byte-identical SSR HTML (boolean class applied client-side only).
- No-scheme drill: only server rewrites of valid `https:` emit deep-link forms; stored-payload schemes unchanged.
- Measurement drill: banner interactions arrive as beacon events with zero extra PG writes.
- Violation drill: any per-visitor cache key or new measurement endpoint fails review.

## Out of scope

- Anchor/banner implementation (backend plan); dashboard mobile tab and QA matrix execution (frontend plan); URL allowlist (blocks backend plan); beacon pipeline (analytics plans); native apps (never — PWA instead).

## Open questions

- Q1: Byte-identical HTML across webviews — hold as permanent rule, or allowlisted divergence later?
- Q2: Escape-tap attribution window — same-session only, or keep current beacon dedupe?
