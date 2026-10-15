# Order Management (unified ops console)

PLAN ref: none — new scope beyond PLAN P0–P5, flagged (see `sales/sales-ledger.md` for placement). Provider ops endpoints exist per major; this doc unifies them into one console with one audit trail.

## Purpose

Give support and ops one place to find any order, see its complete money trail, and take every legal action on it: the cross-provider console that replaces eight provider consoles, chat-memory archaeology, and database spelunking. Each provider major defines its own verify/refund/lookup endpoints, but a buyer asking "where is my money" should never require knowing which rail they paid on — lookup starts from orderCode, buyer email, or provider reference, and the console assembles the rest.

## Users

- Support agent: *As a Support agent, I want to find orders, explain states in plain language, trigger buyer-safe actions (resend receipt, re-drive verification, escalate) so that support and ops have one place to find any order, see its complete money trail, and take every legal action as defined here.* finds orders, explains states in plain language, triggers buyer-safe actions (resend receipt, re-drive verification, escalate).
- Ops/finance operator: *As an Ops/finance operator, I want to execute restricted actions (cancel, link exception, refund, subscription fix) with dual-control where defined so that support and ops have one place to find any order, see its complete money trail, and take every legal action as defined here.* executes restricted actions (cancel, link exception, refund, subscription fix) with dual-control where defined.
- Buyer (indirect): *As a Buyer, I want to get faster, consistent answers because every agent sees the same trail so that support and ops have one place to find any order, see its complete money trail, and take every legal action as defined here.* gets faster, consistent answers because every agent sees the same trail.

## Functional requirements

1. Universal lookup: single search across `orderCode`, buyer email/account, `napas_order_ref`/`app_trans_id`/`vnp_TxnRef`/`zp_trans_id`/PaymentIntent id, and receipt/credit-note ids — one result view assembling order row, canonical status (see `payment/order-status-model.md`), provider events, sales lines + adjustments, receipts, subscription/entitlement effects, and affiliate/creator attributions where present. Nothing requires knowing the provider first.
2. Action catalog (role-gated, all audited with actor + reason): support tier — resend receipt, re-drive status verify (routes to the right provider query endpoint), escalate to ops case; ops tier — cancel unpaid, link rail exception, initiate refund (routes to provider settlement refund), subscription period fix, campaign pause (routes to promo kill path). Each action shows its preconditions (e.g. refund only from grant-confirmed states) and refuses ineligible orders with the reason stated.
3. Case management (lightweight, attached to orders): `support_cases{order_id?, reporter, status (open|investigating|waiting_buyer|resolved), notes[]}` with status transitions audited and buyer-visible status mirroring (buyer sees "under review", never internal notes). Cases link exceptions, disputes, and missing-payout claims into one thread instead of scattered chat.
4. Dual-control on money-moving actions above a threshold (see Q1): refunds and manual grants require a second approver; the console enforces maker-checker (proposer cannot approve own action) with expiry on pending approvals.
5. Plain-language state explanations: every canonical status maps to a buyer-safe sentence + next-step guidance for agents (no provider-native codes quoted to buyers); the console suggests the correct next action per state (e.g. `confirming` → verify-then-wait, `disputed` → evidence pack).
6. Safety rails: all actions re-validate order state at execution (the order may have changed since page load — stale-view actions fail closed); bulk actions operate per-order with per-item results (no silent partial batches); every destructive or money-moving path has a dry-run/preview showing exact effects before confirm.

## Data / API touches

- New table `support_cases` (+ notes append-only) with standard RLS posture (support/ops roles; buyers see only their own case statuses). All other data read from existing tables across minors (orders, ledger, receipts, exceptions, payouts) — the console adds no financial state of its own. All PG via `worker/db/*`.
- Endpoints (role-scoped, support vs ops tiers): `GET /api/v1/ops/orders/search`, `GET /api/v1/ops/orders/:orderCode` (assembled trail), action endpoints per the catalog (each delegating to the owning minor's endpoint — the console orchestrates, never reimplements), `POST /api/v1/ops/cases` + transitions. Dual-control approvals as first-class records with expiry.
- Read-model performance: assembled trail served from indexed lookups with a strict latency budget (see Q2); heavy export/aggregations stay in the analytics endpoints.

## Acceptance criteria

- Lookup drill: same order found via all five identifier kinds with identical assembled trails; unknown identifiers return clean no-result (no cross-order leakage).
  *GWT: Given Lookup drill context; When same order found via all five identifier kinds with identical assembled trails; Then unknown identifiers return clean no-result (no cross-order leakage).*
- Action drill per role: support tier cannot reach ops-tier actions (403 with reason); ineligible-state actions refused with stated preconditions; stale-view execution fails closed.
  *GWT: Given Action drill per role context; When support tier cannot reach ops-tier actions (403 with reason); Then stale-view execution fails closed.*
- Dual-control drill: above-threshold refund requires a different approver; self-approval impossible; expired approvals cannot execute.
  *GWT: Given Dual-control drill context; When above-threshold refund requires a different approver; Then expired approvals cannot execute.*
- Case drill: exception → case → buyer-visible "under review" → resolution with full audit chain; internal notes never exposed.
  *GWT: Given Case drill: exception; When the transition executes; Then case → buyer-visible "under review" → resolution with full audit chain; internal notes never exposed.*
- Dry-run drill: every money-moving action previews exact effects; previewed-equals-executed verified on samples.
  *GWT: Given Dry-run drill context; When every money-moving action previews exact effects; Then previewed-equals-executed verified on samples.*
- Auth probe: buyers on ops endpoints → 403; support agents on finance-only actions → 403; RLS denies cross-scope rows.
  *GWT: Given Auth probe: buyers on ops endpoints; When the transition executes; Then 403; support agents on finance-only actions → 403; RLS denies cross-scope rows.*

## Out of scope

- Provider verify/refund mechanics being orchestrated (their own minors); canonical statuses (`payment/order-status-model.md`); ledger/receipts/attributions being displayed (`sales/sales-ledger.md`, `billing/`, `affiliate/`, `advertising/`).
- Automated dunning/retries (lifecycle + settlement docs); accounting reports; P4/P5.

## Open questions

- Q1: Dual-control threshold — amount floor for maker-checker on refunds/manual grants?
- Q2: Trail latency budget — assembled view SLA under load (p95 target)?
