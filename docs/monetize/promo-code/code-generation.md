# Promo Code Generation (formats, batches, attribution)

PLAN ref: P3-3 (implements the code supply behind `promo-code/campaign-management.md` budgets and `subscription/trials-promotions.md` kinds). Redemption behavior stays in those docs.

## Purpose

Define how codes come into existence and reach buyers: unguessable formats, bulk batch generation, single- vs multi-use semantics, and channel attribution (influencer/partner codes). Predictable or colliding codes are a revenue hole; this doc makes every issued code unique, scoped, and traceable to its campaign and channel without ever exposing the generation scheme to buyers.

## Users

- Marketing/ops: generates a batch (10k influencer codes, 50 VIP single-use codes), exports for distribution, tracks channel performance.
- Partner/influencer: receives an attributable prefix batch to share with their audience.
- Buyer: receives a code that simply works — format details invisible.

## Functional requirements

1. Code format: `PREFIX-XXXXXX` (uppercase ASCII, ambiguous characters `0/O/1/I/L` excluded), cryptographically random suffix, minimum 64 bits of entropy; prefix encodes campaign + channel (e.g. `BTS26-`, `KOLMINH-`) for human routing, never the discount value. Full-code UNIQUE constraint; generation retries on the negligible collision.
2. Batch generation (audited ops action): `{campaign_id, channel?, count, use_limit (1 = single-use, N = multi-use), per_user_limit, expires_at ≤ campaign end}`. Generation is atomic per batch (all-or-nothing), stamped with actor + timestamp; the plaintext list is exportable exactly once at creation (download-and-acknowledge), after which only hashes are retrievable — leaked exports have bounded blast radius.
3. Single-use semantics: first successful grant consumes the code (status `redeemed`, bound to `user_id` + `order_id`); concurrent redemptions of the same code serialize — exactly one wins. Multi-use codes decrement campaign counters per redemption (campaign doc) and enforce per-user limits.
4. Channel attribution: partner/influencer batches carry `channel_ref`; redemptions stamp it onto orders for performance readout (redemptions, conversion to renewal — aggregates only, no buyer PII shared with partners). Partner dashboards, if any, show counts, never buyer identities.
5. Storage discipline: codes stored as salted hashes for validation (`hash(code)` lookup — timing-safe compare); plaintext exists only in the one-time export. Validation endpoint behavior from the trials doc (throttled, indistinguishable rejections) applies unchanged.
6. Bulk void: campaign pause/leak response voids unredeemed codes by batch or campaign in one audited action; redeemed codes are never voided retroactively (issued discounts stand).

## Data / API touches

- `coupons{code_hash UNIQUE, campaign_id FK, channel_ref?, use_limit, uses, per_user_limit, status (active|redeemed|voided|expired), expires_at}` with restricted-write RLS (buyers validate only via the throttled endpoint; no direct reads). Batch metadata in `coupon_batches{id, campaign_id, channel_ref?, count, created_by, created_at}`. All PG via `worker/db/*`.
- Ops endpoints: `POST /api/v1/ops/coupon-batches` (generate + one-time export), `POST /api/v1/ops/coupon-batches/:id/void-unredeemed`, `GET /api/v1/ops/coupon-batches/:id/stats`. Export payload encrypted in transit; download event audited.
- Validation path reuses the existing throttled validate endpoint (hash lookup, constant-time compare); no new buyer-facing surface.

## Acceptance criteria

- Entropy/format drill: generated batches meet the charset/entropy rule; zero collisions across 100k-code stress batch; ambiguous characters absent.
- Single-use race drill: 10 concurrent redemptions of one code — exactly one grant, nine clean rejections.
- Export drill: plaintext available once, hashes-only afterward; export event in audit with actor + batch size.
- Void drill: bulk-void flips only unredeemed codes; redeemed grants and their discounts stand untouched.
- Partner-readout drill: channel aggregates correct with zero buyer PII in partner-visible output.
- Auth probe: non-ops generation/export/void endpoints → 403; buyers cannot list or enumerate codes (no oracle via timing or error text).

## Out of scope

- Campaign budgets, targeting, and lifecycle (see `promo-code/campaign-management.md`); stacking and redemption UX (see `promo-code/redemption-rules.md`).
- Coupon kinds and repricing math (`subscription/trials-promotions.md`); catalog versioning (`subscription/plan-catalog.md`); partner payout/revenue-share mechanics (separate finance major, P4 at earliest); P5.

## Open questions

- Q1: Code alphabet and prefix registry — central prefix reservation to avoid channel collisions, or free-form per campaign?
- Q2: QR/deep-link distribution (code embedded in a signup URL) in v1, or plain-code entry only?
