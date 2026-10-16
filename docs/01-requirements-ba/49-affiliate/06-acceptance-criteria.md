# Affiliate Tracking, Program, and Payouts - Acceptance Criteria (feature 49) - GATED

Phase 1 BA · GATED: AC-01 runs every release (live now); AC-02..AC-16 run only at/after the spec amendment. Drills reference `docs/02-architecture-techlead/49-affiliate/04-step-by-step-implementation-plan.md`.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Absence drill (LIVE) | Any release candidate while gated | Schema diff, OpenAPI diff, source + bundle grep run | Zero affiliate surface found (`affiliate_*` tables, `/r/` route, dashboard strings, payout jobs); a hit blocks promotion by tooling |
| AC-02 | Precedence drill | Cookie A + code B at checkout; two cookies; direct purchase | Attribution resolves | B credited; most recent click wins among cookies; direct unattributed; all reproducible from evidence bundles |
| AC-03 | Window drill | Purchases on day 29 and day 31 of the window | Attribution evaluates | Day 29 attributes; day 31 does not — respected to the second from server-side timestamps |
| AC-04 | Reversal drill | A confirmed line; refund/dispute-loss (full and partial); a self-purchase | States transition | Reversed proportionally on partials; self-purchase voids + flags; linked references present |
| AC-05 | Stuffing drill | Hidden-iframe click floods and unattributed drops seeded | Ingest evaluates | Rejected; dashboards unskewed; affiliate warned per terms ladder |
| AC-06 | Privacy drill | A declined-consent buyer completes checkout | Flow runs | Zero attribution; no friction; affiliate stats contain no buyer PII |
| AC-07 | Auth drill | Affiliate A reads affiliate B clicks/attributions/earnings; rate-setting attempts | Each is attempted | 403/404 with zero rows; RLS denies cross-affiliate; no rate route exists |
| AC-08 | Enrollment drill | An application submitted | Flow runs | Manual approval → active with attributable links/codes; auto-approval impossible (no code path); audited |
| AC-09 | Tier drill | A volume threshold crossed mid-month | Rates apply | Next month's rate steps only; current-month attributions keep the prior rate (per-order stamp) |
| AC-10 | Conduct drill | A cookie-stuffing signal; suspension → upheld termination | Ladder runs | Attribution frozen, balance held; forfeiture only of tainted earnings with notice + appeal log; legitimate history untouched without review |
| AC-11 | Buyer-price drill | Same cart via referral link vs direct | Totals compared | Identical totals — margin-funded, never surcharged |
| AC-12 | Cap drill | Exposure cap breached | Period evaluates | New-accrual review pauses; earned balances intact and visible |
| AC-13 | Computation drill | Tier change mid-month; partial refund; rounding fixture | Lines compute | Later attributions only; proportional netting; rounding (if any) favors affiliate + logged per line; integer VND |
| AC-14 | Cycle drill (sandbox) | Monthly batch with one seeded rail failure | Freeze → instruct → execute → reconcile | Statements + emails match; one failed line opens exactly one ops case and blocks nothing else |
| AC-15 | Duplicate-execution drill | A batch confirm re-run; double-instruct attempt while frozen | Each is attempted | Re-run is an idempotent no-op; double-instruct impossible |
| AC-16 | Tax drill | An affiliate without complete tax fields | Batch builds | Line blocked with a fix-it notice; earnings intact; identity fields were collected at enrollment |
