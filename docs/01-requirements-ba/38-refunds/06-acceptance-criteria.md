# Refunds - Acceptance Criteria (feature 38)

Phase 1 BA · BDD checks mapped to `docs/02-architecture-techlead/38-refunds/04-step-by-step-implementation-plan.md`.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Eight-rail drill | One paid order per registry member (payos, sepay, vnpay, zalopay, napas, googlepay, applepay, stripe) | Request → approve → execute runs per member posture | Each lands `refunded` with its member's PSP/manual reference recorded; entitlements revoked per rules; re-grant refused; audit chain complete |
| AC-02 | Manual evidence + two-person | A sepay order approved by ops A | Ops A tries to execute without evidence, then ops B executes with evidence | 422 then `two_person_required`-free success; evidence row precedes the transition; both identities audited |
| AC-03 | Idempotency drill | A retried request and a retried/interleaved execution (same keys; concurrent execute attempts) | Both run | Single refund row; single reversal; races resolve to one outcome; zero duplicate credit notes or audits |
| AC-04 | Eligibility drill | Non-paid orders, a tip order, a disputed order | Requests attempted | `refund_ineligible` with terminal reason; `tip_final`; dispute copy; no rows minted for refusals |
| AC-05 | No-default drill | Unknown provider code injected | Routing runs + CI grep gate scans | Rejected with no fallback call; gate finds zero hardcoded defaults or cross-rail fallback in refund code |
| AC-06 | IDOR probe | Two users; non-ops on ops routes | Cross-user reads/requests and role-lifted actions attempted | 403/404 parity; RLS denies at DB; `billing_ops_required` on ops routes |
| AC-07 | Plan revocation drill | Active pro row created by order X (extension recorded from order Y) | Refund X, then separately refund Y in a fresh fixture | X-refund: row closes `expired`, free floor opens, `plan.refunded` audited, surfaces downgrade losslessly; Y-refund: same matcher path; re-buy restores |
| AC-08 | Credit note chain | A reversed refund after 37 ships | Receipt reopened; credit-note email checked | `CN-<orderCode>-1` linked to `RCPT-<orderCode>`; append-only; email sent once; balances read zero |
| AC-09 | Consumer invalidation | `order.refunded.v1` published with the frozen shape | 23/24/25 consume | Pre-print jobs cancel; enrollment + certificate revoke with courtesy window; booking states settle; all idempotent on redelivery |
| AC-10 | Retry budget | PSP fails every attempt | Executor runs | ≤ 5 attempts over ≤ 24 h, then `failed` + alert; ops re-execute after fix succeeds exactly once; tracker copy stays honest |
| AC-11 | Full-only guard | A partial amount is attempted through every path | Requests/executions attempted | Refused or normalized to full (`amount_vnd` equality re-asserted); `uniq_rf_order` blocks stackings |
| AC-12 | System-class flow | A `hold_expired` booking refund on an API rail, and one on a manual rail | Internal call lands | API rail auto-executes; manual rail queues for ops with evidence; credit-note email cites the policy reason |
