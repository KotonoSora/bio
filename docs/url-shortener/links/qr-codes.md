# QR Codes (generation for short URLs)

PLAN ref: none — new scope beyond PLAN P0–P5, flagged (see `links/short-links.md` for placement). QR targets are always short URLs; this doc owns rendering and print discipline, never destinations.

## Purpose

Define how every QR code in the product gets made: QR images encode short links (never raw destination URLs), inherit the link's lifecycle automatically, and print/scan reliably across sizes and damage levels. Scattered QR generation (each feature rolling its own encoder with its own error-correction guess) produces unscannable print runs and orphan codes pointing at dead targets — one generator with fixed quality floors prevents both.

## Users

- Creator/operator: *As a Creator/operator, I want to download a print-ready QR for a short link with size/format choice so that reprints still scan reliably.* Downloads a print-ready QR for a short link (campaign poster, packaging, event signage), picks size/format, reprints later with confidence it still scans.
- Scanner (phone camera, any bank/wallet/camera app): *As a Scanner, I want to point at the code and land on the short URL so that the normal redirect + counting path follows.* Points, lands on the short URL, follows the normal redirect + counting path.
- Print vendor (indirect): *As a Print vendor, I want to receive files with quiet zones and contrast so that real printing survives.* Receives files with quiet zones and contrast that survive real printing.

## Functional requirements

1. Encode-short-only rule: the QR payload is always the short URL (`https://<host>/go/<code>`), never the destination — so destination edits, expiry, suspension, and analytics keep working after print. Minting a QR requires an existing live short link; the QR record references it (`qr_codes{short_code FK, format, size, error_level, created_at}`).
2. Generation quality floors (non-negotiable): error-correction level M minimum (H for outdoor/small print), quiet-zone margin baked into every export, minimum module size per declared print size (refuse exports that can't scan at the requested size — a failed export beats a failed print run), high-contrast palette only (no brand-color QRs below the contrast floor, see Q1).
3. Formats and sizes: PNG (screen/print raster at declared DPI) + SVG (vector for professional print) + print-sheet layout (multi-up with labels for event packs); every export stamped with short code + destination preview text alongside (humans file by label, not by pixels).
4. Lifecycle inheritance (automatic, no separate expiry): suspended/expired short link → its QR resolves to the same gone-page/suspension behavior as the URL (the pixels don't change; the resolution does). Reprints always re-render from the live link record — never from a cached image — so a reprint after a destination edit carries the current target.
5. Scan-path performance: QR scans land on the short URL's normal resolve path (cached redirect, queue-logged hit) — no QR-specific server path, no extra latency budget beyond the redirect SLA.
6. Bulk/event packs: audited ops action generating labeled sets (per-table, per-booth codes each resolving to distinct short links for per-location measurement); per-code stats distinguish scan traffic from click traffic by referrer/user-agent class where signals allow.

## Data / API touches

- New table `qr_codes{short_code FK, format, size_class, error_level, created_at}` (render log, not truth — truth stays in `short_links`). Generation runs server-side in the dashboard/API worker (library choice at implementation: zero native deps, Workers-safe); no client-side generation for official codes (preview-only rendering allowed in browser, clearly watermarked as such).
- Endpoints: `POST /api/v1/links/:code/qr` (generate/export), `GET /api/v1/links/:code/qr` (re-render current), ops bulk-pack generation. All PG via `worker/db/*`; owner-scoped RLS matching the parent link.
- Analytics: scans counted through the short-link hit pipeline with a scan-vs-click signal split where identifiable (see `tracking/link-analytics.md`).

## Acceptance criteria

- Encode drill: every exported QR decodes to the short URL (never the destination); destination edit + reprint resolves to the new target; old prints follow the link's current state. *GWT: Given an exported QR for a short URL; When decoded and when the destination is edited with reprint; Then every code decodes to the short URL (never the destination), reprints resolve to the new target, and old prints follow the link's current state.*
- Scan drill: matrix of devices (iOS/Android native cameras + major bank/wallet scanners) × sizes × error levels — minimum-size exports scan at arm's length; below-floor requests refused with the reason stated. *GWT: Given the device × size × error-level matrix; When minimum-size exports are scanned or below-floor exports are requested; Then minimum-size exports scan at arm's length and below-floor requests are refused with the reason stated.*
- Lifecycle drill: suspended/expired link's QR serves the same gone/suspended behavior as URL opens; no orphan resolves. *GWT: Given a suspended/expired link; When its QR is opened; Then it serves the same gone/suspended behavior as URL opens with no orphan resolves.*
- Contrast drill: below-floor palette requests rejected; all exported codes pass a contrast/scan-ability check in CI with reference images. *GWT: Given a below-floor palette request; When validation and CI checks run; Then the request is rejected and all exported codes pass the contrast/scan-ability check with reference images.*
- Bulk drill: 100-code event pack generates with unique per-location links, correct labels, and per-code stats separation. *GWT: Given a 100-code event-pack request; When generation completes; Then it has unique per-location links, correct labels, and per-code stats separation.*

## Out of scope

- Short-link minting, namespaces, and redirect semantics (see `links/short-links.md`); click analytics and fraud filtering (see `tracking/link-analytics.md`).
- Profile QR-vs-URL conventions (`docs/link-to-bio/` — QR equals the same URL there); payment QR payloads (provider majors own their VietQR/EMV payloads, never this generator); P4/P5.

## Open questions

- Q1: Brand-styled QRs (logos, colors) — allow above a strict contrast/size floor, or plain-only in v1 for scan reliability?
- Q2: Dynamic print sizes — user-declared intent (poster vs receipt) driving defaults, or manual size picker only?
