# Subscription Lifecycle - Acceptance Criteria (feature 36)

Phase 1 BA · BDD checks; each maps to the implementation tasks in `docs/02-architecture-techlead/36-subscription-lifecycle/04-step-by-step-implementation-plan.md`.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Time-travel full cycle | A paid active row with content on all pro surfaces | Clock passes `period_end` then `period_end + graceDays` | Row passes `grace -> expired`; free row opens; every downgrade effect fires losslessly; re-upgrade restores all surfaces |
| AC-02 | Early renewal extends | Active pro monthly with `period_end` in 20 days | Buyer renews the same plan+term today | Single row extends to `period_end + 1 month`; status stays `active`; audit `plan.renewed`; no second current row exists |
| AC-03 | Cancel clean | Active paid row; fresh step-up | Buyer cancels, then does nothing | Status `cancelled`; reminders stop; service runs to `period_end`; expiry downgrades losslessly; zero PSP calls in the drill log |
| AC-04 | Reminders exactly once | Active row 8 days from `period_end` | Job runs twice (double fire) | Exactly one `plan.renewal_7d` send; one audit; flags set; rerun writes nothing |
| AC-05 | Grace truth | Row in grace (day 3 of 7) | Buyer opens pro surfaces | Gates pass; banner shows `graceEndsAt`; AI credits do not refill; no new credit window row exists |
| AC-06 | Job-down cutoff | `active` row whose `period_end + graceDays` passed; job never ran | Any pro surface is read | Snapshot resolves free; no pro gate passes; the stale row materializes on the next job run with correct audits |
| AC-07 | Trial once, no instrument | `trialDays = 7`; active pro monthly term; user with no prior trial | User starts a trial, then tries again; a second user without a published term tries | Exactly one `trial` row; second attempt `trial_already_used`; missing-term case `trial_unavailable`; no provider/order values exist on the row |
| AC-08 | Trial conversion path | Trial in day 5 | User buys pro monthly; a different user lets it lapse | Conversion extends from the trial's `period_end` and sets `active`; the lapse path sends `plan.trial_1d`, then `grace -> expired` losslessly |
| AC-09 | Lossless downgrade drill | Pro user with custom domain live, pro theme, premium template id stored, pro data present | Expiry executes, then the user re-upgrades | Every surface stops/falls back per the effect matrix (stored ids/DNS untouched); after re-upgrade all surfaces match pre-downgrade byte-for-byte |
| AC-10 | Rerun safety | Yesterday's post-transition state | Job fires twice plus a second replica | Zero writes, zero duplicate audits; advisory lock observed in logs |
| AC-11 | Period fix audit | An ops operator with a wrong `period_end` | Fix submitted with reason, then repeated | Before/after + operator + reason in audit; both writes audited individually; invalid bounds refused |
| AC-12 | Scoping probes | Two users, one in grace | User B reads/cancels/period-fixes user A's rows | 403/404 parity; RLS denies; ops route rejects non-ops roles |
