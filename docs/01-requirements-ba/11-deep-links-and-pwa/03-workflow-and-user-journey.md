# Deep Links and PWA - Workflow and User Journey (feature 11)

Phase 1 BA · Flows are normative; rules cite `02-user-stories-and-business-rules.md`, errors cite `05-exception-handling-and-errors.md`.

## Journey 1 - Tap a link inside a webview (mobile visitor)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Visitor | Opens `/:username` from a TikTok/Instagram/Zalo bio | Cached SSR HTML + micro-script (`<5 KB`) load; byte-identical across webviews | BR-04 |
| 2 | Micro-script | Reads `navigator.userAgent` against the shipped pattern list | Applies one `webview` boolean class; CSS reveals the `Open in browser` banner | BR-03 |
| 3 | Visitor | Taps the banner primary action | iOS webviews: `x-safari-https://` escape where honored; otherwise the `https:` URL; `escape_tap` registered after success | BR-03, BR-05 |
| 4 | Visitor | Uses copy-link fallback (always present) | URL copied; guidance text for manual paste | BR-03 |
| 5 | Visitor | Taps a block link | `target="_blank" rel="noopener ugc"`; YouTube/TikTok/Instagram open the native app when installed, browser otherwise | BR-02 |
| 6 | Visitor | Reference checkout/form opens | Works because the context is a real browser (matrix passes only then) | BO-1 |

Alternative: detection misses a new webview -> no banner, content fully usable (advisory-only rule); the pattern list ships in a deployable artifact and is extended by release.

## Journey 2 - Creator reviews mobile behavior (zero config)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Opens the dashboard `Mobile` tab | Read-only: banner eligibility of the profile's UA mix + `escape_tap` trend (from analytics reads) | BR-05 |
| 2 | Creator | Hovers a YouTube/TikTok/Instagram row in the block editor | Preview icon shows the computed deep-link form (server-computed, cached per session) | BR-02 |
| 3 | Creator | Looks for open-mode settings | None exist; the tab has no toggles (uniform by design) | BR-03, Q-01 |

## Journey 3 - Launch-night check from a phone (PWA)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | Creator | Opens `/app/analytics` on a phone, taps `Install app` | Manifest + icons install; `prompt_*` outcomes beaconed | BR-08 |
| 2 | Creator | Reads overview -> now-line -> top links -> revenue strip -> audience splits | Identical figures to desktop (same endpoints, same clamping); charts downsample, totals exact | BR-06 |
| 3 | Creator | Switches away from the tab | Realtime polling pauses (`document.hidden`); resumes on focus | BR-06 |
| 4 | Creator | Opens offline | Shell renders + persistent stale-data warning; no numbers without the label | BR-07 |
| 5 | System | New worker version deployed | `Update available - restart` pill; restart switches atomically | BR-07, Q-03 |

## Journey 4 - Release gate (QA + platform)

| Step # | Actor/System | Trigger Action | System Response | Rules |
|---|---|---|---|---|
| 1 | CI | Build runs | JS budgets, tap-target audit, Lighthouse thresholds, icon check all asserted | BR-10 |
| 2 | QA | Runs the device matrix (iOS Safari/Chrome/IG/TikTok/Zalo x Android Chrome/IG/TikTok/Zalo) | Banner presence, primary landing, copy fallback, checkout/form success recorded with numbers | BR-10 |
| 3 | Release owner | Matrix red on any device | Promotion blocked (same teeth as k6) | BR-10 |
| 4 | Edge/origin | Unknown public slug requested | 404 - never SPA HTML; `/app/*` deep links boot the SPA with `noindex` | BR-09 |
