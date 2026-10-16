# Entitlement and Plans - Exception Handling and Errors (feature 35)

Phase 1 BA · Every failure is deterministic, audited where money is involved, and never renders a missing-plan state. Codes are RFC 9457 `code` strings; this feature appends to the money catalog (32) and reuses 17/26 codes where named.

## Catalog and pricing

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Plan intent without `term` | Browser/internal | Reject before pricing; no row created | 422 `term_required` "Choose a billing term" |
| `term` not `monthly`/`yearly` | Browser/internal | Reject; log metric | 422 `term_invalid` "That billing term is not available" |
| `free` plan intent | Browser/internal | Reject; free is never purchasable | 422 `plan_not_purchasable` "The free plan cannot be purchased" |
| No active `plan_terms` row for the pair | Browser/internal | Reject; pricing page shows launch state | 409 `plan_not_published` "Plan pricing is not available yet" |
| Pending order pays after a publication flip | Rail webhook | Charge the stamped row; no re-price | (no error - grant proceeds) |
| Retire while `pending` orders reference the row | Ops | Refuse; list the blocking order count | 409 `term_retire_blocked` "Pending orders still reference this version" |
| Activate with another active row for (plan, term) | Ops | Refuse; no partial state | 409 `term_activation_conflict` "Retire the current active version first" |
| Yearly draft without an active monthly row | Ops | Refuse; derivation impossible | 409 `term_pair_incomplete` "Publish the monthly term first" |
| Draft with amount <= 0, bad plan, or exceeded config bounds | Ops | Reject; nothing written | 422 `term_invalid` "Check the draft values" |
| `yearlyDiscount` config > 44% at load/flip | Ops/config | Refuse the value; boot or flip fails loudly | 422 `config_invalid` "Discount exceeds the allowed maximum" |
| Public pricing read before the pair exists | Browser | Serve no pricing block | 404 `plan_not_published` "Pricing is not available yet" |

## Ledger and projection

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Current row missing and no subscription history | Internal read | Materialize free row idempotently; resolve `free` | (none - normal first touch) |
| Current row missing but history exists | Internal read | Loud failure; `plan.missing_row` alert; never silent free | 500 "Plan could not be resolved - retry or contact support" |
| Billing unreachable during snapshot resolution | Internal chain | Snapshot error; consumers fail closed to free; `entitlement_snapshot_miss` rises | Gates show upgrade prompts quietly (no stack trace) |
| Duplicate grant (webhook replay) | Rail webhook | 34's terminal fast-path; no second ledger row | (no error - idempotent) |
| Concurrent grants for one user | Internal | Row lock serializes; partial unique leaves one current row | (no error - one winner audited) |
| Foreign user's plan read (session mismatch) | Browser/internal | RLS + owner check refuse; log | 404 (no existence leak) |

## AI credits and tailor

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Free user calls the tailor | Browser | Gate from the snapshot; no debit attempted | 403 `upgrade_required` "The AI tailor is part of Pro" |
| Pro user with zero credits | Browser | Debit refused atomically; no state change | 429 `tailor_quota_exhausted` "No credits left this period - resets {date}" |
| Debit called with an unknown kind | Internal | Registry check; refuse | 422 `kind_unknown` "Unknown credit kind" |
| Debit replay (same idempotency key) | Internal | Return the original result; counter untouched | (no error - idempotent) |
| Second return for one debit | Internal | Refuse; one return per debit | 409 `return_conflict` "Credit already returned" |
| Generation fails or exceeds 30 s budget | Browser | Return the credit exactly once; metric/alert on rate | 503 `ai_unavailable` "Generation failed - credit returned, try again" |
| JD over 10 000 chars or empty | Browser | Reject before debit | 422 `validation_failed` "Job description must be 1-10 000 characters" |
| Document not owned / absent | Browser | 17's absence path | 404 `resume_absent` "Resume not found" |
| Tailor request flood | Browser | Rate envelope | 429 `throttled` "Too many requests - slow down" |

## Ops and support

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Non-ops caller on catalog routes | Ops | Role check first | 403 `billing_ops_required` "Operator role required" |
| Ops caller tries to activate | Ops | Finance role required for prices going live | 403 `finance_role_required` "Finance approval role required" |
| Activate/retire without reason | Ops | Reject | 422 `validation_failed` "A reason is required" |
| Support reads another user's plan | Ops/internal | Ops context + audit only; no session path | 403 (no existence leak) |
