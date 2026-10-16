# Entitlement and Plans - Acceptance Criteria (feature 35)

Phase 1 BA · BDD criteria; verified by the drills in `docs/02-architecture-techlead/35-entitlement-and-plans/02-database-schema-design.md` and the task list in `docs/02-architecture-techlead/35-entitlement-and-plans/04-step-by-step-implementation-plan.md`.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Grant becomes visible | A verified plan payment on any of the eight rails | The grant transaction commits | Exactly one `active` row exists (plan, stamped term, provider, order, period) and gates resolve pro within `<= 30 s`; a paid plan order with no ledger row fails the conformance drill |
| AC-02 | Product orders bypass the ledger | A paid course/tip/merch order | The grant completes | Zero subscription or credit rows change; fulfillment proceeds via `order.granted.v1` only |
| AC-03 | Free floor, three states | (a) new user, no history; (b) resolved user; (c) history without current row | Any resolution runs | (a)+(b) resolve `free` with exactly one materialized row; (c) fails loudly with a `plan.missing_row` alert - never silent free |
| AC-04 | One current row under concurrency | Two grants race for one user | Both transactions run | One row is current; the loser supersedes cleanly; the partial unique never yields two `active` rows |
| AC-05 | Upgrade mid-period | A pro-monthly user with consumed credits | Buys pro-yearly | Old row closes as `superseded` with audit; new period starts at grant; new quota stamps from the new term; old period's usage stays recorded |
| AC-06 | Grandfathering | A pending order stamped `v1` | A `v2` version is activated before the pending pays | The pending charges and grants `v1`; new quotes price `v2`; `v1` rows are never edited |
| AC-07 | Catalog guards | An active `v1` monthly row; pending `v1` orders | Ops activates another monthly / retires `v1` | Activation refused (`term_activation_conflict`); retirement refused while pendings exist (`term_retire_blocked`); both audited |
| AC-08 | Yearly derivation | Active monthly amount and `yearlyDiscount` config | A yearly term is drafted/activated | Amount equals `12 x monthly - discount` to the VND (half-up), stored; config > 44% refuses boot/flip; config unset yields `12 x monthly` and no saving claim |
| AC-09 | Debit atomicity | A pro user with `n` credits left | `n+1` debits run concurrently | Exactly `n` succeed; the last is refused (`429`); the counter never exceeds the total; each success has one keyed event row; replays return the original result |
| AC-10 | Failure returns the credit | A debit succeeded for a generation | The generation fails or overruns 30 s | One return event restores the credit (audited); a second return is refused (`return_conflict`); remaining credits reflect the return |
| AC-11 | Gates with exact copy | A free user and a zero-credit pro user | Each calls the tailor | `403 upgrade_required` and `429 tailor_quota_exhausted` (with reset date) render; no debit is attempted for the free user; no stack traces leak |
| AC-12 | Snapshot bound and fail-closed | A user with an active plan | Plan changes / billing goes down | Flip visible within `<= 30 s`; billing unreachable -> consumers resolve free; `entitlement_snapshot_miss` rises and recovers automatically |
| AC-13 | IDOR and role denial | A session for user B; a non-ops and an ops-only caller | Read A's plan / call ops routes | Foreign reads return 404 with no existence leak (RLS); activation without the finance role is 403; every ops action audited |
| AC-14 | Tailor sanitization | A JD containing injected instructions and markup | Generation runs | The system prompt is fixed server-side, the JD is passed as data, output is escaped text (<= 8 items), the JD never appears in logs, and the credit behaves per AC-09/10 |
