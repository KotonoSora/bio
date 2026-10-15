# Deep Links Mobile Frontend Plan (mobile tab + matrix execution — UI build steps)

PLAN ref: P1c-1/P1c-2 dashboard and QA surfaces for the mobile contract. BA source: `docs/link-to-bio/public-page/deep-links-mobile.md`. Server truth in `docs/link-to-bio/public-page/deep-links-mobile-backend-plan.md` (anchor rules, banner markup, budgets — never redefined here).

## Purpose

Give the frontend developer and QA an ordered procedure for the consumer side of deep links: the dashboard mobile tab, deep-link preview icons, micro-script behavior verification, and the device-matrix execution. Follow steps 1–5 in order. The banner itself is SSR markup — the dashboard only observes and verifies it.

## Users

- Frontend developer: builds the `Mobile` tab and preview icons.
- QA: executes the device matrix (the actual acceptance gate for this slice).
- Creator: sees links behave correctly with zero per-link configuration.

## Functional requirements

1. Surfaces: dashboard `Mobile` tab (read-only: banner eligibility for the profile's UA mix from analytics reads + `escape_tap` counts); per-link deep-link preview icons in the block editor (YouTube/TikTok/Instagram URLs show the rendered form computed server-side, read-only); nothing else — there is no banner configurator (the banner is uniform by design).
2. Step 1 — mobile tab flow: open tab → banner-eligibility summary (share of visits from webview UAs, from the audience reads) → `escape_tap` trend (from analytics reads, same `generated_at` honesty as all widgets) → copy-link fallback tester (copies the profile URL, confirms clipboard write). No toggles exist on this tab — a toggle would imply configurability that does not exist.
3. Step 2 — preview-icon flow: block editor rows with YouTube/TikTok/Instagram URLs show a small icon with the computed deep-link form on hover (fetched once per URL, cached client-side per session). Plain `https:` links show no icon (nothing to rewrite). A URL that fails the allowlist shows the `bad_url_scheme` error instead (owned by the blocks frontend plan — this icon never appears on invalid rows).
4. Step 3 — micro-script behavior contract (verified, not implemented here): applies webview class from UA, wires banner primary + copy fallback, fires `banner_show` on display and `escape_tap` on action, pauses nothing (it is not a poller), writes no storage beyond beacon dedupe. Dashboard E2E asserts the script loaded (banner element interactive in a webview-UA emulation run).
5. Step 4 — device-matrix execution (the gate): iOS Safari/Chrome/IG/TikTok/Zalo webviews × Android Chrome/IG/TikTok/Zalo webviews, each checked for banner presence, primary-action landing in system browser/native app on a reference URL, copy fallback success, and checkout/form success on the reference destination. Matrix runs per release touching this slice; failures block promotion (same teeth as the k6 gate). Results attach to the release note (numbers, not pass/fail).
6. Step 5 — budget observability: tap-target audit (≥44px at 360px) and blocking-JS budget (≤50KB) run as build checks; the dashboard shows their last-green commit hash in the mobile tab footer (informational — failures block in CI, not in-UI).

## Data / API touches

- UI → server map: mobile tab → analytics read endpoints (UA mix, escape taps); preview icons → computed server-side with block data (no new endpoint; part of block reads or a `?deepLinkPreview=1` query param on the SSR head model — fixed at implementation to the latter if it keeps block reads unchanged). No new endpoints; no writes.
- QA inputs: reference destination URL (stable checkout + form), device lab inventory, release under test.

## Acceptance criteria

- Tab drill: eligibility + escape-tap figures match analytics tabs at same `generated_at`; no toggles exist anywhere on the tab.
- Icon drill: YT/TT/IG URLs show correct preview forms; plain links show none; invalid URLs show the allowlist error, never an icon.
- Script drill: webview-UA emulation shows interactive banner; `banner_show` + `escape_tap` events fire; no storage writes beyond dedupe.
- Matrix drill: full matrix green per release touching this slice; any red blocks promotion.
- Budget drill: last-green hashes visible; breach blocks CI.

## Out of scope

- Anchor/banner implementation (backend plan above); storage posture (database plan above); URL validation (blocks plans); SSR tags (share-seo plans); beacon pipeline (analytics plans); native apps.

## Open questions

- Q1: Reference destination — one stable external URL, or a first-party test checkout?
- Q2: Matrix automation (device farm) vs manual runs at current volume?
