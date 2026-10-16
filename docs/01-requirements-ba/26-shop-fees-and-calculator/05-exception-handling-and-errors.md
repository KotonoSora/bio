# Sales Fees and Profit Calculator - Exception Handling and Errors (feature 26)

Phase 1 BA · Codes are stable; UI copy localizes vi/en. No error message may state a rate value that is not read from published config.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| No ratified schedule published | API | 409 `schedule_not_published`; zero copy rendered | "Fee schedule isn't published yet." |
| Schedule read pre-publication | API | 404 `schedule_not_published` | branded empty state |
| Malformed quote input (negative gross, unknown kind) | API | 422 `quote_invalid` | "Check the quoted amounts and type." |
| Ops publishes with the same operator | API | 409 `dual_control_violation`; draft stays unpublished | (ops-facing JSON only) |
| Non-finance role attempts publish | API | 403 `finance_role_required` | (ops-facing JSON only) |
| Structural invariant violation (free < pro; linkout ≠ 0) | API/DB | 422 `schedule_invalid`; publish refused | (ops-facing JSON only) |
| Rate version edited after publish | DB | append-only enforced; update denied | (ops-facing JSON only) |
| Merch quote without a cost entry | API | 422 `cost_unknown`; no guessed cost | "Set the partner cost for this template first." |
| Reverse-mode price below total cost (publish attempt) | API | 422 `below_cost_blocked` with the shortfall | "This price is below cost by {amount} — raise it." |
| Scenario from a missing/foreign owner | API | 404 / 403 + RLS deny | "You don't have access to this scenario." |
| Stale pinned version on requote | UI | old-vs-new diff shown; republish is explicit | "Rates changed — review before republishing." |
| Bare unpriced plans in tier comparison | UI | break-even shows "pending" | "Break-even shows once plan prices are live." |
| Checkout quote mismatch (quote ≠ charged) | System | settlement halts; finance paged (38 forward) | (internal alert; buyer unaffected until resolved) |
| Buyer sees a fee line (display violation) | Test gate | CI display drill fails the build | - |
| Fee literal found in this directory | Test gate | CI grep fails the build | - |
