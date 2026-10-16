# Account Deletion - Non-Functional Requirements (feature 02)

Phase 1 BA · Quantified thresholds enforced in `docs/02-architecture-techlead/02-account-deletion/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

| NFR ID | Area | Requirement |
|---|---|---|
| NFR-01 | Deactivation latency | Public surfaces resolve `missing` within `<= 60 s` end-to-end (status cache bound); session revocation effective `<= 60 s` (feature 01 JWT bound) |
| NFR-02 | Grace period | Exactly 30 days (`requested_at + 30 d`); purge runs daily at 03:30 UTC; `purge_due_at` never silently resets |
| NFR-03 | Reminder | One email at day 23, delivered `± 2 h`; bounce surfaced to support within the same day |
| NFR-04 | Purge completion | 100% of due accounts purged or in reported retry within 24 h of `purge_due_at`; certificate emitted in the completing run |
| NFR-05 | Purge call budget | `<= 30 s` per participant call; bounded retries (3) with backoff; overall run must not overlap the next (single-flight lock) |
| NFR-06 | Idempotency | Re-running the same `runId` produces identical counts and no side effects; concurrent runs impossible (lock + DB guard) |
| NFR-07 | Probes | Weekly residual probe completes `<= 1 h` after start; any non-zero result pages ops immediately |
| NFR-08 | Certificate integrity | Counts reconcile to `0` residuals for the account; certificate retained 24 months minimum (audit baseline); readable only via audited route |
| NFR-09 | Collateral zero-tolerance | Zero cross-user deletes in the probe suite: purge handlers are `user_id`-scoped and parameterized; wildcard deletes fail test |
| NFR-10 | Data minimization | Certificate + deletion record contain ids, counts, timestamps only - no emails, names, or content |
| NFR-11 | Retention config | Legal floors (year counts, hashed-email allow-list) load from configuration; no values are hard-coded in specs or code |
| NFR-12 | Rate envelopes | Delete request/cancel 10 req/min/user; status reads 60 req/min/user; support certificate/status reads 30/min/operator; beyond -> `429` + `Retry-After` |
| NFR-13 | Audit | Request, cancel, purge start, participant results, certificate emission, support reads: append-only, 24-month retention, actor + target present |
| NFR-14 | Availability posture | Account-service outage delays new requests only; an in-flight purge re-runs safely next cycle; support reads fail closed, public reads follow the consumer's documented 60 s bound |
