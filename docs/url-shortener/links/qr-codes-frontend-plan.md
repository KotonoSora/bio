# QR Codes Frontend Plan (downloads + print sheets — UI build steps)

PLAN ref: P2 QR UX (per-link downloads, print-sheet layout, bulk packs, scan-floor feedback). BA source: `docs/url-shortener/links/qr-codes.md`. Server truth in `docs/url-shortener/links/qr-codes-backend-plan.md` (floors, lifecycle, bulk atomicity — never redefined here).

## Purpose

Give the frontend developer an ordered UI procedure for QR delivery: per-link downloads with honest floor feedback, print-sheet assembly, reprint-from-live semantics, and bulk-pack flows. Follow steps 1–6 in order. The UI never generates official codes client-side — downloads come from the server log, so what prints is what scans.

## Users

- Frontend developer: builds the QR panel + print-sheet + bulk-pack screens.
- QA: executes the decode, floor-refusal, and reprint drills (plus physical scan checks).
- Creator/operator: downloads print-ready codes; ops runs labeled event packs.

## Functional requirements

1. Entry points: per-link `QR` tab (from the short-links manager with code preselected) + ops bulk-pack route (role-gated, invisible to creators). Both share the `QrDownload` component (format/size/error-level selects + download button + floor messaging) — one component, two callers.
2. Step 1 — single-download flow: select format (PNG with DPI select, or SVG for professional print) → size class (S/M/L/poster with physical-size captions, e.g. `M — 4cm, arm's length`) → error level (M default; H auto-required for outdoor/small — UI upgrades automatically with `Outdoor needs H` caption, not a silent change) → `POST :code/qr` → progress (render may take seconds for sheets) → download + alongside label file (short code + destination preview text — filed by humans, required in the same download). Below-floor requests (tiny size + low level + low contrast) are blocked pre-submit with the floor named AND server-rejected if forced — both layers, same message.
3. Step 2 — reprint flow: `Re-render current` (always visible on the QR tab) → `GET qr` regenerates from the LIVE link record → version note (`Rendered just now from the current target`) → old downloads show `May be outdated — re-render` once the link's destination or state changed after their render timestamp (staleness tracked client-side from link `updated_at` vs render `createdAt` — honest without a new endpoint).
4. Step 3 — print-sheet flow: multi-up layout builder (codes per sheet, label template, margins) → server assembles the sheet → single download (PDF? No — print sheet is an HTML/SVG sheet the browser prints via `window.print()` with the same no-server-PDF guardrail as resumes; the sheet is a print view, not a PDF artifact) → test-print checklist shown once (`scan the test square at arm's length before the full run`).
5. Step 4 — bulk-pack flow (ops): count + prefix + size class + per-location labels (table/paste interface, 100-row cap per pack with `split into multiple packs` guidance past it) → submit → atomic progress (all-or-nothing — partial failure shows `Pack failed, nothing created`, never 73 of 100) → per-code label sheet + stats-separation note (scan-vs-click split arrives in analytics via location codes).
6. Step 5 — non-live parent states: suspended/expired link's QR tab shows the state banner (`Suspended — codes resolve to the suspension page`) with generation disabled (backend 409 mirrored; the button explains instead of erroring after click).
7. Step 6 — speed rules: downloads stream (no in-memory assembly of 100-code packs client-side); thumbnails lazy; sheet builder paginates label rows at 25.

## Data / API touches

- Screen → endpoint map: QR tab → `POST|GET /api/v1/links/:code/qr`; bulk → `POST /api/v1/ops/qr-packs`; renders list (history) → owner qr reads. Session cookie + CSRF; Turnstile on generate/bulk submits.
- Type mirror: `QrRender {id, shortCode, format, sizeClass, errorLevel, createdAt, downloadUrl}`. Code → UI map: `qr_parent_not_live` → state banner (pre-disabled); `qr_below_floor|qr_low_contrast` → floor message with the requirement stated.

## Acceptance criteria

- Download drill: PNG/SVG + label file correct; decode check on downloaded files (spot QA decodes to short URL).
- Floor drill: below-floor combos blocked pre-submit with named floor; forced calls map server codes.
- Reprint drill: destination edit → old download flagged `May be outdated` → re-render carries new target.
- Sheet drill: multi-up sheet prints via browser print (no PDF artifact anywhere); test-scan checklist shown.
- Bulk drill: 100-code pack atomic with labels; partial failure creates nothing.
- Security drill: no client-side official generation (grep for encoder use outside watermarked preview); no `user_id` in bodies.

## Out of scope

- Generation/quality/bulk logic (backend plan above); render-log DDL (database plan above); mint/resolve (short-links plans); scan-split stats (analytics plans); profile QR (same URL there); payment QR payloads.

## Open questions

- Q1: Test-scan checklist — one checkbox or photo-proof upload for event packs?
- Q2: Label templates — fixed set vs customizable fields (layout-injection risk either way)?
