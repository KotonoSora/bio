# Promo Codes - Acceptance Criteria (feature 39)

Phase 1 BA · BDD checks mapped to `docs/02-architecture-techlead/39-promo-codes/04-step-by-step-implementation-plan.md`.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Launch-guard drill | A cap-less or window-less campaign | Create/launch attempted | Rejected at validation and at the column level; unbounded codes uncreatable |
| AC-02 | Cap-race drill | Concurrent creates at 99% burn | Redemptions race | Exactly the remaining budget succeeds; the rest get clean neutral rejections; exposure never exceeds the cap; auto-pause fires at 100% |
| AC-03 | Pause drill | A live campaign with pending quotes | Pause, wait 5 s, attempt new validation + pay an in-flight order | New validations block; the in-flight order grants with `coupon.in_flight_honored`; resume reopens cleanly |
| AC-04 | 0-VND drill | A 100% percent or full fixed code | Checkout runs to grant | No rail is contacted; `provider='promo'`; immediate canonical grant; receipt renders; a direct client `promo` provider attempt gets `unknown_provider` |
| AC-05 | Kind-pricing drill | Percent, fixed, and term_extend codes over varied amounts | Checkout prices each | Math matches the formulas exactly (integer VND); term_extend adds ledger days and no discount; discounts never exceed amount |
| AC-06 | Oracle drill | The five rejection classes + brute force | Validate runs and timings are measured | All non-expired classes are indistinguishable (copy + ≤ 50 ms spread); only expiry states itself; throttles engage |
| AC-07 | Single-use race drill | 10 concurrent grants of one single-use code | Redemptions race | Exactly one consumes (`redeemed`, attribution stamped); the other nine resolve per in-flight rules; zero duplicate ledger writes |
| AC-08 | Batch drill | A 100k-code stress batch | Generation, export, ack, second export attempted | Format/entropy/alphabet hold; zero collisions; plaintext once; ack audited; second export 409; hashes-only thereafter |
| AC-09 | Void drill | Redeemed + unredeemed codes in a batch | Bulk-void runs | Only unredeemed flip `voided` (audited count); redeemed discounts stand; voided codes reject neutrally |
| AC-10 | Credit application drill | Unconsumed coupon/referral/goodwill days + an active row | Daily job runs | All days apply to `period_end`; ledger rows consumed once with subscription id; `plan.credits_applied` audited; rerun writes nothing; no row -> days wait |
| AC-11 | Stacking drill | Coupon + trial, coupon + credits, two coupons | Checkout paths run | Matrix honored exactly; replacement needs confirmation; renewal charges full catalog price with honest copy |
| AC-12 | Attribution drill | Coupon grants across campaigns/channels | Ledger + subscription rows inspected | `campaign_id`/`coupon_id`/`discount_vnd` on orders; `source='coupon'` + `source_ref=campaign_id` on the granting subscription; partner aggregate shows zero PII |
| AC-13 | Auth + abuse drill | Non-ops callers; a velocity anomaly | Ops routes and anomaly paths exercised | 403s everywhere; campaign auto-pauses with system audit; no auto-bans; buyers cannot enumerate anything |
| AC-14 | Compensation drill | Coupon orders that expire or cancel before payment | Terminal transitions run | Budget decrements restored exactly once per order; a rerun of the sweep restores nothing twice |
