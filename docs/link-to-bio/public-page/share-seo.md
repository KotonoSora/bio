# Share Page + SEO (public SSR + cache)

PLAN ref: P1c-1, P1c-2. Analytics pipeline lives in `analytics/profile-analytics.md`.

## Purpose

Define the public, crawler-visible Link-to-Bio page: server-rendered HTML per username with complete SEO tags, served cache-first at edge scale. A cold crawler with no JavaScript must see the same content and tags as a browser, while dashboard editing stays a separate uncached SPA.

## Users

- Visitor / search crawler / link-preview fetcher: cold-loads `/:username` with no JS.
- Creator: publishes edits and expects them live within the purge SLA.

## Functional requirements

1. Worker-rendered routes: `/:username` (bio page), `/:username/cv` (resume view backed by `docs/resume-builder/` snapshot). Both emit per-profile `<title>`, meta description, canonical, OG/Twitter tags, and JSON-LD `Person`. Tags are server-rendered, never client-fetched.
2. Route isolation: public SSR code stays separate from the `/app/*` SPA bundle. Static assets serve first; Worker `fetch` handles `/api/*` + `/:username` variants with `run_worker_first` literal/wildcard rules (no `/:param` syntax inside that config).
3. Cache-first: `Cache-Control: public, s-maxage=60-300, stale-while-revalidate=600`; target >95% cache-hit. Publish/snapshot change purges that slug; private profiles never cached publicly.
4. Miss-storm guard: single-flight render per slug via KV lock (`RATE_LIMIT` namespace) so a viral link triggers one render, not N.
5. Crawler surfaces from Worker: `robots.txt` and `sitemap.xml` containing only `is_public=1` profiles; private/hidden profiles excluded.
6. QR codes encode the same `/:username` URL (no separate short-link system in v1).

## Data / API touches

- Reads: public profile row + visible in-window blocks + active theme tokens + avatar URL (see sibling docs). No dashboard session required; scope is strictly `username + is_public=1`.
- Writes on this path: none to PG. View/click counting is a separate beacon pipeline (see `analytics/profile-analytics.md`) — the SSR handler never writes to the database per hit.
- Cache keys: per slug (+ theme/snapshot revision). Purge call issued by the publish and snapshot-save endpoints. KV lock key `render:<slug>` with short TTL; Hyperdrive statement timeout 5 s on the render query path.

## Acceptance criteria

- `curl` (no JS) on `/:username` and `/:username/cv`: returns 200 with title/meta/canonical/OG/Twitter/JSON-LD and visible block text; SPA fallback `index.html` without tags is a failure.
- `/assets/*` still serves static files directly; SPA fallback only under `/app/*`.
- Publish → public change visible in <2 min; cache-hit ratio >95% at the P1 k6 gate (10k concurrent public reads, p95 <500 ms).
- Stampede test (many concurrent cold misses on one slug): single render executed, rest coalesced via KV lock.
- `sitemap.xml` contains only `is_public=1` slugs; `robots.txt` served from Worker.
- Username-change 301 (defined in `profile/public-profile.md`) preserved through the cache layer.

## Out of scope

- Block editing and scheduling semantics (see `blocks/link-blocks.md`); theme/avatar authoring (see `appearance/themes-avatar.md`); analytics and realtime windows (see `analytics/profile-analytics.md`).
- Resume snapshot semantics and print (`docs/resume-builder/`); shop `/:username/shop`, custom domains, leads (separate majors); P4 publishing/automations; P5 replicas/ISR pre-render of top profiles.

## Open questions

- Q1: Canonical host for SEO — apex `/:username` only, or also indexed `/:username/cv`?
- Q2: Default `s-maxage` within 60–300 s for launch — start high (300 s) or low (60 s) while content churn is high?
