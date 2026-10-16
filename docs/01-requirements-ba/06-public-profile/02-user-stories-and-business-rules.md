# Public Profile - User Stories and Business Rules (feature 06)

Phase 1 BA · Stories seed `06-acceptance-criteria.md`; rules `BR-xx` are quoted by every later file.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Creator | claim a username at first setup | my page has one stable address | Must |
| US-02 | Creator | edit display name, bio, and SEO fields | my page represents me | Must |
| US-03 | Creator | toggle public/private with honest copy | I control who sees my page | Must |
| US-04 | Creator | rename my username with a safety guarantee | old links keep working | Must |
| US-05 | Visitor | open `/:username` without login | I see public pages only | Must |
| US-06 | Returning visitor / QR scanner | land on the new URL after a rename | saved links and printed codes never break | Must |
| US-07 | Creator | see when my change is live | no doubt about a stale public page | Should |
| US-08 | Public-site service | resolve a username in one call | SSR never queries the profile database directly | Must |

## Business rules

- **BR-01 Username shape.** Normalize = trim + NFKC fold + lowercase. Valid: 3-30 chars matching `^[a-z0-9_]+$`. Case-insensitive unique (`citext`) - `Alice` and `alice` collide at the database, not just in code.
- **BR-02 Reservation and retirement.** Reserved slugs are rejected in app AND DB: `app, api, admin, static, r, cv, shop, login, signup, pricing, blog, go, a`. Retired usernames (present in `username_redirects`) are permanently unclaimable; the redirect table has no delete path.
- **BR-03 One profile per user (v1).** A second create attempt fails `422 profile_limit_reached`; entitlement changes come from feature 03, never hardcoded here.
- **BR-04 Visibility.** `is_public=0` hides `/:username` (404), excludes the profile from `sitemap.xml` and search surfaces, and keeps it fully editable in the dashboard. Private and missing are indistinguishable publicly (no existence oracle).
- **BR-05 Rename safety.** Rename is one transaction: lock profile row, re-run policy, insert `old -> new` redirect, update username. The old URL 301s permanently; the mapping is insert-only.
- **BR-06 Sanitization.** HTML is stripped from `displayName`, `bio`, `seoTitle`, `seoDesc` (no markup is stored); `javascript:` links are rejected; stored links must be `https:`; public anchors render with `rel="noopener ugc"` (renderer: feature 10); CSP holds on dashboard and public page.
- **BR-07 Field limits.** `display_name` <= 80 chars; `bio` <= 500; `seo_title` <= 70; `seo_desc` <= 160. The server verdict is final; the client mirrors maxlength hints only.
- **BR-08 Scoping.** All dashboard endpoints require the session; `user_id` is server-set from the session, never accepted from client input; owner reads and writes pass RLS as a second layer.
- **BR-09 Publication.** Every successful mutation publishes `profile.published.v1`; purge-able caches are owned by feature 10 and bounded by a 60 s TTL, so even a lost event heals within one minute and the UI promise (live within 2 min) holds.
- **BR-10 Deletion pointer.** On the feature 02 signal the profile deactivates immediately; data purges after the 30-day grace. This feature never schedules its own purge.
- **BR-11 Audit.** Claim, update, rename, and visibility flips are audited with actor + timestamp; audit rows are append-only, retained 24 months.
- **BR-12 Bot gate.** Turnstile is required on claim and rename (the abuse-sensitive writes) - not on every save.
