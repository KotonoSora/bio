# Creator Revenue Share Gated Frontend Plan (earnings dashboard — buildable at plan update)

PLAN ref: none — P4-growth, GATED. BA source: `docs/monetize/advertising/creator-revenue-share.md`. Server truth in the revenue-share backend plan above (computation, close, cycles — never redefined here).

## Purpose

Hold the buildable earnings-dashboard UX: estimated vs confirmed balances, per-flight breakdown, exclusion effects, threshold progress, payout history with references, and clawback explainability. Nothing here renders before the plan update.

## Users

- Frontend developer (future): builds earnings screens reusing dashboard components.
- QA (now): asserts zero earnings UI exists; (future) transparency/clawback drills.
- Creator (future): answers "why did my earnings drop" from the dashboard alone.

## Functional requirements

1. Surfaces (future): earnings tab (estimated vs confirmed cards, per-flight table, exclusion-effects panel, threshold progress bar, payout history with statement links); clawback notices (reason + evidence links + appeal entry where terms allow). Shared `FetchState`/footnotes with analytics dashboards (same honesty components, new data).
2. Step 1 — balances flow: `GET earnings` → estimated (pending-close, labeled `Not yet final`) vs confirmed (post-close, labeled `Payable per cycle`) cards side by side (never a single blended number — blending estimates with confirmed money is the trust-killer this layout forbids).
3. Step 2 — breakdown flow: per-flight rows (impressions, clicks, rate version, accrued) + exclusion-effects panel (filter counts affecting this creator's pages with plain reasons — `312 views excluded: bot traffic` reads as protection, not theft).
4. Step 3 — threshold/payout flow: progress bar to threshold (roll-forward balances labeled `carries forward, never expires without notice`); payout history with per-cycle statements (downloadable, matching emails); sub-threshold months show progress, not failure.
5. Step 4 — clawback flow: clawed lines render struck-through with reason + evidence links + appeal entry (terms-laddered); legitimate lines untouched in the same view (precision visible — mass-clawback panic prevented by showing exactly what was tainted).
6. Step 5 — farming-ladder UX: warn state banner (`Unusual activity on your slots — see what counts`) linking the affected lines; restriction states plainly labeled with lift conditions (opacity here prevents support tickets, not creates them).

## Data / API touches

- Screens → endpoints map (all GATED, absent now): earnings tab → `GET earnings`; statements → `GET statements/:id`; opt-in → inventory toggle. Session cookie + CSRF.

## Acceptance criteria

- Absence drill (now): zero earnings UI and routes.
- Separation drill (at build): estimated vs confirmed never blended (DOM assertion).
- Transparency drill (at build): exclusion effects + clawback reasons + evidence links all present; drop explained from dashboard data alone.
- Threshold drill (at build): roll-forward labeled; history downloadable and matching.
- Security drill (at build): cross-creator reads 403; no other earner's lines visible.

## Out of scope

- Computation/cycles/clawbacks (backend plan above); ledger/batch contracts (database plan above); inventory/counting; affiliate screens.

## Open questions

- Q1: Estimate refresh cadence display — daily snapshots labeled, or live-ish counters?
- Q2: Appeal entry in-dashboard vs support-ticket handoff?
