# Tip Jar (Buy-Me-a-Coffee alternative)

PLAN ref: P2-2/P2-3 (tips = fixed-amount product; P1a-3 free plan includes tips). Simplest money path in the shop — no fulfillment, no delivery, no inventory.

## Purpose

Let fans send money with two taps: preset amounts plus custom entry, an optional message, and a public or anonymous thank-you. Tips are pure goodwill revenue — no goods change hands, so the entire spec is checkout + gratitude, with no fulfillment tail to maintain.

## Users

- Fan: *As a Fan, I want to tip a creator with presets or custom entry and an optional message so that support takes two taps.* Tips a creator (account or guest with email receipt), optionally with a message.
- Creator: *As a Creator, I want to configure presets, read messages, thank tippers, and see tip revenue so that goodwill converts to earnings.* Configures presets, reads messages, thanks tippers; sees tip revenue in the earnings dashboard.
- Moderator: *As a Moderator, I want to screen tip messages before any public display so that the thank-you wall stays safe.* Screens tip messages before any public display.

## Functional requirements

1. Tip product: every profile may enable one tip jar (a `tip`-kind product per `products/product-catalog.md`); presets (e.g. 10k/20k/50k VND) + custom amount within floor/ceiling (floor = rail-minimum-viable, e.g. 10k VND — see Q1; ceiling anti-fraud cap). Amounts are VND integers; one-tap preset checkout.
2. Checkout: same PayOS-first order path as all shop money (numeric orderCode, idempotent grant); guest tipping allowed with email for receipt (receipt via existing receipt pipeline); tip orders settle instantly with no fulfillment hook and no delivery email beyond the receipt.
3. Messages: optional 280-char message + display-name choice (named/anonymous); messages held for creator approval before any public thank-you wall render; links and markup stripped (plain text only). Rejected messages stay private to creator + tipper.
4. Thank-you surfaces: creator dashboard tip feed; optional public supporters list (approved messages only, tipper-chosen names); tipper gets on-page thanks + receipt. No auto-posting to social (P4 automation territory, never stubbed here).
5. Recurring tips (membership-style monthly support): named v2 — v1 is one-time only, and the UI makes no recurring promise. Any recurring stub would collide with `monetize/subscription/` ownership.
6. Disputes: tips are final by policy (no digital-goods defect possible); unauthorized-payment claims follow the standard provider dispute path in `monetize/payment/`, never a custom flow.

## Data / API touches

- No new product-side tables (uses `products` with `kind=tip` + `payload_json{presets[], floor, ceiling, wall_enabled}`); new table `tip_messages{tip_order_id UNIQUE, display_name?, message, status (pending|approved|rejected)}` (owner-moderated; public reads only approved rows). Standard `user_id` + RLS throughout; all PG via `worker/db/*`.
- Endpoints: jar config CRUD (owner), `POST /api/billing/checkout` with tip payload (guest-capable, Turnstile + rate-limited), message approve/reject (owner), public supporters list (approved-only, cacheable). Money movement exclusively through the standard checkout/webhook path.
- Analytics: tip revenue flows to the earnings dashboard (`link-to-bio/analytics/earnings-dashboard.md`) as its own strip once the capability flag exists.

## Acceptance criteria

- Floor/ceiling drill: below-floor and above-ceiling amounts rejected 422 with the limits stated; presets all settle end-to-end in sandbox. *GWT: Given below-floor, above-ceiling, and preset amounts; When checkout runs; Then out-of-range amounts are rejected 422 with limits stated and presets settle end-to-end in sandbox.*
- Guest drill: guest tip with email delivers receipt; without email blocked (receipt-required) with a clear message. *GWT: Given a guest tip with or without email; When checkout completes; Then with email a receipt is delivered and without email it is blocked with a clear message.*
- Message drill: pending message invisible publicly; approved renders as plain text (link/markup stripped); rejected stays private. *GWT: Given a pending, approved, or rejected tip message; When the public wall renders; Then pending stays invisible publicly, approved renders as plain text with links/markup stripped, and rejected stays private.*
- Finality drill: refund endpoint refuses tip orders with policy reason (except unauthorized-payment dispute path, which routes to the provider flow). *GWT: Given a tip-order refund request; When the refund endpoint runs; Then it refuses with the policy reason except unauthorized-payment claims, which route to the provider flow.*
- Abuse drill: rapid-fire tipping throttled (Turnstile + WAF/rate rules); IDOR on jar config returns 403. *GWT: Given rapid-fire tipping or user B jar config; When attempted; Then rapid-fire is throttled and IDOR returns 403.*

## Out of scope

- Catalog mechanics (see `products/product-catalog.md`); fees on tips (see `pricing/sales-fees.md` — tips carry the same schedule, no carve-outs).
- Recurring memberships (v2 with `monetize/subscription/`); paid consultations (see `services/paid-consultations.md`); campaigns and social auto-posting (P4).

## Open questions

- Q1: Tip floor/ceiling values (rail-cost viability vs generosity friction) — launch numbers?
- Q2: Public supporters wall default — on or off at jar creation (social-proof vs privacy default)?
