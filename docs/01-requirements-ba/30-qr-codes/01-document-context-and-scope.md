# QR Codes - Context and Scope (feature 30)

Phase 1 BA · Feature 30 of 53 — url-shortener 2/3. Legacy sources: `url-shortener/links/qr-codes.md` plus its database/backend/frontend plans (folded; deleted with this batch). Realized by `docs/02-architecture-techlead/30-qr-codes/`; a module of `link-service` (the short-link owner). Short-link truth stays in feature 29: `docs/01-requirements-ba/29-short-links/`; scan counting in feature 31: `docs/01-requirements-ba/31-short-link-analytics/`.

## Purpose

One generator makes every QR in the product: codes encode the short URL (never the destination), inherit the link's lifecycle automatically, and print/scan reliably across sizes and damage levels. Scattered generators (each feature rolling its own encoder/error-correction guess) produce unscannable print runs and orphan codes pointing at dead targets — one generator with fixed quality floors prevents both. A failed export beats a failed print run.

## Objectives

| # | Objective |
|---|---|
| O-1 | Encode-short-only: the payload is always `https://<host>/go/<code>`; destination edits, expiry, and suspension keep working after print. |
| O-2 | Non-negotiable quality floors: error-correction minimums, baked-in quiet zones, minimum module size per declared print size, high-contrast palette only — the generator refuses before rendering when scan-ability can't be guaranteed. |
| O-3 | Print-ready outputs: PNG (declared DPI) + SVG vector + multi-up label sheets; every export carries a human label (code + destination preview). |
| O-4 | Structural lifecycle inheritance: no QR lifecycle state exists; pixels don't change, resolution does; reprints always re-render from the live link record. |
| O-5 | No orphan generation: minting requires an existing live short link; suspended/expired parents refuse generation and old prints follow the current link state. |

## Personas / RBAC

| Role | CRUD Permissions | Description |
|---|---|---|
| Creator/operator | C own renders; R own render history | downloads a print-ready QR for a short link, picks size/format, reprints confidently |
| Scanner (any camera/bank/wallet app) | none | points, lands on the short URL, follows the normal redirect + counting path |
| Ops | C packs (audited) | runs labeled event packs (per-table/per-booth codes) for per-location measurement |
| Print vendor (indirect) | none | receives files with quiet zones and contrast that survive real printing |

## In scope

QR payload rule + live-parent precondition; render log (`qr_codes`, subordinate to `short_links`); quality floors (error level, quiet zone, module size, contrast) enforced server-side; PNG/SVG exports at declared sizes; label companion; multi-up print sheet (browser-print SVG — no PDF artifact); re-render/reprint flow; staleness flag data; ops bulk packs (atomic, labeled, ≤ 100); browser preview watermarking; quotas + audit; scan-vs-click handoff to feature 31.

## Out of scope

Short-link minting/namespaces/redirect semantics (feature 29); hit counting, filters, scan-split statistics (feature 31); profile QR conventions (feature 10 — the profile share QR encodes the profile URL, not a short link); payment QR payloads (provider majors own their VietQR/EMV payloads — never this generator); brand-styled QRs (deferred, Q1); custom short domains (named future work).

## Open questions (resolved)

| Q | Resolution |
|---|---|
| Q1 brand-styled QRs | Plain-only in v1 (dark modules on light background above a strict contrast floor); logos/colors deferred with a recorded trigger (post-launch scan-failure data shows floor-safe styling demand). The contrast floor ships regardless — it is what future styling must respect. |
| Q2 print-size intent presets | Manual size picker only, with physical-size captions (S ≈ 2 cm receipt, M ≈ 4 cm arm's length, L ≈ 8 cm, poster ≥ A4) and per-class floor defaults; no intent inference (poster-vs-receipt guessing fails safely only by refusing). |
| Q3 execution plans folded | Database/backend/frontend plans folded into this feature's TL. DEVIATION CARRIED (feature 29 boundary map): the legacy monolith package `com.app.links.qr` maps to the `link-service` QR module; the one flagged dependency is `com.google.zxing` (pure-Java, zero native deps, container-safe, grep-gated); the print sheet renders as a browser-print SVG, never a server PDF (platform PDF guardrail). |
