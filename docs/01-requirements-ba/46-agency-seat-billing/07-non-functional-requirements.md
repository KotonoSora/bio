# Agency Seat Billing - Non-Functional Requirements (feature 46)

Phase 1 BA · Numbers with units; enforcement: `docs/02-architecture-techlead/46-agency-seat-billing/05-security-logging-and-rate-limiting.md`.

| ID | Requirement | Threshold |
|---|---|---|
| NFR-01 | Snapshot job | Daily, one row per org, completion within ±15 min of the fixed schedule; idempotent per (org, day); misses retried, never fabricated |
| NFR-02 | Overview latency | ≤ 300 ms p95 (snapshots + subscription row; no live roster fan-out on the read path) |
| NFR-03 | Money math | VND integers only; `round(Σ seat-days × price ÷ cycleDays)` half-up; exact to the VND in fixtures; zero fractional amounts exist |
| NFR-04 | Reconciliation | Daily job ≤ 5 min for 10k orgs; mismatches alerted ≤ 5 min after detection; corrections audited with reason |
| NFR-05 | Evidence immutability | Closed-period snapshots + invoices immutable (no UPDATE/DELETE grants); one-hop trace ≤ 2 clicks from overview |
| NFR-06 | Renewal settlement | True-up computed and attached to the next renewal order within the standard lifecycle window (36); no mid-cycle surprise charges |
| NFR-07 | Audit retention | Subscriptions, snapshots, true-ups, transfers, cancellations, corrections retained 24 months; ids + amounts only, never instruments |
| NFR-08 | Privacy | Contact/owner RLS scoping; counts-only staff shape carries zero amounts; client surfaces nonexistent; zero token material |
| NFR-09 | Rate limits | Overview 300/h/org; subscribe/cancel 20/h/org; transfer 10/h/org; reconciliation reads 120/h/operator; 429 with `Retry-After` |
| NFR-10 | Degrade | 45's seats route down → job retries, no fabricated snapshots, alert after 2 misses; provider rails down → standard 36 dunning; Redis down → rate limits degrade to conservative fixed windows; billing reads unaffected |
| NFR-11 | Observability | Metrics: seats by org, snapshot job success/misses, true-up totals, mismatches, transfer states, lapse counts — zero instrument or token labels |
