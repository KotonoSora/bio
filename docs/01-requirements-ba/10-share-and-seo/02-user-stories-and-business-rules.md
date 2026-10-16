# Share and SEO - User Stories and Business Rules (feature 10)

Phase 1 BA · Stories seed `06-acceptance-criteria.md`; rules `BR-xx` are quoted by every later file.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Visitor / crawler | cold-load `/:username` and `/:username/cv` with no JS | I see the full content and tags, like any browser | Must |
| US-02 | Creator | have my published edits live within the purge SLA | my page is current without waiting on caches | Must |
| US-03 | Creator | preview my saved page privately before it is widely visible | I catch mistakes without leaking drafts | Must |
| US-04 | Creator | see a read-only SEO checklist with fix links | I know what search engines will show | Should |
| US-05 | Search engine | fetch `robots.txt` and a public-only `sitemap.xml` | indexing stays correct and safe | Must |
| US-06 | Visitor | follow an old username link and land on the new page | links never rot | Must |
| US-07 | Creator | copy my link and show a QR of it | I can share the same URL everywhere | Should |
| US-08 | Platform | absorb a viral cold-miss storm with one render | popularity never becomes an origin incident | Must |

## Business rules

- **BR-01 Route isolation.** Public SSR routes are served by the public-site service; dashboard routes live under `/app/*` on the SPA origin; `/assets/*` serves statically before any renderer. The SPA fallback never carries SEO tags.
- **BR-02 Complete tags.** Every 200 carries `<title>` (`seoTitle` else display name), meta description (`seoDesc` else derived from bio), canonical, OG/Twitter tags, and JSON-LD `Person` - all server-rendered, all escaped. Tag presence is asserted by fixture tests.
- **BR-03 Cache-first.** Public 200s: `Cache-Control: public, s-maxage=120, stale-while-revalidate=600` (launch default inside the 60-300 s window). `private, no-store` for previews and 404s. Target > 95% cache hit.
- **BR-04 One purge path.** Profile, block, theme, avatar, badge, and rename changes all publish `profile.published.v1`; renames also publish `username.renamed.v1`. The renderer purges the affected slug keys (old and new on rename) and catches up <= 2 min even when an event is lost (60 s service TTL + SWR).
- **BR-05 Privacy parity.** Private, missing, and retired slugs return the same 404 shape and timing class; no existence oracle exists on any public route. Retired usernames resolve through the rename 301 where a redirect row exists.
- **BR-06 Zero writes.** The render path performs no data-modifying statement anywhere; view/click counting is the analytics beacon pipeline (features 12-15). Structurally, the public-site service holds no database at all.
- **BR-07 Crawler surfaces.** `sitemap.xml` lists only public profiles, paginated, cached <= 1 h, purged on publish visibility changes; `robots.txt` is static and points to the sitemap.
- **BR-08 Single-flight.** Concurrent cold misses on one slug coalesce to one render (5 s lock ceiling); losers wait rather than stampede.
- **BR-09 Rename redirects.** An old username returns 301 to the new page, cacheable as permanent (rows are insert-only in feature 06); the redirect survives the cache layer.
- **BR-10 Preview.** The dashboard preview renders owner-scoped saved state, `private, no-store`, `X-Robots-Tag: noindex`, badged `Preview - not public`; unsaved selections preview only in dashboard chrome (feature 09) - no draft store exists.
- **BR-11 No client purge API.** The dashboard never calls a cache-admin endpoint; it polls the public URL and reflects cache state honestly.
- **BR-12 Share surfaces.** QR codes and copy-link encode the canonical `/{username}` URL only; v1 adds no parallel short-link system for sharing.
