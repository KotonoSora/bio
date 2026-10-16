# Deep Links and PWA - Non-Functional Requirements (feature 11)

Phase 1 BA · Every threshold is quantified; owners in the TL files. Nothing here loosens feature-10 numbers: the public cache envelope stays as specified there unless a row explicitly tightens it.

| Category | Requirement | Threshold |
|---|---|---|
| Latency (public page) | Feature must not move SSR timings | Cache-hit p95 <= 100 ms; field LCP p75 <= 2.5 s on a mid-tier Android at 4G |
| Latency (micro-script) | Parse + banner reveal | <= 5 ms parse; banner visible <= 50 ms after the script executes |
| Latency (PWA) | Cold vs warm shell | Interactive <= 2.5 s cold (first visit) and <= 1.0 s warm (shell cached), mid-tier Android |
| Budgets | JS + assets | Public blocking JS <= 50 KB gzip; micro-script <= 5 KB uncompressed; public HTML + assets within the feature-10 weight budget |
| Quality gates | Lighthouse mobile (CI, every release candidate) | Dashboard: perf >= 85, a11y >= 95; public: perf >= 95, a11y >= 95 |
| Accessibility | Targets + banner | Tap targets >= 44 x 44 px at 360 px width; banner keyboard-focusable, screen-reader labelled, contrast per theme tokens (unchanged) |
| Offline honesty | Stale labelling | Shell renders <= 1.0 s from cache; every figure carries the stale label; no number is shown without data or the label |
| Availability | Degrade contract | Worker/PWA failure never affects the public page (verified 100% in drills); worker registration failure degrades to normal web app; no worker-SSR coupling |
| Privacy | UA handling | UA used request-scope only; never persisted, never logged on the public route, never a cache-key dimension; micro-script writes only its sessionStorage dedupe keys |
| Security | Headers + scope | Public CSP `script-src 'self'`; worker scope `/app/` only; `/app/*` noindex; beacon abuse handled by analytics rate limits |
| Session | PWA continuity | Same SSO session as desktop; expiry redirects to sign-in and returns to the same route in <= 1 step |
| Quotas | Event hygiene | `banner_show` <= 1 per page view; `escape_tap` deduped per session; dashboard polling pauses while hidden (0 requests while hidden beyond a 30 s resume refresh) |
| Localization | Banner + install copy | Vietnamese default, English fallback; figures use the analytics formats |
| Observability | Metrics + alerts | Track `pwa.install{outcome}`, `sw.version` adoption, `banner.eligibility`, `escape.tap{result}`, `budget.last_green`; alert if old worker adoption persists > 48 h or a released tag lacks matrix artifacts |
