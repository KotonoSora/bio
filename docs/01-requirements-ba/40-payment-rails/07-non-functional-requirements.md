# Payment Rails - Non-Functional Requirements (feature 40)

Phase 1 BA · Numbers with units; enforcement: `docs/02-architecture-techlead/40-payment-rails/05-security-logging-and-rate-limiting.md`.

| ID | Requirement | Threshold |
|---|---|---|
| NFR-01 | Intake latency | Verify+persist+ack p95 ≤ 300 ms excluding grant txn (which commits before the ack); wallet authorize p95 ≤ 2 s from token receipt to verdict given PSP response |
| NFR-02 | Ack discipline | No rail contract success code before durable handling commits; transient code on any unhandled failure |
| NFR-03 | Sweep cadence | Sweeper tick every 15 min; stale threshold 30 min; bank lookback 48 h; ≤ 200 orders per rail batch; missed-tick alert after 2 consecutive |
| NFR-04 | Settlement SLA | Lost-notification payments settle ≤ 2 sweep ticks (45 min worst case) after the stale threshold; on-demand verify p95 ≤ 5 s |
| NFR-05 | Exception throughput | Queue intake ≤ 100 rows/min sustained; console read p95 ≤ 500 ms at 10k open rows |
| NFR-06 | Grace precision | Link eligibility window exact to the minute (`lateTransferGraceHours` 24 default; 1..72 config); boundary enforced server-side |
| NFR-07 | Dispute clock | Deadline shown with minute precision; alert ≤ 72 h remaining; auto-loss handling ≤ 15 min after deadline per provider rules |
| NFR-08 | Kill-switch latency | Delist effect ≤ 5 s (32's config semantics); settlement paths unaffected within the same window |
| NFR-09 | Rotation | Quarterly secret rotation drill; dual-read window ≥ 24 h; zero settlement failures attributable to rotation in drills |
| NFR-10 | Probe battery | Full battery per rail runs in CI ≤ 15 min per rail; 100% of probes have one of pass/fail-blocking outcomes (no warnings-only probes) |
| NFR-11 | Audit retention | Provider events, exceptions, dispute cases, sweeps, kill flips, rotation events retained 24 months; payloads stored as hashes + refs only |
| NFR-12 | Privacy | Zero PAN/token/cryptogram/secret material in logs, metrics, audits; PII masked (first/last 4) except ops case detail; no buyer identifiers in partner aggregates |
| NFR-13 | Scoping | Intake resolves users from stored orders only; ops routes role-checked per request; exceptions readable only by ops + finance; no buyer-facing enumeration of events exists (by absence) |
| NFR-14 | Rate limits | Intake: 600/min/IP + 120/min per rail signature key, burst 60/s; signature-mismatch clamp 20/min/IP; ops verify 1/30 s/order + 30/h/operator; all 429 with `Retry-After` |
| NFR-15 | Notification | Dispute open/loss, exception backlog > 24 h, and kill-switch flips alert ≤ 60 s via pipeline metric classes |
| NFR-16 | Localization | Buyer confirming/failure copy bilingual EN+VI; rail-facing codes are contract values, never localized; ops console EN |
| NFR-17 | Degrade | Rail API down → sweeps back off (≤ 4× tick), on-demand verify 502 `verify_unavailable`, intake unaffected; Redis down → throttles degrade to conservative fixed windows, correctness unchanged; provider_events DB down → refuse acks (transient) rather than settle unguarded; no state forks exist |
| NFR-18 | Observability | Metrics per rail: verify outcomes, duplicates, sweep settled/stale, exceptions by reason + age, disputes by status, grace links, kill-switch flips, secrets age — zero PII labels; security probes report per provider per run |
