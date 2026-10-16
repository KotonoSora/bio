# QR Codes - User Stories and Business Rules (feature 30)

Phase 1 BA · Realized by `docs/02-architecture-techlead/30-qr-codes/` (BR-01..BR-12). Short-link truth and lifecycle states are feature 29's contract, consumed never redefined: `docs/01-requirements-ba/29-short-links/02-user-stories-and-business-rules.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Creator/operator | download a print-ready QR for a short link with size/format choice | reprints still scan reliably | Must |
| US-02 | Scanner | point any camera at the code and land on the short URL | the normal redirect + counting path follows | Must |
| US-03 | Creator | re-render after editing the destination | reprints carry the current target | Must |
| US-04 | Ops | generate a labeled per-location pack for an event | per-booth measurement works | Should |
| US-05 | Print vendor (indirect) | receive files with quiet zones and contrast | real printing survives | Must |

## Business rules

- BR-01 Encode-short-only: the QR payload is always the canonical short URL (`https://<host>/go/<code>`), never the destination; minting a QR requires an existing `live` short link; the render log references the code (`short_code` FK; tombstoned links keep their renders — cascade only for the exceptional purge).
- BR-02 Quality floors (server-enforced, both UI and server layers, non-negotiable): error-correction level M minimum, H required for small (receipt) and poster classes; quiet-zone margin ≥ 4 modules baked into every export; minimum module size 0.4 mm at the declared print size; contrast ratio ≥ 7:1, dark-on-light palette only. Below-floor requests are refused before rendering (a failed export beats a failed print run) with the floor named.
- BR-03 Formats: PNG raster at declared DPI (300 default) + SVG vector + multi-up print sheet (browser-print SVG; no server PDF exists); every export ships with a companion label (short code + destination preview text) so humans file by label, not pixels.
- BR-04 Lifecycle inheritance (structural): no lifecycle columns exist on renders — expiry/suspension live on the parent link and resolve at scan time; a suspended/expired link's QR serves the same gone/suspended behavior as URL opens; pixels never change, resolution does.
- BR-05 Reprints: re-render reads the live link record (never a cached image); a reprint after a destination edit carries the current target; the UI flags downloads whose render timestamp predates the link's last update (`May be outdated — re-render`).
- BR-06 Scan path: QR scans land on the normal short-URL resolve path (cached redirect, queue-logged hit) — no QR-specific server path, no extra latency budget beyond the redirect SLA.
- BR-07 Bulk packs (ops only, audited): count ≤ 100 per pack; one distinct short link per label (per-location measurement); all-or-nothing (partial packs impossible — 73 of 100 never exists); labels required; pack reference recorded; per-code stats separation via feature 31's scan split.
- BR-08 Scan-vs-click signal: event-pack location codes give exact splits (attribution by code); organic scan-share is feature 31's heuristic, labeled estimated there — this feature only guarantees the code-level attribution input.
- BR-09 Preview honesty: no client-side generation of official codes; browser preview rendering is allowed only watermarked `PREVIEW — not for print` (enforced by overlay, not by trust); downloads always come from the server render log.
- BR-10 Generation abuse: session + CSRF on owner renders; ops-only packs (creator token → 403 + audit); per-user/per-ops quotas; Turnstile-class challenge on generate/bulk; secrets env/vault only.
- BR-11 Subordinateship: the render log is a log, never truth (truth stays in `short_links`); reprints always re-render from the live record; the log exists for history, audit, and pack assembly.
- BR-12 Purge: account deletion removes render rows (by `user_id`) and render objects; raw scan evidence is feature 31's domain (hashed only, no identity to purge).
