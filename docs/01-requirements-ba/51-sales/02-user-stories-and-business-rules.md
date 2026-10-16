# Sales (Ledger, Analytics, Order Console) - User Stories and Business Rules (feature 51) - GATED

Phase 1 BA · GATED: design-only; AC-01 (absence) runs every release, all other drills run at/after the amendment. Scope: `docs/01-requirements-ba/51-sales/01-document-context-and-scope.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Finance/operator | read net revenue by day, provider, plan, product, campaign — one query | no reconciliation spreadsheets exist | High |
| US-02 | Support agent | find any order from any identifier and explain its state in plain language | buyers get consistent answers without provider archaeology | High |
| US-03 | Ops/finance operator | execute restricted actions with dual control where defined | money moves deliberately and audibly | High |
| US-04 | Operator/founder | answer "are we growing, where do we leak, which rail earns its keep" every morning | decisions use canonical numbers, not per-provider noise | High |
| US-05 | Auditor | replay platform revenue from append-only facts plus the webhooks that caused them | trust is provable, not asserted | Medium |

## Business rules

- **BR-01 Gate (LIVE).** Design-complete only: no sales table, route, job, chunk, or UI string may exist in any image or bundle until a spec amendment; absence drills run every release and a hit blocks promotion.
- **BR-02 Projection rule.** Each provider grant handler emits exactly one `sales_lines{order_id UNIQUE, user_id, kind (plan|product), plan_term_version?, amount_vnd, provider, provider_txn_ref, granted_at}` row in the same transaction as the grant — a grant without a sales line is impossible by construction; duplicate grant attempts hit the UNIQUE guard and emit nothing.
- **BR-03 Netting rule.** Refunds, partial refunds, and lost disputes append `sales_adjustments{sales_line_id, kind (refund|partial_refund|dispute_loss|correction), amount_vnd (negative), reason, actor, at}` — originals are never edited; net revenue for any slice = lines + adjustments in one query.
- **BR-04 Fee transparency.** Provider-fee fields stay NULL until confirmed, then backfill from settlement data where available; finance reads gross and net-of-known-fees separately — never a blended fiction; fee-schedule interpretation stays in finance ops.
- **BR-05 Correction path.** Genuine bookkeeping errors produce `kind=correction` adjustments with reason + actor (audited); still append-only; no UPDATE/DELETE path exists anywhere in code or migrations (grep + migration-review gate).
- **BR-06 Retention and independence.** Sales facts persist as long as orders; raw provider payloads age out per settlement docs while facts remain; affiliate/creator payout bases derive independently from grants (no double-entry coupling).
- **BR-07 Currency posture.** Amounts stored as charged (integer VND in v1); any future presentment currency adds `charged_currency` + a converted reference without altering VND lines.
- **BR-08 Universal lookup.** One search across orderCode, buyer email/account, provider references (`napas_order_ref`/`app_trans_id`/`vnp_TxnRef`/`zp_trans_id`/PaymentIntent id), and receipt/credit-note ids — one assembled view (order row, canonical status, provider events, sales lines + adjustments, receipts, subscription/entitlement effects, attributions where present); unknown identifiers return a clean no-result with no cross-order leakage.
- **BR-09 Action catalog (role-gated, audited with actor + reason).** Support tier: resend receipt, re-drive status verify, escalate to ops case. Ops tier: cancel unpaid, link rail exception, initiate refund, subscription period fix, campaign pause. Every action shows its preconditions and refuses ineligible orders with the reason stated; each action delegates to the owning feature's endpoint — the console orchestrates, never reimplements.
- **BR-10 Case management.** Lightweight cases attached to orders: `support_cases{order_id?, reporter, status (open|investigating|waiting_buyer|resolved), notes[]}`; transitions audited; buyer-visible status mirroring ("under review") — internal notes never exposed; exceptions, disputes, and missing-payout claims link into one thread.
- **BR-11 Dual control.** Money-moving actions above the config floor require a second approver; proposer ≠ approver; pending approvals expire (24 h) and expired approvals cannot execute; approvals are first-class records.
- **BR-12 Plain-language states.** Every canonical status maps to a buyer-safe sentence + next-step guidance for agents; no provider-native codes are quoted to buyers; the console suggests the correct next action per state (e.g. `confirming` → verify-then-wait; `disputed` → evidence pack).
- **BR-13 Safety rails.** All actions re-validate order state at execution — stale-view actions fail closed; bulk actions operate per-order with per-item results (no silent partial batches); every money-moving path has a dry-run/preview showing exact effects; previewed equals executed.
- **BR-14 Funnel analytics.** Checkout started → provider handoff → paid, sliced by provider, plan/product, campaign, device class, day; stages derive from existing events (no new buyer-facing instrumentation); `sales_funnel_daily` rebuilt idempotently from the event trail.
- **BR-15 Revenue cuts.** All cuts off `sales_lines` + `sales_adjustments` (day/week/month, provider, plan vs product, campaign with promo attribution, affiliate-attributed vs direct, new vs renewal); every figure links to its underlying lines — no KPI exists that cannot decompose to order ids; finance-period figures freeze at close as a named, versioned snapshot whose re-runs are byte-identical.
- **BR-16 Provider scorecards.** Authorization/grant success rate, median time-to-paid, webhook lag, sweep-rescue rate, refund + dispute rates with config thresholds that page ops; scorecards feed the checkout kill-switch and provider-ordering decisions with data instead of instinct.
- **BR-17 Watch and cohorts.** Refund/dispute rates by provider/product/campaign with anomaly alerting (spike vs trailing baseline); subscription cohort retention (renewal rate by acquisition cohort + campaign) shared with lifecycle reminder tuning.
- **BR-18 Access, freshness, privacy.** Dashboards refresh hourly-or-better (aggregates, never live order rows); analytics restricted to operator/finance roles; no buyer PII beyond existing ops visibility; affiliate/creator rollups stay on their own scoped endpoints — no cross-earner leakage through shared dashboards; financial reads audited.

## Acceptance criteria pointers

Drills in `docs/01-requirements-ba/51-sales/06-acceptance-criteria.md`: absence (live), grant, refund/partial/dispute, correction, replay, auth, lookup, action, dual-control, case, dry-run, reconciliation, funnel, health, anomaly. GWT forms live there.
