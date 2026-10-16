# Refunds - Non-Functional Requirements (feature 38)

Phase 1 BA · Numbers with units; enforcement: `docs/02-architecture-techlead/38-refunds/05-security-logging-and-rate-limiting.md`.

| ID | Requirement | Threshold |
|---|---|---|
| NFR-01 | Request latency | p95 <= 250 ms server-side (Turnstile excluded) |
| NFR-02 | Ops action latency | p95 <= 300 ms for approve/reject; execute returns as soon as `executing` commits (PSP call is async beyond the first attempt) |
| NFR-03 | Execution SLA | API rails: first attempt within 1 business day of approval, target p95 <= 5 min after execute; manual rails: <= 3 business days after approval (Q-03) |
| NFR-04 | Retry budget | <= 5 attempts within <= 24 h, exponential backoff (1 min → 15 min → 1 h → 6 h caps), then `failed` + alert; zero unbounded retries |
| NFR-05 | Idempotency | One refund row per order; retried requests/executions with the same key perform zero duplicate writes, calls, notes, or audits |
| NFR-06 | Atomicity | The final transaction commits order edge + row + note + revocation + marker together or nothing; no half-revoked state exists |
| NFR-07 | Audit retention | All request/approve/reject/execute/fail rows retained 24 months with actor identity; two-person pairs provable |
| NFR-08 | Privacy | No PAN/card/bank material anywhere; reason text plain; emails masked (`a***@domain`); PSP refs are transaction ids only |
| NFR-09 | Scoping | Owner routes RLS + server-set `user_id`; ops routes role-checked per request; internal route service JWT + reason allowlist |
| NFR-10 | Rate limits | Request 3/day/order + 10/day/IP; reads 60/h/user; ops actions 30/h/operator; 429 with `Retry-After` |
| NFR-11 | Notification | Credit-note email enqueued <= 60 s after reversal; transactional class; unresolved recipient = skip + metric (37's rule) |
| NFR-12 | Event delivery | At-least-once with marker + reconciliation (re-emit once); consumers dedupe; event bus downtime never blocks finality |
| NFR-13 | No-default gate | CI grep: zero hardcoded provider defaults, fallback orderings, or cross-rail branches in refund code; unknown provider = reject |
| NFR-14 | Localization | Buyer-facing copy (tracker, emails, credit note) bilingual EN+VI per 37's Q-01; internal codes never surface |
| NFR-15 | Degrade | PSP down, event bus down, pipeline down, or billing down: money truth never forks; browser reads return last consistent state; manual rails depend on no PSP |
| NFR-16 | Observability | Metrics: requests, approvals, rejections, executions, failures by rail/kind, retry attempts, SLA age of queue rows, `refund.emitted` mismatches — no PII labels |
