# Subscription Lifecycle - Non-Functional Requirements (feature 36)

Phase 1 BA · Numbers with units; every claim checkable. Enforcement: `docs/02-architecture-techlead/36-subscription-lifecycle/05-security-logging-and-rate-limiting.md`.

| ID | Requirement | Threshold |
|---|---|---|
| NFR-01 | Job cadence | Exactly one run per 24 h, `02:10 UTC`; advisory lock; overlap = zero-write no-op |
| NFR-02 | Job duration and bounds | p95 <= 5 min; batched <= 500 rows per class per run; remaining rows roll to the next run |
| NFR-03 | Reminder timing accuracy | Fires within 24 h of the `-7d`/`-1d` threshold, never earlier than the threshold |
| NFR-04 | Cancel latency | p95 <= 200 ms server-side (excluding step-up) |
| NFR-05 | Trial start latency | p95 <= 300 ms; exactly one row per account lifetime |
| NFR-06 | Plan read latency | p95 <= 150 ms; config reads cached <= 5 s (`graceDays`) |
| NFR-07 | Grace cutoff | Time-based at `period_end + graceDays`; effective on any read even if the job never ran (no lockout before, no service after) |
| NFR-08 | Downgrade propagation | Domains <= 60 s (feature 04's gate); snapshot-driven surfaces <= 30 s; no write-back choreography |
| NFR-09 | Email delivery | Best-effort via 32's pipeline; backlog + failure rate metrics alert at > 1% failures per run; delivery never gates money state |
| NFR-10 | Idempotency | Job and enqueue reruns: zero duplicate writes, sends, or audits |
| NFR-11 | Audit retention | All lifecycle transitions (grace, renewed, cancelled, expired, downgraded, trial_started, period_fix) retained 24 months |
| NFR-12 | Data honesty gates | CI grep: no proration terms; no standing-charge/auto-renew/provider-subscription columns; zero occurrences allowed |
| NFR-13 | Privacy and masking | Recipient emails never logged in full (masked `a***@domain`); reminder payloads carry no card or provider secrets (none exist) |
| NFR-14 | Scoping | Every owner read/write passes RLS + server-set `user_id`; ops route requires role per request |
| NFR-15 | Session/step-up | Cancel requires step-up <= 10 min old; stale -> 403, no partial state change |
| NFR-16 | Degrade matrix | Job down, email seam down, pipeline down, or owning surface down: money state and ledger reads stay correct; no user lockout on any path |
| NFR-17 | Localization | Reminder and notice email templates render in the user's locale (EN/VN at launch) from server-side bodies; no client strings |
| NFR-18 | Observability | Metrics: reminders sent/failed per template, grace started, expired, renewed, cancelled, trial started, trial abuse signal, downgraded, period fixes, job duration - all label-scoped, no PII |
