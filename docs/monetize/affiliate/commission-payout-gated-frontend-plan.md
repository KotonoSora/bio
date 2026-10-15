# Affiliate Commission Payout Gated Frontend Plan (earnings + statements — buildable at plan update)

PLAN ref: none — P4-growth, GATED. BA source: `docs/monetize/affiliate/commission-payout.md`. Server truth in the commission backend plan above (computation, states, cycles — never redefined here).

## Purpose

Hold the buildable earnings/statement UX: pending→payable→paid state visibility, threshold progress, per-cycle statements, reversal explainability, and the missing-payout flow that starts at statement id. Nothing here renders before the plan update.

## Users

- Frontend developer (future): builds affiliate earnings + statements screens.
- QA (now): asserts zero payout UI exists; (future) state/threshold/reversal drills.
- Affiliate (future): watches states, meets threshold, traces every dong to its line.

## Functional requirements

1. Surfaces (future): earnings tab (lines with states, threshold progress bar, roll-forward balance) + statements list (per-cycle: lines, rates, reversals, payout ref, download) + missing-payout entry (statement-id-first support flow).
2. Step 1 — states flow: lines render `pending` (inside refund window, with window-end date — waiting explained, not silent) → `payable` (cleared, accumulates to threshold) → `in_payout` (batched, with cycle id) → `paid` (rail-confirmed, with reference) | `failed` (reason + retry state, never dead-end copy). State changes arrive via fetch, never pushed (no websocket in v1 — same polling discipline as other dashboards).
3. Step 2 — threshold flow: progress bar to floor (per Q1) with roll-forward labeled (`Balance carries forward — never expires without notice`); sub-threshold months show progress, not failure; threshold met mid-cycle reflects immediately (eligibility computed per line at freeze, displayed live).
4. Step 3 — statement flow: per-cycle statement (lines, stamped rates, reversals with links, threshold math, payout reference) viewable + downloadable + emailed (same content three ways — reconciliation across channels, not three truths); missing-payout flow starts from statement id and ends in ledger state (traceability designed into the artifact).
5. Step 4 — reversal flow: reversed lines show linked refund/dispute reference + proportional math + (for post-payout debits) the future-payables impact with affiliate notice copy (forward-debit transparency — the notice names the debit, the cycle, and the appeal path).
6. Step 5 — tax-paperwork UX: incomplete-fields banner blocks batch entry with fix-it checklist (earnings intact and visible — blocked payout is not lost money, stated); annual summaries downloadable when finance confirms formats.

## Data / API touches

- Screens → endpoints map (all GATED, absent now): earnings tab → earnings reads; statements → statement reads/downloads; missing-payout → support flow seeded by statement id. Session cookie + CSRF.

## Acceptance criteria

- Absence drill (now): zero payout UI and routes.
- States drill (at build): all six states render with specified copy + actions; window-end dates present on pending.
- Threshold drill (at build): roll-forward labeled; mid-cycle eligibility reflects live.
- Statement drill (at build): three-channel consistency (dashboard/email/download identical); traceability to ledger.
- Reversal drill (at build): references + math + debit impact + notice all present.
- Security drill (at build): cross-affiliate reads 403; no other earner's lines visible.

## Out of scope

- Computation/cycle/reversal logic (backend plan above); commission/batch contracts (database plan above); enrollment/tiers (program plans); link/code mechanics (tracking plans).

## Open questions

- Q1: Threshold progress display — exact VND remaining vs percentage bar (both)?
- Q2: Statement download format — HTML print (resume pattern) vs PDF artifact (guardrail says print)?
