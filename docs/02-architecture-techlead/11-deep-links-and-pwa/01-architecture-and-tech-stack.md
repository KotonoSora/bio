# Deep Links and PWA - Architecture and Tech Stack (feature 11)

Phase 2 TL · BA sources: `docs/01-requirements-ba/11-deep-links-and-pwa/` (7 files). Stack pins: `docs/02-architecture-techlead/00-platform-baseline/` (Boot 4.1.1, Kotlin 2.4.21, Gradle 9.8.1, PG 18.6, React 19.3, Compose). Extends feature 10's envelope: `docs/02-architecture-techlead/10-share-and-seo/01-architecture-and-tech-stack.md`.

## Service boundaries

| Component | Owns | Runtime | Notes |
|---|---|---|---|
| `public-site-service` (delta) | `ssr.links` module: AnchorBuilder, EscapeBanner fragment, micro-script artifact, mobile template contract | Existing container, port 8084 | No new service, no database; adds one static route family `GET /public/*` |
| Platform SPA origin (delta) | Manifest, icons, `/app/sw.js`, responsive routes, install UX | React.js 19.3 static build | The SPA origin container is pinned in `00-platform-baseline`; feature adds artifacts + headers only |
| Edge/origin config (delta) | Headers for `/public/*`, `/app/*`, `sw.js`, manifest; fallback order unchanged from feature 10 | Docker Compose | Config-only change; no new container |

Compose delta: none. No environment variables, volumes, or services are added.

## Key decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | Uniform external open mode; no per-link setting exists | BA BR-03; eliminates a whole config surface and state |
| D-2 | Webview detection is client-side from `navigator.userAgent`; SSR HTML stays byte-identical across UAs | BA BR-04; cache invariance is non-negotiable (feature-10 keys, `Vary: Cookie` owner-mode only) |
| D-3 | Micro-script is dependency-free vanilla JS served by `public-site-service` under `/public/*`, immutable | One deployable owns the artifact; no framework tax on the public path |
| D-4 | Escape primary swaps to `x-safari-https://` only where honored (iOS webviews); copy fallback always present | BA BR-03; a primary action that silently fails is worse than none |
| D-5 | Provider URLs rewrite to canonical App-Link `https:` forms; no custom/`intent:` schemes from input | BA BR-02; OS-level App Links resolution, security review friendly |
| D-6 | Worker scoped `/app/` only; `/api/*` network-only (allowlist; unlisted defaults to network) | BA BR-07; root scope would intercept public SSR traffic |
| D-7 | Update via restart pill, never silent `skipWaiting` | Predictable cache swap; stale-new mix never half-applied |
| D-8 | PWA only; no native iOS/Android builds, ever | P0 reconciliation; one release train, one metric truth |

## Build and integration

- `public-site-service` build: micro-script minify + size assertion task (hard fail > 5 KB), template audit task (blocking-JS counter, tap-target scan, `viewport-fit` presence), renderer tests for the anchor/rewrite table. No new dependencies.
- SPA build: icon + manifest presence check (build fails on a missing artifact), route-level code-split budget, Lighthouse CI job at the BA thresholds.
- React integration: dashboard routes are existing SPA routes; mobile work is responsive CSS + chart downsampling inside features 12-15 widgets; service-worker registration is an entrypoint concern (`registerSW` guarded by `import.meta.env.PROD`), hand-written worker (no library).
- Both builds publish digest-pinned images; podman parity per release gate.

## Reuse map

- Cache envelope, route precedence, purge: feature 10 (unchanged).
- URL allowlist + `bad_url_scheme`: `docs/02-architecture-techlead/08-link-blocks/03-api-contracts-and-payloads.md`.
- Beacon event types: features 12-15 (forward contract recorded in `docs/02-architecture-techlead/11-deep-links-and-pwa/03-api-contracts-and-payloads.md`).
- SSO/session + edge identity: platform baseline + feature 06.
- Analytics surfaces reshaped for mobile: features 12-15 (T-07..T-10 schedule note).
