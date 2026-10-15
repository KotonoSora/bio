# POD Fulfillment (AI merch designs, no inventory)

PLAN ref: P2-2 (merch = POD listing, no inventory). AI design generation is new scope beyond PLAN (only P3-3 JD-tailor AI exists) — flagged, needs a plan update; design-complete only until then. Fulfillment-partner selection is pending (see Q1); this doc specs the adapter interface, not a vendor.

## Purpose

Let creators sell merch with zero inventory: AI-assisted designs applied to print templates, buyer orders routed to a print-on-demand partner, partner prints and ships, creator keeps the margin. The platform holds no stock, prints nothing, and ships nothing — it holds the design files, the order routing, and the status trail.

## Users

- Creator: *As a Creator, I want to generate/pick a design, apply it to garments, set profit, and publish so that merch sells with zero inventory.* Generates/picks a design, applies it to garments, sets profit via the calculator (see `merch/profit-calculator.md`), publishes.
- Buyer: *As a Buyer, I want to order a size/variant with ship-to collected at checkout so that print-to-delivery status stays trackable.* Orders a size/variant, gets ship-to address collected at checkout, tracks print-to-delivery status.
- Fulfillment partner (integration): *As a Fulfillment partner, I want to receive print jobs, report status, and bill base costs so that print and ship execute.* Receives print jobs, reports status, bills base costs.

## Functional requirements

1. Design flow: creator uploads artwork (same validated-upload pipeline: size caps, magic-byte, R2 random key) or generates via AI (prompt → options → pick; AI draws from the shared `ai_generations` credit pool per the design-assist pattern; outputs are print-file candidates only, never auto-published). Design files versioned; published products pin a version.
2. Product templates (closed v1 set): tee, hoodie, mug, tote — each with printable-area spec, size/variant matrix, and base-cost table per partner. Creator picks template + variants offered; per-variant availability is partner-driven (disabled when the partner reports stockout of blank stock).
3. Order routing: paid merch order → print job `{order_id, design_file_ref, template, variant, quantity, ship_to}` dispatched to the partner adapter exactly once (idempotent job key = orderCode + line). No print dispatch before grant-confirmed payment (see `monetize/payment/order-status-model.md`); refunds before print cancel the job, after print follow the partner's reprint policy (see §5).
4. Status trail: `pending-print|printing|shipped|delivered|failed` synced from partner callbacks (verified-webhook discipline: signature, idempotency, no direct DB writes from callbacks — queue-mediated like all money paths). Buyer sees status on the order page; creator sees the job queue.
5. Reprint/misprint policy: partner-caused defects trigger reprint jobs (linked to the original, cost flagged non-billable to creator); buyer-address errors are buyer-responsible per published policy; policy text is part of the shop's terms surface before launch.
6. No-inventory invariant: the platform stores no blank-stock counts and promises no ship dates beyond partner SLAs; every date shown is labeled partner-provided with the partner named.

## Data / API touches

- New tables: `merch_designs{design_id, profile_id, version, file_r2_key, source (upload|ai), prompt_ref?}` + `print_jobs{job_id, order_id UNIQUE, design_version, template, variant, status, partner_ref, cost_base_vnd}` (standard `user_id` + RLS; job rows immutable except status transitions, all audited). All PG via `worker/db/*`.
- Endpoints: design CRUD + `POST /api/v1/merch/designs/generate` (credit-checked, quarantined until partner selected); `POST /api/v1/merch/jobs` (internal, paid-order hook); partner callback `POST /api/merch/partner-callback` (verified, queued); buyer/creator status reads (owner-scoped).
- Partner adapter interface (code contract, vendor-neutral): `createJob`, `cancelJob`, `getStatus`, `verifyCallback` — exactly one adapter implementation ships once the partner is chosen; no vendor SDKs enter the codebase before then.

## Acceptance criteria

- No-dispatch drill: unpaid/cancelled merch orders create zero print jobs (job table empty; partner mock receives nothing). *GWT: Given unpaid/cancelled merch orders; When the dispatch hook runs; Then zero print jobs are created with the partner mock receiving nothing.*
- Idempotency drill: duplicate paid-webhook delivery dispatches exactly one job (unique job key holds under replay). *GWT: Given a duplicate paid-webhook delivery; When dispatch runs; Then exactly one job is dispatched with the unique job key holding under replay.*
- Status drill: partner callback sequence advances buyer-visible status end-to-end; tampered callback rejected (401) with no state change. *GWT: Given a partner callback sequence including a tampered callback; When callbacks arrive; Then valid callbacks advance buyer-visible status end-to-end and tampered callbacks are rejected (401) with no state change.*
- AI-gating drill: generation endpoint 403s while the AI scope is unapproved, or debits shared credits once approved — one behavior, decided at plan update, never both/neither. *GWT: Given the AI scope unapproved or approved; When the generation endpoint is called; Then it 403s while unapproved or debits shared credits once approved, per the plan-update decision.*
- Upload drill: non-image, oversized, and SVG/HTML artwork rejected before R2 write. *GWT: Given non-image, oversized, or SVG/HTML artwork; When upload validation runs; Then it is rejected before R2 write.*

## Out of scope

- Price/profit math (see `merch/profit-calculator.md`); catalog/listing (see `products/product-catalog.md`); fees (see `pricing/sales-fees.md`).
- Checkout, refunds, receipts (`monetize/payment/`, `monetize/billing/`); digital/course/tip/consultation fulfillment (their docs); shipping-rate shopping beyond partner SLAs (v2).

## Open questions

- Q1: Fulfillment partner choice (VN print-ship vendor with API + webhook callbacks) — selection criteria and SLA floor?
- Q2: AI design scope — generator built in-house on the shared credit pool, or partner-supplied design tool embedded?
