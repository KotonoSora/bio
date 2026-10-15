# QR Codes Backend Plan (server-side generation + quality floors — server build steps)

PLAN ref: P2 delivery (QR per `docs/PLAN.md`) + S-1/S-4/S-5/S-6/S-7. BA source: `docs/url-shortener/links/qr-codes.md`. Phasing per `docs/platform/delivery/execution-overview.md` (E2). Schema in `docs/url-shortener/links/qr-codes-database-plan.md` (render log, written once); link truth in `docs/url-shortener/links/short-links-database-plan.md`. Download/print screens in the qr frontend plan (splits next to this file).

## Purpose

Give the backend developer an ordered procedure for QR generation with non-negotiable quality floors: encode-short-only, error-correction minimums, quiet zones, contrast floors, lifecycle inheritance, and bulk packs. Follow steps 1–8 in order. A failed export beats a failed print run — the generator refuses before rendering whenever scan-ability cannot be guaranteed.

## Users

- Backend developer: executes steps 1–8 in package `com.app.links.qr`.
- QA: runs encode, scan-matrix, lifecycle, contrast, and bulk drills (including physical reference prints).
- Creator/operator: downloads print-ready codes; ops runs event packs.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — modular monolith (`links` module). Monolith pros: generation reads the live link row in-request (reprints always current); render log + audit share the transaction; one release train. Microservices cons avoided: a render service would split generation from link truth (stale-target prints) for CPU-trivial QR encoding. Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; none fire. Starters: web, validation, data-jpa, security, actuator, micrometer. Dependency addition flagged once here: `com.google.zxing` (pure-Java QR encoder — zero native deps, container-safe; no headless-browser or image-native packages, grep-gated).
2. Step 1 — schema: run the qr database plan script once (after `short_links`). No other DDL in this slice.
3. Step 2 — `service/QrRenderService.kt#render(code, format, sizeClass)`: assert parent link owned + `live` (non-live → `409 qr_parent_not_live` with the link state named — generating print for a suspended link is refused, not rendered-then-dead) → payload = canonical short URL (`https://<host>/go/<code>`, NEVER the destination — asserted by decode test on every export) → enforce floors: error level M minimum (H required for outdoor/small classes — request below floor → `422 qr_below_floor` naming the floor), quiet-zone margin baked into the bitmap, minimum module size per declared print size (refuse unprintable requests), high-contrast palette only (below-floor palettes → `422 qr_low_contrast`) → render PNG (declared DPI) or SVG (vector) → store bytes to blob under render id → insert log row → audit. Every export stamped alongside with short code + destination preview text (humans file by label).
4. Step 3 — `GET /api/v1/links/:code/qr` (re-render current): same pipeline from the LIVE link record (never from a cached image — reprint after destination edit carries the current target by construction). Print-sheet layout (multi-up with labels) assembled server-side for event packs.
5. Step 4 — bulk packs (audited ops action): `POST /api/v1/ops/qr-packs {count, prefix?, sizeClass, labels[]}` → mint N distinct short links (per-location measurement) + render N codes + label sheet → all-or-nothing (partial pack impossible) → audit with operator + purpose. Per-code stats separate scan traffic later via the analytics scan-split.
6. Step 5 — scan path (performance): scans land on the normal short-URL resolve path (cached redirect, queue-logged hit) — no QR-specific server path, no extra latency budget beyond the redirect SLA. No client-side generation for official codes (browser preview rendering allowed, watermarked `PREVIEW — not for print`, enforced by overlay, not by trust).
7. Step 6 — lifecycle inheritance (automatic): suspended/expired parent → QR resolves to the same gone/suspension behavior (pixels unchanged, resolution changed — drill asserts old prints follow current state, proving the log-not-truth design).
8. Step 7 — abuse: Turnstile on generate/bulk endpoints (S-5); per-user generation quotas (bulk outside ops → 403 + audit); 2MB+ upload N/A (no uploads here — thumbnails N/A); secrets via env only.
9. Step 8 — tests + container: decode test (every export decodes to the short URL); scan matrix (iOS/Android native + bank/wallet scanners × sizes × levels — arm's-length minimums; below-floor refused with reason); lifecycle test (old print follows new state); contrast test (reference images in CI); bulk test (100-code pack: unique links, correct labels, stats separation); `@WebMvcTest` per code; same image via `docker run` + `podman run` per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`.

## Data / API touches

- Schema: database plan above (render log only). Endpoints: `POST /api/v1/links/:code/qr {format, sizeClass, errorLevel?}` → `201 {renderId, downloadUrl}` / `409 qr_parent_not_live` / `422 qr_below_floor|qr_low_contrast`; `GET /api/v1/links/:code/qr` (re-render current); ops `POST /api/v1/ops/qr-packs` (atomic, audited). Bytes: blob objects by render id (short TTL for intermediates).

## Acceptance criteria

- Encode drill: all exports decode to short URLs; destination edit + reprint resolves new target.
- Scan drill: matrix minimums scan at arm's length; below-floor refused with reason.
- Lifecycle drill: suspended/expired parent's QR matches URL behavior; no orphans.
- Contrast drill: below-floor palettes rejected; CI reference-image check green.
- Bulk drill: 100-code pack unique + labeled + stats-separated, atomic.
- Container drill: digest pair boots, compose green, rollback green, contract diff clean; native-dep grep clean.

## Out of scope

- Render-log DDL (database plan above); download/print-sheet screens (frontend plan); mint/resolve (short-links plans); hit counting + scan-split (analytics plans); profile QR conventions; payment QR payloads; P4/P5.

## Open questions

- Q1: Brand styling above floor vs plain-only v1 (see database plan Q1)?
- Q2: Intent presets vs manual size picker (see database plan Q2)?
