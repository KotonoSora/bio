# Affiliate Commission + Payout (computation, thresholds, rails)

PLAN ref: none — new scope beyond PLAN P0–P5, flagged (see `affiliate/affiliate-program.md` for placement). Enrollment in that doc; attribution evidence in `affiliate/tracking-attribution.md`.

## Purpose

Define how confirmed attributions become money in affiliates' hands: commission computation off frozen rate snapshots, payout thresholds and cycles, bank-rail payouts with reconciliation, and the tax paperwork boundary. Attribution decides WHO; this doc decides HOW MUCH, WHEN, and with what paper trail — so payouts are boring, predictable, and auditable instead of a monthly firefight.

## Users

- Affiliate: *As an Affiliate, I want to watch pending → confirmed → payable → paid states, meet a threshold, receive bank payout with a statement so that confirmed attributions become money in affiliates' hands as defined here.* watches pending → confirmed → payable → paid states, meets a threshold, receives bank payout with a statement.
- Program operator: *As a Program operator, I want to run the payout cycle, handle failures and tax paperwork, answer "where is my money" from one ledger so that confirmed attributions become money in affiliates' hands as defined here.* runs the payout cycle, handles failures and tax paperwork, answers "where is my money" from one ledger.
- Finance: *As a Finance, I want to book commission expense off the same ledger, withholds/report only as counsel directs so that confirmed attributions become money in affiliates' hands as defined here.* books commission expense off the same ledger, withholds/reports only as counsel directs.

## Functional requirements

1. Computation: on attribution confirmation, commission = confirmed order net × the rate snapshot stamped at attribution (tier changes never rewrite history). Multi-item and partial-refund orders compute per-line and net down proportionally; currency is integer VND throughout; rounding (if ever needed) always favors the affiliate and is logged per line.
2. Ledger states per commission line: `pending` (order paid, inside refund window) → `payable` (window passed, no reversal) → `in_payout` (batched) → `paid` (rail-confirmed) | `failed` (rail rejection, auto-retry bounded, then ops case). Reversals move `pending`/`payable` → `reversed` with the linked refund/dispute reference; `paid` lines reverse only via a debit against future payables (explicit ops action, affiliate-notified — never silent clawback from bank).
3. Payout cycle: monthly cycle with a minimum threshold (see Q1; sub-threshold balances roll forward indefinitely, never expire without notice). Cycle run is an audited batch: freeze eligible lines → generate bank payout instructions + affiliate statements → execute → reconcile confirmations → mark `paid`. Any line failing reconciliation stays `in_payout` with an ops case, blocking nothing else in the batch.
4. Payout rail (v1): VN bank transfers from the operating account via the existing finance ops flow (batch instruction file + dual-control approval — same discipline as manual bank refunds in the rail settlement docs). No new rail integration in v1; e-wallet/foreign payout rails are named future work with their own compliance review.
5. Tax boundary (explicit, non-blocking): payouts to individuals may trigger withholding/reporting obligations — the doc mandates collecting the required identity/tax fields at enrollment and producing per-affiliate annual summaries, but directs the actual rates, forms, and filing to finance/legal confirmation (see Q2). The platform never improvises tax treatment in code.
6. Statements and support: per-cycle affiliate statement (lines, rates, reversals, threshold progress, payout reference) in dashboard + email; "missing payout" support flow starts from the statement id and ends in the ledger, never in chat memory.

## Data / API touches

- New tables `commissions{attribution_id UNIQUE, affiliate_id, amount_vnd, rate_snapshot, status, payout_batch_id?}` + `payout_batches{id, period, status, executed_at, rail_refs}` with standard RLS posture (affiliates read own lines/statements; batch execution restricted to finance-ops role). All PG via `worker/db/*`.
- Endpoints: affiliate `GET /api/v1/affiliate/earnings` (lines + states + threshold progress), `GET /api/v1/affiliate/statements/:id`; ops `POST /api/v1/ops/payout-batches` (freeze + instruct), `POST /api/v1/ops/payout-batches/:id/confirm` (reconcile). All financial actions append `audit_log`.
- Failure accounting: `failed` lines carry rail reason codes; bounded auto-retry with backoff, then human case — retries never duplicate payouts (batch-line idempotency).

## Acceptance criteria

- Computation drill: tier change mid-month affects only later attributions; partial refund nets the line proportionally; rounding (if any) favors affiliate and is logged.
  *GWT: Given Computation drill context; When tier change mid-month affects only later attributions; Then rounding (if any) favors affiliate and is logged.*
- Cycle drill (sandbox): freeze → instruct → execute → reconcile → `paid` with matching statements and emails; one failed line blocks nothing else and opens exactly one ops case.
  *GWT: Given Cycle drill (sandbox): freeze; When the transition executes; Then instruct → execute → reconcile → `paid` with matching statements and emails; one failed line blocks nothing else and opens exactly one ops case.*
- Reversal drill: post-payout reversal becomes a notified debit against future payables — no silent bank pull, full audit chain.
  *GWT: Given Reversal drill context; When the checked action occurs; Then post-payout reversal becomes a notified debit against future payables — no silent bank pull, full audit chain.*
- Duplicate-execution drill: re-running a batch confirm is a no-op (idempotent); double-instruct impossible while frozen.
  *GWT: Given Duplicate-execution drill context; When re-running a batch confirm is a no-op (idempotent); Then double-instruct impossible while frozen.*
- Tax drill: affiliate without complete tax fields cannot enter a payout batch (blocked with a fix-it notice, earnings intact).
  *GWT: Given Tax drill context; When the checked action occurs; Then affiliate without complete tax fields cannot enter a payout batch (blocked with a fix-it notice, earnings intact).*
- Auth probe: affiliate A reading affiliate B earnings/statements → 403; RLS denies cross-affiliate rows.
  *GWT: Given Auth probe: affiliate A reading affiliate B earnings/statements; When the transition executes; Then 403; RLS denies cross-affiliate rows.*

## Out of scope

- Enrollment, tiers, and terms (see `affiliate/affiliate-program.md`); link/code mechanics and windows (see `affiliate/tracking-attribution.md`).
- Referral pro-day credits (`subscription/trials-promotions.md`); influencer batch supply (`promo-code/`); agency billing (`organizations/`, P4); accounting/revenue-recognition reports; P5.

## Open questions

- Q1: Payout threshold and cycle — monthly + 500k VND minimum, or different cadence/floor?
- Q2: Withholding/reporting rules for individual affiliates — finance/legal confirmation and form list?
