# Sales Ledger (canonical revenue facts)

PLAN ref: none — new scope beyond PLAN P0–P5, flagged explicitly. Proposed as P3-ops (ships with the first provider); needs a plan update before build. Distinct from `orders` (stateful lifecycle in `payment/order-status-model.md`), buyer receipts (`billing/invoices-receipts.md`), and payout liabilities (affiliate/creator ledgers): this is the platform's gross-revenue fact table.

## Purpose

Define the single append-only record of what was sold, for how much, and what happened to the money afterward: every paid order projected once into sales facts, every refund/dispute netted against its original line. Provider `orders` rows mutate through statuses, receipts prove purchases to buyers, and payout ledgers track what we owe others — none answers "what did we earn, net, on Tuesday" without fragile re-derivation. This ledger is that answer, written once per financial event and never edited.

## Users

- Finance/operator: *As a Finance/operator, I want to read net revenue by day, provider, plan, product, campaign — one query, no reconciliation spreadsheets so that the single append-only record of what was sold, for how much, and what happened to the money afterward as defined here.* reads net revenue by day, provider, plan, product, campaign — one query, no reconciliation spreadsheets.
- Support/ops: *As a Support/ops, I want to trace any buyer money question to one immutable line and its linked reversal lines so that the single append-only record of what was sold, for how much, and what happened to the money afterward as defined here.* traces any buyer money question to one immutable line and its linked reversal lines.
- Auditor: *As an Auditor, I want to replay platform revenue from append-only facts plus the provider webhooks that caused them so that the single append-only record of what was sold, for how much, and what happened to the money afterward as defined here.* replays platform revenue from append-only facts plus the provider webhooks that caused them.

## Functional requirements

1. Projection rule: each provider grant handler emits exactly one `sales_lines{order_id UNIQUE, user_id, kind (plan|product), plan_term_version?, amount_vnd, provider, provider_txn_ref, granted_at}` row in the same transaction as the grant (grant without a sales line is impossible by construction; duplicate grant attempts hit the UNIQUE guard and emit nothing).
2. Netting rule: refunds, partial refunds, and lost disputes append `sales_adjustments{sales_line_id, kind (refund|partial_refund|dispute_loss|correction), amount_vnd (negative), reason, actor, at}` — originals are never edited. Net revenue for any slice = lines + adjustments, computable in one query.
3. Fee transparency (recorded, not decided here): provider-fee fields (`provider_fee_vnd?`, unknown until the provider reports it) stay NULL until confirmed, then backfilled by settlement data where available — finance reads gross and net-of-known-fees separately, never a blended fiction. Fee-schedule interpretation lives in finance ops, not in this doc.
4. Correction path: genuine bookkeeping errors produce `kind=correction` adjustment lines with reason + actor (audited ops action) — still append-only, still no UPDATE/DELETE anywhere in code (grep + migration-review gate).
5. Retention and immutability: sales facts persist as long as orders; raw provider payloads age out per their settlement docs while these facts remain. The ledger is the input to revenue analytics (`sales/sales-analytics.md`), affiliate/creator payout bases stay on their own ledgers (no double-entry coupling — each ledger derives from grants independently).
6. Multi-currency posture: amounts stored as charged (integer VND in v1); any future presentment currency adds a `charged_currency` + converted reference without altering VND lines.

## Data / API touches

- New tables `sales_lines` + `sales_adjustments` (schemas above; append-only: INSERT + SELECT only) with standard RLS posture (operator/finance roles only — buyers never read this ledger; they read receipts). All PG via `worker/db/*`.
- No buyer-facing endpoints. Ops reads: `GET /api/v1/ops/sales` (filtered lines + adjustments), `GET /api/v1/ops/sales/:orderCode` (single-order money trail: order → sales line → adjustments → receipts → payouts). All financial reads audited.
- Emission points enumerated per provider (their webhook/grant docs gain a one-line hook reference at implementation — no behavior change to grant logic).

## Acceptance criteria

- Grant drill on three rails (redirect, wallet/card, bank rail): exactly one sales line each, amounts matching charged totals to the dong, unique-guarded against replay.
  *GWT: Given Grant drill on three rails (redirect, wallet/card, bank rail) context; When the checked action occurs; Then exactly one sales line each, amounts matching charged totals to the dong, unique-guarded against replay.*
- Refund/partial/dispute drill: adjustment lines net correctly; originals byte-identical; net-revenue query matches hand computation.
  *GWT: Given Refund/partial/dispute drill context; When adjustment lines net correctly; Then net-revenue query matches hand computation.*
- Correction drill: bookkeeping fix appends a reasoned adjustment; no UPDATE/DELETE path exists in code or migrations.
  *GWT: Given Correction drill context; When bookkeeping fix appends a reasoned adjustment; Then no UPDATE/DELETE path exists in code or migrations.*
- Replay drill: duplicate grant/webhook traffic emits zero additional lines across all providers.
  *GWT: Given Replay drill context; When the checked action occurs; Then duplicate grant/webhook traffic emits zero additional lines across all providers.*
- Auth probe: buyers hitting ops sales endpoints → 403; non-finance roles denied; RLS denies cross-scope rows.
  *GWT: Given Auth probe: buyers hitting ops sales endpoints; When the transition executes; Then 403; non-finance roles denied; RLS denies cross-scope rows.*

## Out of scope

- Funnel and revenue analytics built on these facts (see `sales/sales-analytics.md`); the unified ops console (see `sales/order-management.md`).
- Order lifecycle and transitions (`payment/`); buyer receipts and history (`billing/invoices-receipts.md`, `invoices/`); affiliate/creator payout ledgers (`affiliate/`, `advertising/` — separate liabilities); provider grant mechanics (their own minors); accounting/revenue-recognition reports; P4/P5.

## Open questions

- Q1: Provider-fee capture — which rails report reliable fee data for net-of-fees reads, and at what latency?
- Q2: Correction approval — single finance role suffices, or dual-control above an amount threshold?
