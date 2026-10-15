# Checkout Orchestration Database Plan (dispatch reads + idempotency — no new tables)

PLAN ref: P3-1 (one dispatcher, no-default, kill switches, one-order-one-provider, double-submit collapse) + S-1. BA source: `docs/monetize/payment/checkout-orchestration.md`. Consumed by the orchestration backend plan (the dispatcher; splits next to this file). `orders` rows live in the checkout-session database plan (next in queue — creation DDL there, never here); eligibility config + kill switches in the overview backend plan (ops config, versioned, audited). No migration — this slice creates zero tables.

## Purpose

Define the dispatcher's data contract without owning storage: which rows/config it reads, the idempotency contract that collapses double-submits, the provider-immutability rule, and guest-row ownership. No `CREATE TABLE` here by design — the dispatcher is stateless orchestration over rows owned elsewhere.

## Users

- Backend developer: implements the dispatcher against these read contracts + idempotency rules.
- QA: proves double-submit collapse, kill-switch propagation, and no-default behavior from data, not code reading.
- Ops: flips rails knowing exactly which rows in-flight orders follow.

## Functional requirements

1. Reads (all owned elsewhere, listed for completeness): `orders` (existence + status for dedupe/double-submit handling); plan/product catalogs + `plan_terms` (re-pricing source — client amounts never trusted); provider registry constant + eligibility matrix config + kill-switch state (overview backend plan); `subscriptions` current row (plan-change intent validation, read-only here).
2. Idempotency contract (the double-submit killer): create accepts `Idempotency-Key` header + server-computed fingerprint (user + plan/product + amount + provider + coupon). Same key + same fingerprint → re-issue the SAME handoff, zero new rows (lookup by stored key, not by re-creation). Same key + different fingerprint → `422` (key reuse across intents rejected — keys are intent-bound). Keyless double-click → fingerprint dedupe within a short window collapses to one row (window length per session Q2; behavior specified in the session slice). Keys stored on the `orders` row (`idempotency_key`, storage owned by the checkout-session database plan) with a uniqueness scope that makes the second insert impossible, not merely checked-against.
3. Provider immutability: `provider` set once at creation, never updated (no `UPDATE orders SET provider` path exists — switching methods mid-order is cancel-then-new-checkout, which keeps every rail's idempotency story intact). Immutability is a no-column-write rule asserted in review (grep for provider assignment outside creation), backed by the session slice's update whitelist.
4. Kill-switch data flow: switch state (config, overview plan) removes the rail from eligibility within seconds; in-flight `pending` orders of a killed rail follow that rail's settlement path (owned per rail minor) with buyer messaging — never silently dropped, never auto-migrated to another rail (migration would re-price and re-consent implicitly — forbidden).
5. Guest rows (policy hook, decided in session slice): IF enabled, guest product orders carry claim-bound `user_id` (no null-owner rows anywhere — RLS and joins assume non-null owners universally). This rule lives here because the dispatcher creates those rows; the policy decision lives in the session slice.
6. Uniform `next` shape (contract, not storage): `{kind: url|qr|sheet|token, payload}` — rail builders return exactly this; the dispatcher passes it through unmodified. A ninth rail fits without dispatcher changes (addition dry-run stays review-only proof).

## Data / API touches

- DDL: none — assertion: no `CREATE TABLE` for dispatch state anywhere under `docs/monetize/payment/` for this slice (review greps it). Consumed rows/config: `orders`, catalogs, eligibility config, kill switches, subscriptions current row (all owned elsewhere). Written rows: none directly (creation executes through the session slice's insert path — one writer, no split-brain creates).

## Acceptance criteria

- Matrix drill: each plan/product × provider cell allowed/rejected per config via API (client filtering alone fails).
- Double-submit drill: same key/fingerprint → one row + same handoff; cross-intent key reuse → 422; keyless double-click → one row.
- Immutability drill: no provider-update path exists (code grep + attempted-update test 422s or no-ops safely).
- Kill-switch drill: list exclusion within seconds; in-flight pendings routed with messaging; flip audited.
- Guest drill: zero null-owner order rows under any enabled policy (constraint-level proof when the session DDL lands).
- Violation drill: any dispatcher-owned table or client-decided eligibility fails review.

## Out of scope

- Creation/quote/expire/return logic (checkout-session plans); rail handoff/verify mechanics (provider minors); status edges (status-model plans); probe battery (security plans); ledger/lifecycle/receipts/catalog/coupons/refunds (lifecycle plans); P4/P5.

## Open questions

- Q1: Display-order sourcing (deferred to the overview decision — single answer propagates)?
- Q2: Guest checkout enablement (deferred to the session decision — single answer propagates)?
