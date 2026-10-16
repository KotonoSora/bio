# Sales (Ledger, Analytics, Order Console) - Exception Handling and Errors (feature 51) - GATED

Phase 1 BA · GATED: at amendment; problems follow RFC 9457 (`code` field). No buyer PII or cross-order data leaks beyond the caller's scope; provider-native codes never reach buyers.

| Scenario | Path Type | System Handling | Displayed Error Message |
|---|---|---|---|
| Duplicate/replayed grant webhook | System | UNIQUE guard emits nothing; zero additional lines | None |
| Grant without sales line | System | Impossible by construction (same transaction); a drill asserts it | Internal alert if ever observed |
| Refund with no matching line | System | Adjustment refused with reason (no orphan adjustments) | Ops: "No sales line for this order — check grant state." |
| Correction attempted as an edit | System | No UPDATE/DELETE path exists; correction appends a reasoned adjustment | Ops: "Corrections are appended, never edited." |
| Fee data never confirmed | Read | Net-of-known shows gross with fees unconfirmed; no fiction | Finance view labels "fees unconfirmed" |
| Unknown identifier in lookup | API | Clean no-result; no cross-order leakage | "No order found." |
| Stale-view action | API | Fails closed at execution; order state re-validated | "This order changed — review the current state." |
| Ineligible-state action | API | Refused with stated preconditions | "Refund isn't available from this state." |
| Support tier reaches for ops action | API | 403 with reason; role gate enforced | "Requires ops role." |
| Self-approval attempt | API | Maker-checker refuses | "A second approver is required." |
| Expired approval execution | API | Refused; approval stays dead | "This approval expired — propose again." |
| Bulk action partial failure | Flow | Per-order results; failed items listed with reasons | "12 of 15 succeeded — download results." |
| Buyer probes ops endpoints | API | 403; RLS denies cross-scope | "Not found." |
| Non-finance role on analytics | API | 403 | "Not available." |
| Affiliate/creator probes business cuts | API | Scoped endpoints only; business cuts denied | "Not found." |
| Post-close adjustment arrives | System | Appends to the open period; frozen snapshot unchanged | None |
| Close snapshot re-run mismatch | System | Blocked — re-runs must be byte-identical; ops alert | Internal alert only |
| Provider scorecard threshold breach | System | Ops paged with the slice attached | Ops channel |
| Anomaly spike (dispute/funnel cliff) | System | Alerts vs trailing baseline before manual review would catch it | Ops channel |
| Kill-switch flip | System | Funnel shift visible same-day; decision recorded | Ops log |
| Case with internal notes | Flow | Buyer mirror shows "under review" only | Buyer: "Under review." |
