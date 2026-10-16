# Deep Links and PWA - Acceptance Criteria (feature 11)

Phase 1 BA · BDD. Each `Then` maps to a drill in `docs/02-architecture-techlead/11-deep-links-and-pwa/02-database-schema-design.md` and `docs/02-architecture-techlead/11-deep-links-and-pwa/04-step-by-step-implementation-plan.md`.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Zero-config anchors | Published profile with YouTube/TikTok/Instagram + plain links | SSR HTML inspected | Every anchor is `target="_blank" rel="noopener ugc"`; provider URLs emit canonical App-Link forms; no open-mode field exists anywhere |
| AC-02 | Device-matrix escape | 10-device matrix (iOS Safari/Chrome/IG/TikTok/Zalo, Android Chrome/IG/TikTok/Zalo) | Open profile, tap banner primary, then a checkout link | Banner present in every webview; primary lands Safari/Chrome or native app; copy fallback succeeds; reference checkout/form completes |
| AC-03 | Budgets | Production build | CI runs budget checks | Blocking JS <= 50 KB gzip; micro-script <= 5 KB uncompressed; tap targets >= 44 px at 360 px; any failure blocks the build |
| AC-04 | Cache envelope intact | Warm SSR cache per feature 10 | k6 headroom re-run with banner payload | Hit ratio >= 95%; publish-to-visible <= 120 s; page weight within the feature-10 budget |
| AC-05 | No scheme injection | Hostile block-URL fixtures | Author via API, then fetch the page | Save rejected `bad_url_scheme`; renderer never emits non-`https` from user input |
| AC-06 | Measurement | Stub collector (analytics 12-15 pending) | Webview visit shows banner then escapes | Exactly `banner_show` once per view and `escape_tap{result}` per action; zero per-hit database writes attributable to this feature |
| AC-07 | Cache invariance | Same slug, UA fixture set | Fetch as Safari/IG/Zalo webviews | Byte-identical HTML bodies; no UA dimension in cache keys |
| AC-08 | PWA install | Android Chrome + iOS Safari | Run the install flow | Android: prompt at the deferred moment, outcomes beaconed; iOS: instruction card once; installed icon boots `/app/analytics` standalone |
| AC-09 | Same numbers | Desktop + installed PWA, same account | Compare all 5 analytics surfaces at one `generatedAt` | Identical figures and clamps; charts downsample only, totals exact |
| AC-10 | Worker honesty | Worker installed | Go offline, read a widget, redeploy, restart | Offline labels every figure stale; `/api/*` never cache-served; new version purges old caches after restart |
| AC-11 | SEO isolation + routing | Crawler fetches | Crawl `/app/*`; request unknown slug; request `sw.js` | `/app/*` carries `noindex`; unknown slug returns 404 HTML (never SPA); `sw.js` never swallowed by the fallback |
| AC-12 | Background + Lighthouse | Dashboard open on a mid-tier phone | Hide the tab 5 min; run Lighthouse mobile | Polling stops while hidden and resumes on focus; dashboard perf >= 85 / a11y >= 95; public perf >= 95 |
| AC-13 | Absence drill | Repo + schema review | Inspect migrations, routes, tab controls | Zero tables, zero endpoints, zero toggles added by this feature |
| AC-14 | Release gate | All above green | Promote the candidate | Matrix + budget artifacts attached to the release; any red blocks promotion |
