# Custom Domains - Non-Functional Requirements (feature 04)

Phase 1 BA · Quantified thresholds enforced in `docs/02-architecture-techlead/04-custom-domains/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

| NFR ID | Area | Requirement |
|---|---|---|
| NFR-01 | Resolve latency | Host resolve p95 `<= 5 ms` cache-hit / `<= 100 ms` origin; unknown-host neutral error `<= 150 ms` edge-rendered |
| NFR-02 | Verification timing | On-demand check result `<= 60 s` after click (DNS TTL permitting); worker first poll `<= 60 s` after add; pending cadence 30 s, 2 m, 5 m, 15 m, 1 h, then 6 h within the 7-day window |
| NFR-03 | Recheck cadence | Live bindings rechecked every 24 h staggered (`±10%` jitter); control loss detected `<= 24 h`; denylist re-evaluated each recheck |
| NFR-04 | Serving stop/start | Lapse stop and re-upgrade restore `<= 60 s` end-to-end; explicit removal halts serving at the delete commit |
| NFR-05 | Cache | Positive resolve cache 30 s, negative 60 s; SSR keys namespaced per host and username-tagged; purge-on-publish flushes both key families |
| NFR-06 | TLS | Managed issuance on verification, auto-renew before expiry with a 14-day early window; issuance/renewal failure withholds serving (fail-safe) and pages ops |
| NFR-07 | Bounds | Hostname `<= 253` total, labels `1-63`; challenge token 32 bytes, single-use, stored hashed; challenge window 7 days |
| NFR-08 | Rates | Add 10/day/user; verify/recheck 10/min/user; list 60/min/user; internal resolve service budget; `429` + `Retry-After` beyond; zen-like add/verify cycling also trips abuse counters |
| NFR-09 | Privacy classes | Unknown, unverified, private, and deactivated all render the identical neutral error shape and timing class (`<= 50 ms` p95 interleaved difference) |
| NFR-10 | Auto-release | `unverified` with failing control 14 days -> released; `suspended` beyond the 30-day hold -> `unverified` + fresh challenge required |
| NFR-11 | Audit | Add, verify, recheck result changes, lapse, restore, removal, reclaim, abuse suspension: append-only, actor + target, 24-month retention |
| NFR-12 | Quotas | `<= 10` hostnames per account; exceeding -> `422` with removal guidance, never silent eviction |
