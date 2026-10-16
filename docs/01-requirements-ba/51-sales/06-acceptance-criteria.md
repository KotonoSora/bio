# Sales (Ledger, Analytics, Order Console) - Acceptance Criteria (feature 51) - GATED

Phase 1 BA · GATED: AC-01 runs every release (live now); AC-02..AC-17 run only at/after the spec amendment. Drills reference `docs/02-architecture-techlead/51-sales/04-step-by-step-implementation-plan.md`.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Absence drill (LIVE) | Any release candidate while gated | Schema diff, OpenAPI diff, source + bundle grep run | Zero sales surface found (`sales_*` tables, console routes, funnel/scorecard UI); a hit blocks promotion by tooling |
| AC-02 | Grant drill (three rails) | Redirect, wallet/card, and bank-rail grants | Grants execute | Exactly one sales line each; amounts match charged totals to the dong; UNIQUE guard rejects replay |
| AC-03 | Refund/partial/dispute drill | A refund, a partial refund, a lost dispute | Adjustments append | Lines net correctly; originals byte-identical; net-revenue query matches hand computation |
| AC-04 | Correction drill | A bookkeeping error | Correction appended | Reasoned adjustment with actor; no UPDATE/DELETE path exists in code or migrations |
| AC-05 | Replay drill | Duplicate grant/webhook traffic across providers | Replay runs | Zero additional lines emitted |
| AC-06 | Auth drill | Buyers on ops endpoints; support on finance-only actions; non-finance on analytics | Attempts run | 403 with zero rows; RLS denies cross-scope |
| AC-07 | Lookup drill | The same order via all five identifier kinds; unknown identifiers | Lookups run | Identical assembled trails; clean no-result without cross-order leakage |
| AC-08 | Action drill (per role) | Support-tier and ops-tier actions; ineligible-state attempts; stale views | Each runs | Support cannot reach ops actions (403 + reason); ineligible refused with preconditions; stale-view fails closed |
| AC-09 | Dual-control drill | An above-floor refund | Approval flow runs | Different approver required; self-approval impossible; expired approvals cannot execute |
| AC-10 | Case drill | An exception escalated to a case | Case lifecycle runs | Buyer sees "under review" only; resolution with full audit chain; internal notes never exposed |
| AC-11 | Dry-run drill | Every money-moving action | Previews compared to executions | Previewed equals executed on samples |
| AC-12 | Reconciliation drill | Any dashboard figure; a close snapshot | Decomposition + re-run | Figure decomposes exactly to ledger lines; close re-runs byte-identical |
| AC-13 | Funnel drill | Synthetic traffic with known drop-offs; a kill-switch flip | Aggregates rebuild | Exact stage counts per slice; kill-switch shift visible same-day |
| AC-14 | Health drill | Degraded-provider simulation (delayed webhooks, elevated declines) | Scorecards evaluate | Thresholds trip; ops paged with the right slice |
| AC-15 | Anomaly drill | Seeded dispute spike and funnel cliff | Watch evaluates | Both alert before manual review would catch them (measured lead time) |
| AC-16 | Fee drill | Grants with unconfirmed fees; settlement backfill | Reads run | Gross and net-of-known render separately; no blended number; NULL until confirmed |
| AC-17 | Bulk drill | A bulk action with one failing order | Batch runs | Per-order results; failure listed with reason; no silent partial batch |
