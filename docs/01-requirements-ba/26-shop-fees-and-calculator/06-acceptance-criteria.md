# Sales Fees and Profit Calculator - Acceptance Criteria (feature 26)

Phase 1 BA · BDD; legacy drills preserved by name. Rate values in drills are labeled test fixtures, never production defaults.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Identity drill | fixture sales across tiers × kinds × rate versions | quote, charge, and ledger run | all three reconcile to the VND through the same function + pinned version |
| AC-02 | Display drill | a checkout with fee lines | shopper and seller views render | shopper's total equals the payable amount with no last-step additions; seller view decomposes gross → fees → net exactly |
| AC-03 | Version drill | a mid-checkout tier change and a rate publish | pricing applies | the open checkout keeps its price; only new checkouts use the new rate; closed-period figures stay immutable |
| AC-04 | Copy drill | one config version | schedule page, dashboard summary, and checkout lines render | identical figures (CI drift test) |
| AC-05 | Free/tip drill | zero-price and tip orders | ledger lines build | zero-price orders carry zero fees; tips carry the standard schedule |
| AC-06 | Math drill | fixture calculator inputs at rounding boundaries | quotes run | price/profit/margin reproduce hand-computed integer-VND results exactly under `nearest_1000` and `exact` |
| AC-07 | Rate-change drill | a config bump with saved scenarios and live prices | new quotes run | only new quotes change; scenarios and live prices stay unchanged until explicit republish (pinned `rate_version` verified) |
| AC-08 | Below-cost drill | a reverse-mode price under total cost | publish is attempted | blocked `below_cost_blocked` with the shortfall stated |
| AC-09 | Tier drill | live plan prices | tier comparison runs | break-even units match hand computation; a plan-price change moves the line; unpriced plans show pending |
| AC-10 | IDOR drill | user B scenarios/products | user A attempts reads/writes | 403 + RLS denies; zero rows touched |
| AC-11 | Dual-control drill | one operator drafts and tries to self-approve; then a second operator approves | publish runs | self-approval refused `dual_control_violation`; two-operator approval publishes; both audited |
| AC-12 | No-invention drill | this feature's directory | CI greps for literal fee percentages/rate amounts | zero matches; quotes render only from published config; pre-publication quotes refuse `schedule_not_published` |
