# Agency Seat Billing - Acceptance Criteria (feature 46)

Phase 1 BA · BDD; drills run against `docs/02-architecture-techlead/46-agency-seat-billing/` implementations.

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Count drill | Fixture roster churn (invite/accept/suspend/remove across a month) | Billable seats are computed from snapshots | Hand-computed counts match exactly; clients never counted; pending invites unbilled; suspensions stop at the date |
| AC-02 | Pro-rate drill | Mid-cycle seat add and remove; a renewal | The cycle charge + true-up compute | Day-weighted lines exact (`round(Σ seat-days × price ÷ cycleDays)`, half-up, VND integers); positive delta on the next invoice; negative as account credit |
| AC-03 | Transfer drill | A billing-contact transfer with one or both confirmations | Each path runs | Both confirmations switch billing + audit; single-sided attempt leaves billing untouched + audit; expiry at 7 days restarts cleanly |
| AC-04 | Dispute drill | Any invoice line; later roster changes | The line is traced; the closed period is re-read | One-hop snapshot trace from the overview; closed-period snapshots + invoices immutable; corrections forward-only with reason |
| AC-05 | Lapse/no-hostage drill | A lapsed subscription (grace ended) | Staff powers and client surfaces evaluate under the same session | Invites + act-as starts refuse `agency_subscription_required`; roster readable; client pages live; cure restores tooling |
| AC-06 | IDOR drill | Non-contact reads/writes (manager amounts, client surface, cross-org refs) | Each is attempted | 403/404 with zero rows; managers get counts-only shape; client route does not exist |
| AC-07 | Catalog drill | Catalog missing agency values; then values present | Subscribe + overview run | Absent → `catalog_incomplete`, nothing invented; present → checkout via 33 with explicit provider, grant activates, catalog version stamped |
| AC-08 | Reconciliation drill | A snapshot/roster mismatch fixture | The daily job + correction run | Alert raised; correction forward-only with reason + audit; closed periods untouched |
