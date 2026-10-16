# Social Connections - Non-Functional Requirements (feature 41)

Phase 1 BA · Numbers with units; enforcement: `docs/02-architecture-techlead/41-social-connections/05-security-logging-and-rate-limiting.md`.

| ID | Requirement | Threshold |
|---|---|---|
| NFR-01 | Connect latency | OAuth start p95 ≤ 300 ms; callback exchange p95 ≤ 2 s (provider-bound); connect-start rate-limited 20/h/user |
| NFR-02 | Encryption | AES-256-GCM envelope per row; master key versioned; rotation quarterly (≤ 90 days) with dual-read ≥ 24 h |
| NFR-03 | Refresh | Skew budgets per network (default 24 h before expiry); refresh attempts backoff ≤ 6 tries; health cycle every 15 min per account |
| NFR-04 | Revoke detection | Provider-side revokes reflected within one health cycle (≤ 15 min) or next refresh, whichever first |
| NFR-05 | Cap enforcement | Server-side at connect and dispatch; zero bypass paths (contract-tested); refusal ≤ 200 ms |
| NFR-06 | Disconnect | Row deletion immediate (≤ 500 ms); remote revoke attempt ≤ 5 s timeout; queued-target cancellation ≤ 60 s |
| NFR-07 | Log hygiene | Zero token-material occurrences in logs/queues/analytics/errors (CI grep gate on captured flows + static scan) |
| NFR-08 | Rotation | Re-wrap batches ≤ 500 rows; full re-wrap ≤ 60 min at 100k rows; zero publish failures during the window (drill-verified) |
| NFR-09 | Audit | Connection lifecycle + rotation events retained 24 months (append-only, actor-stamped); never contains token material |
| NFR-10 | Scoping | Owner-only row access via RLS; ops restricted view excludes token columns; cross-user reads 403/zero rows |
| NFR-11 | Privacy | Account refs are non-secret labels; PII minimized (no emails from providers stored beyond account ref); masking in ops views |
| NFR-12 | Degrade | Provider OAuth down → connect attempts fail honestly, existing connections unaffected; vault key unavailable → vault operations fail closed (no plaintext fallback); Redis down → rate limits degrade to conservative fixed windows |
| NFR-13 | Observability | Metrics: connections by network/health, refresh outcomes, revoke detections, cap refusals, rotation progress — zero token or handle labels beyond masked refs |
