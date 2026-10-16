# Agency Client Reporting - Non-Functional Requirements (feature 47)

Phase 1 BA · Numbers with units; enforcement: `docs/02-architecture-techlead/47-agency-client-reporting/05-security-logging-and-rate-limiting.md`.

| ID | Requirement | Threshold |
|---|---|---|
| NFR-01 | Report latency | Per-profile read ≤ 500 ms p95 cached / ≤ 1.5 s p95 cold; combined view ≤ 1 s p95 at 50 profiles |
| NFR-02 | Cache discipline | Access facts ≤ 60 s TTL; metric reads ≤ 15 min TTL with last-good timestamp on degrade; zero stale-as-fresh renders |
| NFR-03 | Purge latency | Unlink/membership events purge caches ≤ 60 s and revoke shares immediately (event-driven) |
| NFR-04 | Snapshot cadence | `leaderboard_snapshots` daily ±15 min of schedule; idempotent per (org, day, metric); misses alerted after 2 |
| NFR-05 | Exactness | Report values byte-equal to the creator dashboard for the same period (same read components); rollup = exact sum; VND/CTR math owned by the source features |
| NFR-06 | Share security | Signed tokens hashed at rest, expiry ≤ 30 days, `noindex`, read-only; revocation immediate; zero enumeration |
| NFR-07 | Periods | 7/30/90/custom ≤ 366 days; timezone + boundaries stated on every value |
| NFR-08 | Audit retention | Share create/revoke + saved-view changes 24 months; individual reads not audited; ops reads scoped + audited |
| NFR-09 | Rate limits | Report reads 600/h/user; combined 120/h/user; share create 30/h/org; snapshot/ops reads 120/h/operator; 429 with `Retry-After` |
| NFR-10 | Degrade | Access read down → scoped reads fail closed beyond cache; metric source down → section reason + last-good; billing summary down → revenue hides honestly; Redis down → conservative fixed windows; snapshot job down → latest standing shown honestly |
| NFR-11 | Observability | Metrics: report reads by surface, cache hit rate, purge latency, share states, snapshot results, parity mismatches, degraded sections — zero visitor-level or cross-org labels |
