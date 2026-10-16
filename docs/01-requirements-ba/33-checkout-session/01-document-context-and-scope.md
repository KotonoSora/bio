# Checkout Session - Context and Scope (feature 33)

Phase 1 BA · Feature 33 of 53 — monetize 2/9. Legacy sources: `monetize/checkout/{checkout-session,provider-selection}.md` plus the `monetize-foundation` delivery plan's checkout-front-door slice (folded). Realized by `docs/02-architecture-techlead/33-checkout-session/`; kernel conventions from `docs/01-requirements-ba/32-monetize-overview/` (registry, no-default, lifecycle map) are consumed, never re-decided.

## Purpose

Define the single buyer journey from price intent to a tracked pending order — preview an exact quote, create exactly one order per intent under an explicit provider, expire it if unpaid, cancel it if abandoned, and return the buyer to one pending page that polls but never grants. Provider docs restate fragments of this today; this feature is the arbiter so double-submit, expiry, cancellation, and return behavior are identical on every rail. It also owns the selection surface: eight-member registry rendering, eligibility, and capability hints.

## Objectives

| # | Objective |
|---|---|
| O-1 | One buyer journey — quote → provider choice → create → handoff → pending/return — behaves identically on all eight rails. |
| O-2 | Quote equals charge to the VND: one server-side pricing path; client amounts are never trusted. |
| O-3 | Exactly one order per intent: keyed idempotency never expires; keyless double-clicks collapse within 90 s. |
| O-4 | Fail-closed validation and safe races: expiry, cancel, and grant resolve to exactly one terminal outcome; return pages and query params transition nothing. |
| O-5 | Explicit selection: the eligible list is computed server-side (registry ∩ config ∩ kill switches ∩ capability) and rendered verbatim. |

## Personas / RBAC

| Role | CRUD Permissions | Description |
|---|---|---|
| Buyer (session or guest for products/tips) | C own quotes/checkouts; R own order status; C cancel own pending | One consistent pending/return experience |
| Frontend checkout | none (client) | Quote without rows, create idempotently, poll one status shape, render the eligible list verbatim |
| Support/ops | R orders by `orderCode`; C cancel stuck pendings (actor audited) | Explains expiry and cancels without ambiguity |
| Attacker (adversarial) | none | Double-submits, tampers amounts, forges return URLs, races cancel against grant — gains nothing |

## In scope

Quote (side-effect-free, 15-min validity, `quoteToken`); create (explicit `provider`, eligibility matrix, re-price, fee snapshot, one `orders` row, handoff `next{url|qr|sheet|token}` via the provider-adapter interface); expiry (per-rail TTL, sweep, late-money refusal); cancel (owner-scoped, race-safe); return handling (display-only polling page); the selection surface (`GET /api/v1/billing/payment-methods`: eligible list, order, capability hints); guest product/tip checkout (email-mandatory, no account); the internal create path for shop's tip checkout; lead opt-in capture at checkout; same-origin return/cancel URL policy.

## Out of scope

Dispatch ordering and provider handoff mechanics (40); status model, legal edges, grant, and dispatch (34); ledger/plans/catalog (35); lifecycle jobs (36); receipts rendering and magic-link view (37); refunds (38); promos (39); probe battery (40). Plans require login; guest bookings do not exist (25).

## Open questions (resolved)

| Q | Resolution |
|---|---|
| Q1 pending TTL | Per-rail config with global default 30 min; bank-transfer rails (`sepay`, `napas`) 24 h (resolved in 32; consumed here). |
| Q2 keyless dedupe window | 90 s fingerprint window; keyed idempotency never expires (resolved in 32; consumed here). |
| Q3 guest product checkout | Enabled for products (incl. tips) with mandatory email; plans remain login-only. Guest rows carry a claim-bound `user_id` (`guest:` + HMAC pepper of the email), no account is created, and no claim/transfer machinery exists (25's precedent). History/receipt viewing for guests is the signed magic-link read owned by 37 — read-only, single-order. |
| Q4 capability detection (from provider-selection) | Server-side: UA class + `Sec-CH-UA` client hints where present; hints are advisory, the server is authoritative; the response carries `disabledReason` and the client renders only what the API returns. |
| Q5 quote-vs-create mismatch detection | `quoteToken` (stateless HMAC over intent, amount, catalog version, expiry) returned by quote; when present at create, a mismatch is 409 `amount_changed` and an expired token is 410 `quote_expired`; when absent, the server re-price is authoritative. |
| Q6 ship-to collection | Required when the product kind is `merch`; stored minimized (name, phone, address); never logged; carried through `order.granted.v1` (23's forward). |
