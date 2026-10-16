# Concierge Managed Service - Non-Functional Requirements (feature 05)

Phase 1 BA · Quantified thresholds enforced in `docs/02-architecture-techlead/05-concierge-managed-service/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

| NFR ID | Area | Requirement |
|---|---|---|
| NFR-01 | Board latency | Task board read p95 `<= 200 ms`; task transitions committed `<= 1 s` |
| NFR-02 | SLA clock | Timestamps recorded at action commit; board clock display stale `<= 5 min`; response/completion windows config-driven per tier (values from the staffing cost model before first sale) |
| NFR-03 | Approval integrity | Approval rows append-only (no update/delete grants); tamper attempts audited; replay of signed-email confirmations rejected |
| NFR-04 | Dispatch gate | Concierge-origin dispatch verification fail-closed: if the approval check is unavailable, dispatch refuses (`<= 100 ms` added p95 on dispatch decisions, cached `<= 30 s` per approval id) |
| NFR-05 | Work log | Operator actions visible in client history `<= 5 min`; audit retention 24 months |
| NFR-06 | Staffing presence | Activation check is a presence query answered `<= 1 s`; refusal is deterministic (no partial activation) |
| NFR-07 | Rates | Client task requests 20/day/contract; approves 60/min/user; internal ops routes 120/min/operator; `429 throttled` + `Retry-After` beyond |
| NFR-08 | Retention/minimization | Post-exit concierge rows keep ids, states, timestamps only (no client content) for 24 months; work references remain valid links |
| NFR-09 | Scoping | RLS owner predicate on every concierge table; operators see only assigned contracts; client content only through Act-as-user (agency audit) |
| NFR-10 | Availability | Client board target 99.9%; pause/exit routes durable (no lost pending transitions on restart) |
| NFR-11 | Localization | vi/en parity for all client-facing copy; operator surfaces internal-language only |
