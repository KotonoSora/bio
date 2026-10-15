# Plan Catalog (versioned plan, term, and price definitions)

PLAN ref: P3-3 (gives the plans matrix in `plans/subscription-plans.md` a versioned, priced, admin-manageable backing store). Buyer-facing matrix stays in `plans/`; entitlement rows in `billing/entitlement-ledger.md`.

## Purpose

Define plans as versioned data instead of scattered constants: every plan, term, price, and AI quota the checkout prices from, the gates enforce, and the ledger records. Today the matrix describes WHAT pro unlocks but no spec owns the price/term rows checkout re-prices against — so price changes, yearly terms, and grandfathering have nowhere to land. This doc is that backing store plus its change discipline.

## Users

- Buyer: sees stable prices at checkout that always match what they are charged.
- Ops/admin: introduces prices, terms, and quota changes without code deploys or breaking in-flight checkouts.
- Checkout/grant/lifecycle code: prices from one catalog version per order, forever auditable.

## Functional requirements

1. Catalog tables: `plans{id, code UNIQUE (free|pro), name, features_json}` + `plan_terms{id, plan_code, term (monthly|yearly), amount_vnd INT, ai_credits, version INT, status (draft|active|retired)}`. Checkout re-prices exclusively from the `active` term row — the amount sent by the client is never trusted (see `payment/payment-security.md`).
2. Versioning and grandfathering: prices/quotas change by publishing a new `version` row and retiring the old one — never by editing in place. In-flight `pending` orders keep their captured version; active subscriptions keep their term until renewal (renewal always prices from the then-`active` version, with the reminder emails stating the new amount — no silent price rises).
3. Yearly terms (decides ledger-doc Q1 — yes, monthly + yearly): yearly = 12× monthly minus a configured discount, single upfront charge, same grant/lifecycle machinery with `period_end` at +12 months. No other terms in v1.
4. AI quota coupling: each term row carries its `ai_credits`; mid-term catalog changes never alter already-granted quotas (credits are stamped at grant from the purchased version).
5. Admin operations (restricted role, audited): draft → activate → retire lifecycle for term rows; activation blocked while another `active` row exists for the same plan+term; retirement blocked while `pending` orders reference the version (drain first). Every change appends `audit_log`.
6. Free tier is a catalog row too (`free`, amount 0), so signup, downgrade targets, and gate defaults all resolve through the same lookup — no hardcoded free anywhere.

## Data / API touches

- New tables `plans` + `plan_terms` (schemas above) with standard RLS posture (public-readable `active` rows for pricing display; writes restricted to admin role). `orders` stamps `plan_term_version` at creation; `subscriptions` stamps the purchased version at grant.
- Endpoints: `GET /api/v1/plans` (public catalog for pricing/checkout display), admin `POST /api/v1/ops/plans/terms` (draft), `POST /api/v1/ops/plans/terms/:id/activate|retire`. All PG via `worker/db/*`.
- Checkout display and charge path read the same `active` row in the same request scope — displayed price and charged price cannot diverge.

## Acceptance criteria

- Price-change drill: publish v2, retire v1 — in-flight pendings charge v1, new checkouts show and charge v2, renewals quote v2 in reminder emails; zero mismatches.
- Activation-guard drill: second `active` row for the same plan+term rejected; retire-with-pendings rejected until drained.
- Downgrade-target drill: free row resolves as the universal fallback in lifecycle and gate code with no hardcoded constants (grep gate).
- Yearly drill: single charge, `period_end` +12 months, reminders/ledger/AI quotas all follow the stamped version.
- Auth probe: non-admin catalog writes → 403; public catalog readable without session; RLS denies cross-scope rows.

## Out of scope

- Buyer-facing matrix and gate enforcement (`plans/subscription-plans.md`); ledger writes and lifecycle transitions (`billing/`); trials/coupons (see `subscription/trials-promotions.md`); recurring mechanics (see `subscription/recurring-billing.md`).
- Provider checkout/settlement internals (their own minors); P4/P5.

## Open questions

- Q1: Yearly discount depth — fixed percent off monthly×12, or per-campaign pricing?
- Q2: Currency of the catalog — VND-only rows in v1, or multi-currency rows ahead of Adaptive Pricing?
