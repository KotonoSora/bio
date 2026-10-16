# Deep Links and PWA - Step-by-Step Implementation Plan (feature 11)

Phase 2 TL · BA sources: `docs/01-requirements-ba/11-deep-links-and-pwa/` (ACs in `06-acceptance-criteria.md`). Contracts: `docs/02-architecture-techlead/11-deep-links-and-pwa/03-api-contracts-and-payloads.md`. Execute in order; T-IDs are stable.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | AnchorBuilder + canonical rewrite table + tracking-param stripping + renderer re-assert of the allowlist | feature 08 allowlist | AC-01, AC-05 |
| T-02 | BE | EscapeBanner SSR fragment + copy fallback + CSS reveal contract (hidden by default) | T-01 | Banner present in every payload; AC-02 partial |
| T-03 | BE | Micro-script artifact + `/public/*` static route + size assertion + `webview` class application + beacon calls | T-02 | <= 5 KB; AC-03 partial, AC-07 |
| T-04 | BE | Mobile-first template checks: `viewport-fit`, system-font opt-in, lazy intrinsic thumbnails, blocking-JS counter | T-03 | AC-03 |
| T-05 | BE | Cache invariance suite: UA fixture matrix, byte-identical bodies, no cache-key UA dimension | T-03 | AC-07 |
| T-06 | BE | Beacon wiring (`beacon.enabled`; stub collector until 12-15 land) | T-03 | AC-06 |
| T-07 | FE | Responsive analytics arrangement: routes, widget order (overview -> now-line -> top links -> revenue strip -> audience splits), `?points=` downsampling, hidden-tab polling pause | features 12-15 surfaces (drill attaches at that release) | AC-09, AC-12 partial |
| T-08 | FE | Install flow: deferred `beforeinstallprompt`, iOS instruction card, outcome beacons, hide when installed | T-07 | AC-08 |
| T-09 | FE | PWA artifacts: manifest (`start_url /app/analytics`, `standalone`, 192+512 maskable icons, theme color) + entrypoint `registerSW` (PROD-guarded) | T-08 | AC-08 |
| T-10 | FE | Hand-written `/app/sw.js`: shell + fingerprinted-asset precache, `/api/*` network-only allowlist, offline shell + stale banner, update pill + atomic swap | T-09 | AC-10 |
| T-11 | DevOps | Edge/origin config per contracts: `/app/*` noindex, CSP `worker-src 'self'`, `sw.js` no-cache, manifest MIME, `/public/*` immutable; fallback-order tests | T-03, T-10 | AC-11 |
| T-12 | DevOps | CI gates: micro-script size, tap-target audit, chunk budget, icon presence, Lighthouse thresholds, hidden-tab check | T-04, T-09 | AC-03, AC-12 |
| T-13 | FE/BE | Dashboard `Mobile` tab + block-editor preview icons (read-only; analytics reads) | T-06 | BA 04 surfaces |
| T-14 | DevOps | Device-matrix protocol (10 devices) + reference checkout/form + release attachment | T-01..T-11 | AC-02, AC-14 |
| T-15 | BE | Security wiring: CSP public + dashboard, worker-scope assertion test, UA-never-logged test, allowlist re-assert tests | T-05 | AC-05, AC-11 |
| T-16 | DevOps | Observability: metrics, dashboards, alerts (old worker > 48 h; missing matrix artifacts) | T-06, T-10 | BA 07 observability |
| T-17 | DevOps | Release gate: digests, SBOM, podman parity, rollback | all | Cold boot + rollback drill |

## Scheduling note

T-01..T-06 ship first (public side; beacon stub). T-07..T-10 depend on the features 12-15 analytics surfaces and are scheduled in the first release after that batch - the install and same-numbers drills attach there. This is a forward dependency recorded the same way as feature 10's resume-snapshot read; it does not reorder the migration register.
