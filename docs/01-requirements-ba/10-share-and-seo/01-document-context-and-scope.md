# Share and SEO - Context and Scope (feature 10)

Phase 1 BA · Feature 10 of 53 (link-to-bio) · Implemented by `docs/02-architecture-techlead/10-share-and-seo/` (6 files). First feature of the `public-site-service`; consumers: feature 11 (deep-link banner + PWA on the same origin), analytics (features 12-15, beacon only). Folds the link.me parity inventory (research) into this register.

## Business objectives

| # | Objective | Measure |
|---|---|---|
| BO-1 | Crawler parity: a no-JS cold load of `/:username` and `/:username/cv` returns the same content and complete tags as a browser; the SPA fallback without tags is a named failure. | Curl-parity drill green on both routes (AC-01). |
| BO-2 | Cache-first at edge scale: public reads scale by caching, not by origin compute. | >95% cache hit and p95 <500 ms at 10k concurrent reads (AC-03). |
| BO-3 | Honest freshness: a publish is publicly visible within the purge SLA, even if a purge event is lost. | Change live <= 2 min with the event dropped (AC-11). |
| BO-4 | Privacy parity and zero writes: private, missing, and retired slugs are indistinguishable; the render path never writes. | Same status and timing class; zero data-modifying statements (AC-07). |
| BO-5 | Share surfaces: QR and copy-link encode only the canonical profile URL; the sitemap lists public profiles only. | AC-10, AC-05. |

## In scope

- SSR routes `/:username` (bio) and `/:username/cv` (resume view) with title/meta/canonical/OG/Twitter/JSON-LD, XSS-escaped, server-rendered.
- HTTP cache policy (`s-maxage` + `stale-while-revalidate`), service-side page cache, purge listener, single-flight render coalescing.
- `robots.txt` and `sitemap.xml` (public profiles only), rename 301s preserved through the cache layer.
- Dashboard-side preview mode (`private, no-store`), the shared purge chip, and the read-only SEO checklist; QR/share utilities encoding the canonical URL.

## Out of scope

- Authoring: profile (06), badge/followers (07), blocks (08), themes/avatar (09). Deep-link escape banner and PWA delivery (feature 11). Analytics beacons and aggregates (features 12-15).
- Resume snapshot semantics and print (features 17-21); shop/leads/domains (their majors); P4 publishing/automations; P5 ISR pre-render (trigger recorded in TL 01).

## Personas and RBAC matrix

| Role | CRUD Permissions | Description |
|---|---|---|
| Visitor / crawler / link-preview fetcher | Read public routes only | No session; identical HTML with or without JS; never sees drafts. |
| Creator | Read own preview + head model; publish via owning editors | Sees drafts only through the sandboxed preview; never purges cache directly. |
| Public-site service | Internal read: resolve, blocks, theme catalog | Renders and caches; holds no database and no write path. |
| Platform edge | Cache public 200s/301s per headers | Honors `s-maxage`/SWR; never caches `private, no-store` responses. |

## Link.me parity (folded from research; itemized mapping)

| Capability | Owner feature | Status |
|---|---|---|
| Profile + rename 301s, page QR (canonical URL) | 06, this feature | done / this feature |
| Link scheduler windows, unlimited links, frozen block superset | 08 | done |
| Verification badge + follower total display | 07 | done |
| Themes, animated links, own-logo pro gates, avatar | 09 | done |
| Deep-link escape, mobile-first page, PWA | 11 | done |
| Analytics tiers, source/city, QR-as-URL | 12-15 | done |
| Shop, leads, resume, publish/automate, agency, monetize checklists | 17-47 | owned by each major's batch |

Source freshness: re-fetch the link.me reference set at each phase exit; deltas are recorded as spec amendments, never silent drift.

## Open questions (non-blocking; v1 defaults)

| # | Question | v1 default | Revisit trigger |
|---|---|---|---|
| Q-01 | Canonical host: bio only, or `/cv` indexed too | bio indexed; `/cv` `noindex` with canonical to the bio URL | SEO reports demand separate CV ranking |
| Q-02 | Launch `s-maxage` inside the 60-300 s window | 120 s | high churn makes stale complaints exceed support budget |
| Q-03 | Sitemap at 10M profiles (narrow hourly scan) | keep scan, cached 1h | scan p95 degrades the shared database; move to keyset cursor |
| Q-04 | Purge-chip HEAD poll interval | 5 s | edge quota pressure favors 10 s |

Related specs: identity/rename contract `docs/02-architecture-techlead/06-public-profile/03-api-contracts-and-payloads.md`; blocks read `docs/02-architecture-techlead/08-link-blocks/03-api-contracts-and-payloads.md`; deep links `docs/01-requirements-ba/11-deep-links-and-pwa/`.
