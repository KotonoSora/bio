# QR Codes - Architecture and Tech Stack (feature 30)

Phase 2 TL · Feature 30 of 53; QR module of `link-service` (port 8083, `links_db`, Redis db 3). Implements `docs/01-requirements-ba/30-qr-codes/`. Host: `docs/02-architecture-techlead/29-short-links/` (link truth, lifecycle, ops auth conventions). Scan counting: `docs/02-architecture-techlead/31-short-link-analytics/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | DEVIATION CARRIED (feature 29 boundary map): the legacy monolith package `com.app.links.qr` maps to the `link-service` QR module; implementation in this service only, `links_db` migration `V2__qr_codes.sql` (V1 = feature 29 baseline; new `V_n` per change, no ALTER chains). | The renderer reads the live link row in-request; one transaction domain with link truth. |
| D-2 | Quality floors are server-side constants, enforced before any bytes exist: error level M minimum (H required for `s`/`poster`), quiet zone ≥ 4 modules, module size ≥ 0.4 mm at the declared class, dark-on-light contrast ≥ 7:1 asserted at render. | A failed export beats a failed print run (BA O-2). |
| D-3 | One flagged dependency: `com.google.zxing:core` (pure-Java, zero native deps, container-safe). Grep gate: exactly one server-side encoder in the links module; the only client-side encoder in the product remains feature 10's share utility (canonical profile URL, non-print, documented allowance). | No dependency sprawl; container drill stays clean. |
| D-4 | The render log is subordinate: no destination column, no lifecycle columns; `short_code` FK cascades only for the exceptional purge (tombstoned links keep their renders); reprints always re-render from the live link record. | The log-not-truth design is structural, not procedural. |
| D-5 | Artifacts: PNG/SVG bytes under `links/qr/{userId}/{renderId}.{png\\|svg}` on the uploads volume, 30-day retention sweep (re-renderable cache); downloads via owner-bound HMAC grants (`renderId\\|userId\\|exp`, 15 min) served service-mediated; row + file written together on success (file failure → no row, 503). | Pixels are cache; the log is history; re-render is always possible. |
| D-6 | Packs: ops-only route (`ROLE_OPS` re-verified per request, per feature 29's ops convention); one transaction mints N links (shared `pack_ref`, per-location distinct codes) + N render rows; labels required 1:1; audit carries operator, purpose, packRef, codes; partial failure creates nothing. | Per-location measurement never half-exists (BA BR-07). |
| D-7 | Print sheets are server-assembled SVGs (multi-up grid + label rows), printed via the browser; no PDF artifact exists (platform PDF guardrail: PDF = `window.print()` A4 only). | Same guardrail as resumes; no server PDF machinery. |
| D-8 | The scan path is untouched: scans ride feature 29's resolve + queued hit (`link.hit.v1`); scan-vs-click heuristics live in feature 31; this feature guarantees only the code-level input (event-pack codes give exact splits). | No QR-specific server path exists to slow down or secure. |
| D-9 | Abuse + lifecycle: Turnstile-class challenge on generate/bulk; quotas (owner 60 renders/h; ops 10 packs/day); preview rendering watermarked `PREVIEW — not for print` (overlay, not trust); purge extends feature 02's `link-service` participant (rows by `user_id` + `links/qr/{userId}/` files). | Same posture as every owner-scoped artifact surface. |

## Component map

| Piece | Home | Note |
|---|---|---|
| Renderer core (payload, floors, palette assert, PNG/SVG) | `link-service` | zxing core; DPI 150/300/600 for PNG |
| Render log + RLS | `links_db` V2 | subordinate log; FK cascade hygiene only |
| Download + label + grants | `link-service` | companion label from the live link; 15-min owner grants |
| Sheet assembly | `link-service` | SVG multi-up; browser print; test-scan checklist copy |
| Packs | `link-service` | atomic; ops auth; audit with code list |
| Scan split input | feature 31 | event-pack codes = exact attribution |

## Boundaries

Owns: payload rule, floors, rendering, exports, labels, sheets, render log, packs, quotas, preview watermarking, render-file lifecycle. Does not own: link minting/resolve/lifecycle (29 — read-only), hit counting/filters/stats (31), profile share QR (10 — client-side utility, canonical URL), payment QR payloads (provider majors), custom short domains (future work).

## Consumers

| Consumer | Contract |
|---|---|
| feature 29 manager | Stats/QR handoff links target this module + 31 |
| feature 31 | event-pack per-location codes give exact scan splits; organic split is 31's heuristic |
| ops console | pack route + audit views |
