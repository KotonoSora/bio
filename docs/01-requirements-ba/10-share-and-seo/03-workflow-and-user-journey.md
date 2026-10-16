# Share and SEO - Workflow and User Journey (feature 10)

Phase 1 BA · Flows are normative; rules cite `02-user-stories-and-business-rules.md`, errors cite `05-exception-handling-and-errors.md`.

## Journey 1 - Cold load, rename, and 404 (visitor / crawler)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Visitor/crawler | `GET /:username` (no JS) | Edge cache hit serves full HTML with tags; miss renders server-side | BR-01, BR-03 |
| 2 | Public-site service | Resolve order: cache -> profile -> redirect -> 404 | `200` bio / `301 /{newUsername}` / `404` | BR-05, BR-09 |
| 3 | Public-site service | `200` assembled from resolve + windowed blocks + theme tokens | Title/meta/canonical/OG/Twitter/JSON-LD + block text, escaped | BR-02, BR-06 |
| 4 | Visitor/crawler | `GET /:username/cv` | `200` resume snapshot view when `showResume` + published snapshot; else same-parity 404 | BR-05 |
| 5 | System | Private, missing, or retired slug | Identical 404 shape and timing; `private, no-store` | BR-05 |

## Journey 2 - Publish and purge (creator)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Saves via the owning editor (profile/blocks/theme) | Mutation commits; `profile.published.v1`; purge chip appears | BR-04, BR-11 |
| 2 | Public-site service | Purge listener fires | Slug keys deleted (old+new on rename); sitemap purged on visibility change | BR-04, BR-07 |
| 3 | SPA | HEAD-polls the public URL every 5 s | Chip clears when the live page reflects the change | BR-11 |
| 4 | System | Purge event lost | 60 s service TTL + SWR revalidation heal within 2 min | BR-04 |
| 5 | SPA | Still pending after 2 min | `Still publishing - safe to leave`; change is queued server-side, nothing lost | - |

## Journey 3 - Crawl and index (search engine)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Crawler | `GET /robots.txt` | Static rules + sitemap pointer | BR-07 |
| 2 | Crawler | `GET /sitemap.xml` | Public slugs only, canonical host, `lastmod` from `updated_at`; cached <= 1 h | BR-07 |
| 3 | System | Profile goes private | Slug leaves the sitemap within the purge/TTL bound; public URL 404s | BR-05, BR-07 |
| 4 | Crawler | Old username URL | `301` to the new page (permanent) | BR-09 |

## Journey 4 - Preview and checklist (creator)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Opens preview from any editor | Sandboxed iframe (`/app/preview/:username`) renders owner-scoped saved state | BR-10 |
| 2 | Public-site service | Owner check via session JWT | Owner -> render `private, no-store` + `noindex` + `Preview` badge; non-owner -> parity 404 | BR-05, BR-10 |
| 3 | Creator | Reads the SEO checklist | Green/amber rows (title, description, canonical, OG, Twitter, JSON-LD, sitemap) with deep links to the exact fields | BR-02 |
| 4 | Creator | Profile is private | Checklist shows `Hidden from search (private)` as informational, never an error | BR-05 |
| 5 | Creator | Copies link or shows QR | Both encode the canonical `/{username}` URL | BR-12 |

## Journey 5 - Cold-miss storm (platform)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Public-site service | Viral link causes N concurrent cold misses on one slug | One render acquires the `render:<slug>` lock; the rest wait | BR-08 |
| 2 | System | Render completes | Result cached; waiters serve the same HTML; origin saw one render | BR-03, BR-08 |
| 3 | System | Render exceeds deadline | Stale-if-available served; else branded 503 (never a tagless SPA fallback) | BR-01 |
