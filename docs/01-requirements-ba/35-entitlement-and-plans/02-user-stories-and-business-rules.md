# Entitlement and Plans - User Stories and Business Rules (feature 35)

Phase 1 BA · Stories and numbered rules. Implemented by `docs/02-architecture-techlead/35-entitlement-and-plans/`; consumed by `docs/02-architecture-techlead/33-checkout-session/` (pricing) and `docs/02-architecture-techlead/34-order-status-and-orchestration/` (grant writes).

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Pro buyer | The plan I paid for active on any of the eight rails within a minute | Features unlock immediately after payment | P0 |
| US-02 | Creator | One clear plan status (plan, period end, AI quota) everywhere | I never see "pro here, free there" | P0 |
| US-03 | Free user hitting a gate | An upgrade prompt stating exact effects | I know what money buys before paying | P0 |
| US-04 | Ops operator | Publish new prices/quotas without a deploy | Campaigns ship same-day without breaking in-flight checkouts | P1 |
| US-05 | Pro user | AI JD-tailor suggestions within my allowance | Tailoring a CV is part of my plan | P1 |
| US-06 | Over-quota user | A clear prompt, and a credit returned when generation fails | Failures never silently burn credits | P1 |
| US-07 | Support agent | The grant chain visible read-only | "Why does this user have pro?" is answerable without DB access | P1 |

## Business rules

| Rule | Statement |
|---|---|
| BR-01 | One current row: at most one subscription per user in `active`/`grace`/`trial` (partial unique). Grants close the previous row as status `superseded` (audited) and open the new row in one transaction |
| BR-02 | Free floor is structural: resolution never branches on row absence. A user with no subscription history gets a `free`/`active` row materialized idempotently at first touch; history without a current row is corruption (loud failure + alert), never silent free |
| BR-03 | Grant: a verified confirmation (34) closes the current row and opens the new one (plan, term row, provider, order, period) inside the grant transaction; a paid plan order with no ledger row is a defect |
| BR-04 | Period bounds: `period_start` = grant transaction time; `period_end` = start + 1 month (monthly) or + 12 months (yearly), calendar-clamped; free rows carry no period. A same plan+term renewal of the current row extends it from `max(now, period_end)` instead of opening a new row (36's rule) |
| BR-05 | Effective plan: `active`/`grace`/`trial` resolve to the row's plan while `now < period_end + graceDays` (36's window, config `billing.lifecycle`); `cancelled` resolves to the plan until `period_end`, then free; `superseded` and `expired` resolve free. Resolution is time-aware and needs no job |
| BR-06 | Price truth: quote and create price plan intents from the active `plan_terms` row; the order stamps that term row at creation; in-flight pendings charge the stamped row; later publication flips never alter stamped rows |
| BR-07 | Plan intents require an explicit `term` (`monthly`/`yearly`): missing `term_required`, invalid `term_invalid`, `free` `plan_not_purchasable`, no active row `plan_not_published` — each `422`/`409` per the error table |
| BR-08 | Yearly amount = `12 x monthly - configured discount`, computed to the VND (half-up) when a yearly term is drafted/activated, stored in the immutable yearly row; config unset means yearly equals `12 x monthly` (no saving claim) |
| BR-09 | Catalog lifecycle: `draft -> active -> retired`; one active row per (plan, term); activation refused while another active row exists; retirement refused while `pending` orders reference the row; rows are never edited, only superseded by new versions |
| BR-10 | Public plans read (`GET /api/v1/plans`, shape frozen by 03) serves active rows cache-first (`max-age=300`); `404 plan_not_published` until the paid monthly+yearly pair exists |
| BR-11 | Projection: the feature 01 snapshot resolves `plan`, period bounds, quotas, and capabilities from the ledger read; consumers keep the `<= 30 s` bound; billing unreachable means snapshot error and fail-closed free — no invented values |
| BR-12 | Capabilities: `profiles.limit` free `"1"`, pro `"unlimited"` (03's registration); keys come from plan data, never code branches |
| BR-13 | AI credits: total stamped at period start from the subscription's term row; consumed counter per (user, kind, period); no rollover; each new period starts a fresh counter; free = 0 |
| BR-14 | Debit is atomic and keyed-idempotent (one event row per debit, replay returns the same result); over-quota debit refused; a failed generation returns its credit exactly once (one return per debit); counters can never exceed the total (DB check) |
| BR-15 | Credit kinds are a code registry: `tailor-resume` (v1), `design-assist` (reserved, gated 16); unknown kind refused; adding a kind is a spec amendment, never a DDL change |
| BR-16 | Tailor is pro-only (`403 upgrade_required`) and quota-bounded (`429 tailor_quota_exhausted`); the job description is data, never instructions (fixed system prompt, capped 10 000 chars, escaped output); results are suggestions only (no auto-apply) |
| BR-17 | Ops routes: ops role drafts/retires, finance role activates (reason mandatory); every action appends audit; changes are effective `<= 5 s`; no price/quota literal exists in code (grep gate) |
| BR-18 | Product purchases (course/digital/tip/paywall/service/merch) never create or alter ledger rows; their fulfillment is 23/24/25 via `order.granted.v1` — plan truth is only for plan orders |
| BR-19 | Downgrade target is the free floor row; downgrades are lossless (data retained, creates blocked by the limit); execution belongs to 36 at period end |
| BR-20 | Traceability: support reconstructs order -> grant -> ledger row -> audit from 32's trace; rows are retained as financial records (PII scrubbed per 02) |
| BR-21 | Money values are data: fixtures are labeled and test-database-only; no seed introduces a paid price — the first real price enters via a finance activation |
