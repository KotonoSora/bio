# Affiliate Commission Payout Gated Database Plan (commissions + batches — designed, not built)

PLAN ref: none — P4-growth, GATED (attribution decides WHO here via tracking; this slice decides HOW MUCH, WHEN, with what paper trail). BA source: `docs/monetize/affiliate/commission-payout.md`. Consumed by the commission backend plan (computation, cycles, reversals; splits next to this file). Attribution rows live in the tracking plans (evidence source, never redefined here). Creator earnings reuse the shared batch runner defined here (earner-type partitioned — one table, specified once). No migration — nothing is created until the plan update.

## Purpose

Hold the buildable commission/payout schema: per-line computation off frozen rate snapshots, the pending→payable→in_payout→paid|failed machine with reversal discipline, audited monthly batches over VN bank rails, and the tax-paperwork boundary. Exactly the future `CREATE TABLE`s, written once at plan update; zero tables now. Payouts boring, predictable, auditable — never a monthly firefight.

## Users

- Backend developer (future): runs these scripts once at plan update; batch confirms are idempotent by construction.
- QA (now): absence drill; (future) computation/cycle/reversal/duplicate/tax drills.
- Affiliate/operator/finance (future): watches states, runs cycles, books expense off one ledger.

## Functional requirements

1. Present state: zero commission/payout tables, zero batch jobs. Absence verified per release.
2. Table `aff.commissions` (future) — one row per attribution line: `attribution_id UNIQUE` (one commission per attribution, ever — uniqueness IS the double-pay guard), `affiliate_id`, `amount_vnd` (integer; computed as confirmed order net × rate snapshot stamped at attribution — tier changes never rewrite history; multi-item/partial-refund orders net proportionally per line; rounding, if ever needed, favors the affiliate and is logged per line), `rate_snapshot` (frozen tier + base + version — recomputation from history, not from current rates), `status` (`pending|payable|in_payout|paid|failed|reversed`), `payout_batch_id?`. States: `pending` (paid order, inside refund window) → `payable` (window passed clean) → `in_payout` (batched) → `paid` (rail-confirmed) | `failed` (rail rejection, bounded auto-retry with backoff, then ops case). Reversals move `pending`/`payable → reversed` with linked refund/dispute reference; `paid` lines reverse ONLY via explicit ops debit against future payables (affiliate-notified — never silent bank pull; pulls don't exist in this design).
3. Table `aff.payout_batches` (future, SHARED with creator earnings — one runner, earner-type partitioned): `id`, `period`, `status` (open|frozen|instructed|confirmed), `executed_at`, `rail_refs`, `earner_type` (affiliate|creator — partition column present from day one so the creator slice adds rows, never a second table). Cycle: freeze eligible lines → generate bank instructions + per-earner statements → execute → reconcile confirmations → mark `paid`. Failed lines stay `in_payout` with exactly one ops case, blocking nothing else (batch partial-failure is normal operations, not exceptional).
4. Payout rail v1: VN bank transfers from the operating account via the existing finance ops flow (batch instruction file + dual-control approval — same discipline as manual bank refunds). No new rail integration in v1 (e-wallet/foreign rails need their own compliance review — named future work, never smuggled in).
5. Tax boundary (explicit, non-blocking): identity/tax fields collected at enrollment (required for batch entry — incomplete paperwork blocks entry with fix-it notice, earnings intact); per-affiliate annual summaries produced; actual rates/forms/filing directed to finance/legal confirmation (platform never improvises tax treatment in code — the absence of tax-math code is asserted, not assumed).
6. Statements: per-cycle per-earner statements (lines, rates, reversals, threshold progress, payout reference) in dashboard + email; "missing payout" support starts from statement id and ends in the ledger (never chat memory — traceability designed into the artifact).
7. Thresholds: monthly cycle, minimum threshold (per Q1), sub-threshold roll-forward indefinitely (never expiring without notice — expiry would be silent forfeiture, forbidden by the same principle as termination-forfeiture limits).

## Data / API touches

- DDL now: none — assertion: pre-update tags show zero commission/batch tables (schema + source grep). Future: two tables per rules 2–3 (full `CREATE TABLE`s at plan update) + audit rows per financial action. RLS (future): affiliates read own lines/statements; batch execution restricted to finance-ops role.

## Acceptance criteria

- Absence drill (now): zero commission/payout surface every release.
- Computation drill (at build): mid-month tier change affects later attributions only; partials net proportionally; rounding favors + logged.
- Cycle drill (at build, sandbox): freeze→instruct→execute→reconcile→paid with matching statements/emails; one failed line blocks nothing, opens one case.
- Reversal drill (at build): post-payout reversal = notified future-payables debit with full chain; no silent pulls.
- Duplicate drill (at build): batch-confirm re-run is a no-op; double-instruct impossible while frozen.
- Tax drill (at build): incomplete paperwork blocks batch entry (earnings intact); summaries producible.
- Auth drill (at build): cross-affiliate 403 + RLS deny.

## Out of scope

- Computation/cycle/reversal flows (backend plan); earnings/statement screens (frontend plan); enrollment/tiers (program plans); link/code/windows (tracking plans); creator earnings lines (same runner, separate earner type); referral credits; influencer batches; agency billing; accounting reports; P5.

## Open questions

- Q1: Threshold + cycle (monthly + 500k VND minimum, or different — BA Q1)?
- Q2: Withholding/reporting rules + form list (BA Q2 — finance/legal confirmation)?
