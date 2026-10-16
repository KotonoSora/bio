# Entitlement and Plans - Architecture and Tech Stack (feature 35)

Phase 2 TL · Service: `billing-service` hosts the ledger, catalog, and credit kernel; the AI JD-tailor endpoint executes in `resume-service` against billing's internal debit contract (one new endpoint each side). Baseline: `docs/02-architecture-techlead/00-platform-baseline/`. Implements `docs/01-requirements-ba/35-entitlement-and-plans/`.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | Ledger/catalog/credits live in `billing-service` (`com.app.billing.ledger`, `.catalog`, `.credits`). The tailor endpoint (`POST /api/v1/ai/tailor-resume`) executes in `resume-service` (`com.app.resume.ai`): documents and AI adapters are content-side; the debit seam mirrors shop 25 -> billing's internal create. | Data and secret locality; billing stays money-only |
| D-2 | `V2__entitlement_ledger.sql` creates `plans`, `plan_terms`, `subscriptions`, `ai_credit_periods`, `ai_credit_events` and ALTERs `orders` (adds `plan_term_version`). Chain map row updated in `docs/02-architecture-techlead/32-monetize-overview/02-database-schema-design.md`. | One CREATE per table, one owning feature; `reminded_*`/grace fields stay V3 (36) per the chain map |
| D-3 | DEVIATION FLAGGED (vs legacy ledger FR5): "free row in the same signup unit of work" cannot span services. Realized as idempotent first-touch materialization with a no-history guard: no history -> materialize `free`; history without a current row -> loud 500 + `plan.missing_row` alert. Signup never calls billing. | Structural equivalent; gates never branch on absence; corruption still alerts loudly |
| D-4 | Grant wiring: 34's grant transaction calls `LedgerWriter.grantPaidPlan` (close superseded row, open new row, `audit_log` rows); a same plan+term renewal extends the current row from `max(now, period_end)` instead (36's branch); non-plan orders never touch the ledger. A paid plan order with no ledger row is a defect. | Same-transaction truth; replay safety inherited from 34's guard + terminal fast-path |
| D-5 | Resolver is time-aware and job-independent: `active|grace|trial` -> plan while `now < period_end + graceDays` (36's window, config `billing.lifecycle`); `cancelled` -> plan while `now < period_end`, then free; `expired` -> free. 36's lifecycle job materializes status + notifications only. | A lagging or down job can never serve a lapsed plan; cancel keeps service-to-period-end |
| D-6 | Projection: `GET /internal/v1/billing/plan/{userId}` returns the effective view (plan, status, term, period, quotas, capabilities). `account-service` resolves fresh (billing caches in-service `<= 5 s`, cleared on ledger writes); consumers keep the `<= 30 s` bound. Billing unreachable -> snapshot error -> consumers fail closed to free. | One end-to-end `<= 30 s` bound; no cross-service caches stacked; 01's normative "no event - TTL self-heals" preserved |
| D-7 | DEVIATION FLAGGED (vs 03 D-5/TL03): the yearly price is `12 x monthly - configured discount` computed when the yearly row is drafted/activated and stored in that immutable row; 03's "computed server-side" is realized at publication time, not per read. `yearlyDiscountPct` in the payload is derived from the active rows (omitted when 0); the frozen `PlansResponse` shape is unchanged. | Grandfathering + `plan_term_version` stamping require immutable rows; a read-time formula would silently reprice stamped orders |
| D-8 | Public catalog: `GET /api/v1/plans` (path/shape frozen by 03) served by billing-service from active rows; `version` token = `m{monthlyVer}y{yearlyVer}`; `404 plan_not_published` until the paid monthly+yearly pair is active; `Cache-Control: public, max-age=300`. | Shape stability; values from exactly one source |
| D-9 | AI credits: `ai_credit_periods` counters (UNIQUE `(user_id, kind, period_start)`) + `ai_credit_events` append-only log (UNIQUE `idempotency_key`, one return per debit). Kinds are a code registry (`tailor-resume`; `design-assist` reserved for gated 16); no DB CHECK on `kind` - 16's "no DDL change" contract holds. | Atomic counters under concurrency; idempotent replays; window-generic (day caps later need rows, not DDL) |
| D-10 | Tailor contract: pro-only (`403 upgrade_required`), quota-bounded (`429 tailor_quota_exhausted`, mirrors 16's `assist_quota_exhausted`), suggestions-only output (`<= 8` items, escaped, no auto-apply), fixed system prompt, JD as data (`<= 10 000` chars, never logged), 30 s budget -> `503 ai_unavailable` + credit return. | Risk posture; content locality in resume-service |
| D-11 | No stream events: ledger changes invalidate in-service caches directly; the snapshot self-heals via its TTL; `billing.events` keeps 34/38's two events (`PlanChanged` -> D-6); audit carries traceability. | No cross-service consumer needs a push |
| D-12 | Ops: draft/retire/list require the ops role (`403 billing_ops_required`); activation adds finance (`403 finance_role_required`, reused from 26); reason mandatory; `yearlyDiscount`/`trialDays` live in `ops_config` key `billing.catalog` (absent -> omitted claims). | Money-visible price goes live under finance; 26's audit posture |

## Ownership

| Owns | Does not own |
|---|---|
| Subscription ledger rows + writer + resolver; free floor; catalog tables + lifecycle; public pricing read; plan-term pricing inputs for 33; entitlement projection details; AI credit counters/events + debit/return contract; tailor surface contract; `plan_term_version` semantics | Quote/create/handoff/expiry (33); status machine + grant step (34); lifecycle jobs/renewals/grace/cancel/reminders (36); receipts (37); refunds (38); promos (39); rails/PSP verification (40); design-assist build (gated 16); org seats (45-47) |

## Wiring notes

- `orders.plan_term_version` is stamped by 33's create for plan intents (cross-edit); reads compose plan titles from active or retired term rows by reference (in-service, 34's detail).
- 32's `OrderTraceView.ledgerRows` is fed by this ledger's order-linked rows (in-service).
- 26's forward seam realized: `GET /internal/v1/billing/plans/current-prices` (empty list -> "pending"); tier default resolves from the 01 snapshot.
- 16's forward references satisfied: kind-keyed counters + periods exported via the internal plan read; no text edits needed there.
- No new Redis database, no new stream; in-service cache keys `billing:plan:{userId}`, `billing:catalog:active` (`<= 5 s`, cleared on write).

## Cross-edits in this batch

| File | Change |
|---|---|
| 01 TL 01/03/04/05 | Snapshot "ledger projection lands" becomes realized; degrade row "(35)" |
| 03 TL 01 | D-1/D-2/D-5: projection wording, serving owner, config store named |
| 33 BA 02/04/05 + TL 01/02/03/04 | `term` on plan intents; pricing from active terms; fixture notes realized; V2 column note |
| 34 TL 01/02/03 | Grant txn calls the ledger writer; fixture notes realized |
| 26 TL 03 | Seam rows: prices + tier (35) |
| 32 TL 02/03 | V2 row extended; registry rows (plans read, ops terms, internal plan/prices/debit/return, tailor) |
| baseline 01 + `MIGRATION.md` | resume-service hosts the 35 tailor endpoint; rows `done` at the batch tail |
