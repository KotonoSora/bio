# Entitlement and Plans - Non-Functional Requirements (feature 35)

Phase 1 BA · Quantified requirements. All latency values are p95 unless stated; money/idempotency drill evidence lands with the task list in `docs/02-architecture-techlead/35-entitlement-and-plans/04-step-by-step-implementation-plan.md`.

| ID | Requirement | Target |
|---|---|---|
| NFR-01 | Grant-to-visible | Effective plan visible on all gates `<= 60 s` p95 after a verified confirmation (32 NFR-02), within the snapshot's `<= 30 s` consumption bound |
| NFR-02 | Snapshot staleness | Consumers cache the entitlement snapshot `<= 30 s`; a plan change self-heals within the bound; no event needed |
| NFR-03 | Public pricing read | `GET /api/v1/plans` p95 `<= 150 ms` at the edge with `Cache-Control: public, max-age=300` |
| NFR-04 | Plan status read | `GET /api/v1/billing/plan` p95 `<= 200 ms` including quota reads |
| NFR-05 | Grant transaction overhead | Ledger close/open adds `<= 20 ms` p95 to the grant transaction |
| NFR-06 | Debit transaction | One credit debit p95 `<= 50 ms` (insert event + counter update, single transaction) |
| NFR-07 | Tailor generation | p95 `<= 15 s`; hard budget 30 s -> `503 ai_unavailable` + credit return; output <= 8 suggestions, each <= 500 chars |
| NFR-08 | Config/catalog flips | Term activation/retire and config flips effective `<= 5 s` at runtime reads (32's config cache rule) |
| NFR-09 | Validation bounds | `yearlyDiscount <= 44%` and integral VND enforced at load and flip; invalid values refuse boot/activation; JD `1..10 000` chars |
| NFR-10 | Rate envelopes | Tailor: 10 requests/min/user (`429 throttled`); ops catalog routes: 20/min/operator; internal debit: 60/min/user; credit returns: 20/min/user |
| NFR-11 | Atomicity | Debits/counters/stamps never exceed totals (DB checks) under concurrency; grants and ledger writes are single-transaction; returns exactly-once per debit |
| NFR-12 | Audit and retention | Every grant, activation, retire, debit, and return appends audit in the same transaction; money audit retained 24 mo; ledger rows kept as financial records (PII scrubbed per `docs/01-requirements-ba/02-account-deletion/`) |
| NFR-13 | Privacy | RLS on `subscriptions`, `ai_credit_periods`, `ai_credit_events` (owner-scoped; ops context for support); job descriptions never logged; prompts/secrets never logged |
| NFR-14 | Localization | All user-facing copy ships as i18n keys with vi + en strings; error copy per `docs/01-requirements-ba/35-entitlement-and-plans/05-exception-handling-and-errors.md` |
| NFR-15 | Data honesty | No finance value, price, discount, or quota literal in code (grep gate); fixtures labeled and test-database-only; zero floats in money math |
| NFR-16 | Availability posture | Billing down -> snapshot fail-closed free (gates degrade, never crash); AI provider down -> `503` with the debit returned; catalog reads never block checkout retirement flows |
| NFR-17 | Observability | Metrics: `ledger.grant`, `ledger.superseded`, `plan.missing_row` (page-worthy), `entitlement_snapshot_miss`, `ai.debit`, `ai.return` (ratio alertable), `ai.return_conflict`, `tailor.latency`; all user-scoped by id only |
