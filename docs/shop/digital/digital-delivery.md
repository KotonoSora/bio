# Digital Delivery (downloads + paywalled content)

PLAN ref: P2-2 (digital/paywall = R2 signed URL after paid `orders{product_id}`). Upload validation follows the avatar-pipeline precedent (caps + magic-byte + random keys).

## Purpose

Sell files and gated content with one secure pattern: pay first, receive a personal, expiring download link; paywalled posts unlock in-place for the buyer. Links cannot be shared into free distribution, and creators can rotate access without re-uploading.

## Users

- Creator: *As a Creator, I want to upload files, set prices, and write free previews with gated full content so that files sell securely.* Uploads files, sets prices, writes free previews with gated full content.
- Buyer: *As a Buyer, I want to pay once, download within the access window, and re-access while entitled so that purchases stay available.* Pays once, downloads within the access window, re-accesses while entitled.
- Non-buyer: *As a Non-buyer, I want to see previews and price only so that full bytes never leak through preview surfaces.* Sees previews and price only — full bytes never leak through preview surfaces or CDN guessing.

## Functional requirements

1. Upload: files to R2 under server-generated random keys (never client-chosen); per-file cap (see Q1 for launch value) + magic-byte verification; blocked executables/scriptable types (allowlist per category: ebook/audio/preset/zip — deny `html/svg/js/exe` equivalents outright). Upload completes before product goes live (no dangling live products with missing files).
2. Purchase-gated delivery: paid order grant → entitlement row → buyer receives one-time signed URL (short TTL, single-use token bound to order + file version). Re-downloads within the access window (see §3) mint fresh URLs per request — URLs are never stored, bookmarked, or reused.
3. Access windows: default 30-day download access + 5 successful fetches (whichever first), both config-driven; creator may extend per buyer (audited courtesy action). Expired access shows re-purchase (or support path), never the file.
4. Paywalled content: long-form post/video page with `preview` (free, SSR-visible for SEO) + `locked` body (buyer-only, server-rendered only after entitlement check — locked bytes never ship in preview HTML/JSON). Works for one-time purchases; subscription-gated paywall is named v2 (owned by `monetize/subscription/` when it lands).
5. Versioning: creator re-upload creates a new file version; past buyers keep their purchased version's access (new version optional paid upgrade or free per creator flag). Version ids in entitlement rows make this exact.
6. Piracy hygiene: signed URLs bound to buyer session/IP-class where feasible without breaking mobile networks (over-strict binding that breaks legit phones is worse than sharing); watermark-by-buyer-email on PDFs where tooling allows (v2, named — not a launch promise).

## Data / API touches

- New tables: `digital_files{file_id, product_id, version, r2_key, size, mime_verified}` + `entitlements{order_id, product_id, file_version, buyer_key, downloads_used, expires_at}` (buyer = account or buyer-email claim per P2-3 pattern; RLS/owner scoping accordingly). All PG via `worker/db/*`; R2 keys random and unguessable.
- Endpoints: creator upload/finalize + version publish; buyer `POST /api/v1/digital/:productId/access-link` (entitlement-checked, mints signed URL, increments counter); paywall page SSR (entitlement check server-side before rendering locked body). No PG writes on the byte path (R2 signed-URL redirect only).
- Abuse: per-buyer issue-rate limits; counter-anomaly (many IPs, one entitlement) flags for review and pauses re-issue pending support.

## Acceptance criteria

- Grant drill: signed URL mints only after grant-confirmed payment; pre-payment and refunded-buyer requests get 403 with no URL. *GWT: Given grant-confirmed, pre-payment, or refunded-buyer state; When a signed URL is requested; Then only grant-confirmed mints a URL while pre-payment and refunded-buyer requests get 403 with no URL.*
- Expiry drill: lapsed/over-count entitlements refuse with the correct reason; courtesy extension restores exactly the granted window. *GWT: Given lapsed/over-count entitlements with a courtesy extension; When access is requested; Then lapsed/over-count requests refuse with the correct reason and extension restores exactly the granted window.*
- Leak drill: preview HTML/JSON contains zero locked bytes; R2 key guessing does not resolve (random keys, no listing); shared signed URL fails after single use / expiry. *GWT: Given preview HTML/JSON, guessed R2 keys, and shared signed URLs; When accessed; Then previews contain zero locked bytes, guessed keys do not resolve, and shared URLs fail after single use / expiry.*
- Version drill: re-upload preserves old-version buyer access; upgrade flag correctly charges or grants new version. *GWT: Given a creator re-upload with an upgrade flag; When old and new versions are accessed; Then old-version buyer access is preserved and the upgrade flag correctly charges or grants the new version.*
- Upload drill: oversized, fake-mime, and scriptable-type uploads rejected before R2 write. *GWT: Given oversized, fake-mime, or scriptable-type uploads; When validation runs; Then they are rejected before R2 write.*

## Out of scope

- Catalog/listing/pricing (see `products/product-catalog.md`); fees (see `pricing/sales-fees.md`); courses with progress (see `courses/course-builder.md`).
- Checkout, refunds, receipts (`monetize/payment/`, `monetize/billing/`); subscription-gated content (v2, with `monetize/subscription/`); email delivery of files (link-based only in v1).

## Open questions

- Q1: Per-file size cap at launch (e.g. 500 MB) and total storage quota per creator/tier?
- Q2: PDF buyer-email watermarking — build at launch or accept single-use-URL protection in v1?
