# Shop Fulfillment - User Stories and Business Rules (feature 23)

Phase 1 BA · Realized by `docs/02-architecture-techlead/23-shop-fulfillment/` (BR-01..BR-16).

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Creator | upload and version artwork, pin a design version, and offer templates/variants | merch sells with zero inventory | Must |
| US-02 | Buyer | order a size/variant with ship-to collected at checkout and track print-to-delivery | I know where my order is | Must |
| US-03 | Creator | upload digital files, price them, and write paywalled posts with free previews | files sell securely | Must |
| US-04 | Buyer | pay once, download within the access window, and re-access while entitled | purchases stay available | Must |
| US-05 | Non-buyer | see previews and price only | full bytes never leak through preview surfaces | Must |
| US-06 | Creator | extend a buyer's access as a courtesy | support resolves edge cases | Should |

## Business rules

- BR-01 Design uploads: ≤ 20 MB, PNG/JPEG/WebP/PDF via magic-byte (no SVG/HTML), random file names; designs are versioned; a merch product pins `{designId, designVersion}` in its payload (feature 22 registry).
- BR-02 Templates (closed v1): `tee|hoodie|mug|tote`, each with printable area, size/variant matrix, and a partner base-cost table; creator picks offered variants; partner-reported stockouts disable variants (no counts stored).
- BR-03 Dispatch gate: a print job is created only after grant-confirmed payment; job key `(order_id, order_line)` unique — duplicate grant deliveries dispatch exactly one job; unpaid/cancelled orders create zero jobs.
- BR-04 Status trail: `pending_print → printing → shipped → delivered | failed`; `cancelled` only from `pending_print` on a pre-print refund; callbacks are signature-verified, deduped, queue-mediated — never direct DB writes; buyer sees status on the order page (34), creator on the job queue.
- BR-05 Reprint policy: partner-caused defects produce a linked reprint job flagged non-billable; buyer-address errors are buyer-responsible; the policy text ships on the terms surface before launch.
- BR-06 No-inventory invariant: no blank-stock counts; every date shown is labeled partner-provided with the partner named.
- BR-07 Digital uploads: allowlist ebook/audio/preset/zip; deny `html/svg/js/exe` equivalents outright; ≤ 500 MB; magic-byte verified; upload completes and a version is published before a digital/paywall product may go live.
- BR-08 Purchase-gated delivery: grant → entitlement row → per-request single-use download grant (TTL ≤ 15 min) bound to order + file version + buyer; grants are never stored, bookmarked, or reused; no PG writes on the byte path.
- BR-09 Access windows: 30 days + 5 successful fetches (whichever first), config-driven; a creator courtesy extension (audited) restores exactly the granted window; expired access shows re-purchase or support, never the file.
- BR-10 Paywall: preview renders in the SSR shell (free, indexable, cacheable, zero locked bytes in HTML/JSON); the locked body is served only by an entitlement-checked session fragment; locked bytes never ship in preview responses.
- BR-11 Versioning: a re-upload creates a new version; past buyers keep access to their purchased version; the new version is `free | paid` per version (paid = normal repurchase flow).
- BR-12 Piracy hygiene: no hard IP binding in v1 (mobile networks); many-IPs-one-entitlement anomalies flag the entitlement and pause re-issue pending support; watermarking is v2.
- BR-13 Refunds: a refunded or chargeback-reversed order's entitlement is revoked (re-grant refused; repurchase = a new order); pre-print refunds cancel the job; post-print refunds follow the partner reprint policy.
- BR-14 Rate limits: access-link minting is per-buyer limited; partner callbacks are rate-limited and deduped; anomalies flag, never auto-serve.
- BR-15 Audit: dispatch, status transitions, extensions, revocations, and design/file version publishes are audited (ids, never content).
- BR-16 PII: ship-to is minimized to fulfillment fields at checkout; a buyer's account deletion scrubs their ship-to and buyer id from job rows; the partner receives fulfillment fields only.
