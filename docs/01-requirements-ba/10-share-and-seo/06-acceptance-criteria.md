# Share and SEO - Acceptance Criteria (feature 10)

Phase 1 BA · BDD form. Every AC runs in the drill suite (tasks of `docs/02-architecture-techlead/10-share-and-seo/05-step-by-step-implementation-plan.md`).

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Curl parity | A public profile with blocks, badge, theme, avatar | `curl` (no JS) requests `/:username` and `/:username/cv` | `200` with title/meta/canonical/OG/Twitter/JSON-LD and visible block text; the SPA fallback without tags is a named failure |
| AC-02 | Route isolation | `/assets/*` files and `/app/*` routes | Each is requested | Static assets serve directly; SPA fallback applies only under `/app/*`; public routes never serve the SPA shell |
| AC-03 | Scale gate | The P1 k6 profile (10k concurrent public reads, banner payload included) | Load runs | >95% cache hit; p95 <500 ms; publish-to-visible <2 min |
| AC-04 | Stampede | N concurrent cold misses on one slug | Requests arrive together | Exactly one render executes (lock `render:<slug>`); the rest coalesce |
| AC-05 | Crawler surfaces | Public and private profiles | `robots.txt` and `sitemap.xml` fetched | Sitemap lists only public slugs with canonical host and `lastmod`; robots points to it; private slugs absent |
| AC-06 | Rename through cache | Old username with a redirect row; warm cache | Old URL requested at edge | `301` to the new page (permanent, cacheable); no 404, no tagless HTML |
| AC-07 | Privacy parity and zero writes | Private, missing, and retired slugs; sustained load | Each fetched; query/monitor audit under load | Identical `404` shape and timing class; zero data-modifying statements anywhere on the render path |
| AC-08 | Preview privacy | Owner session; non-owner session | Preview URL fetched | Owner gets `private, no-store` + `noindex` + `Preview` badge; non-owner gets parity `404`; unsaved editor state never reaches the public URL |
| AC-09 | Head model | Profile with and without `seoDesc` | Preview JSON (head model) read | Booleans `title, description, canonical, og, twitter, jsonLd, inSitemap` correct; amber description row deep-links to the exact field |
| AC-10 | Share surfaces | A published profile | Copy link and QR generated | Both encode the canonical `/{username}` URL only; no parallel short link exists (probe returns nothing) |
| AC-11 | Purge honesty | Any mutation from 06/07/08/09; then the event dropped | Purge listener runs; then TTL-only path observed | Slug keys purged (old+new on rename); sitemap purged on visibility change; TTL-only heal within 2 min |
| AC-12 | Folded parity bar | The P1 bio parity checklist (unlimited links, scheduler windows, server-side pro gates, analytics tiers, QR, badge, follower display, deep-link escape, mobile-first, FAQ coverage) | Each item is checked against the new tree | Each item maps to a migrated feature or its pending batch; no parity item is unowned; P2/P4 checklists transit with their majors |
| AC-13 | No purge API | The dashboard bundle and network traffic | Grep for purge calls; probe an admin route | Zero client purge calls; the probe returns `404` |
