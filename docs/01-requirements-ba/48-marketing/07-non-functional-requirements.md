# Marketing Consent and Broadcasts - Non-Functional Requirements (feature 48) - GATED

Phase 1 BA · GATED: NFR-01/NFR-02 are live now (gate enforcement); the rest bind the future build at the amendment. Volume-cap values are config-owned (`marketing.volume`) and set with ops at the amendment — never invented here.

## Gate enforcement (live now)

| ID | Requirement | Threshold |
|---|---|---|
| NFR-01 | Absence verification | Every release: schema diff + OpenAPI diff + source/bundle grep return zero marketing surface; violation blocks promotion by tooling (AC-01) |
| NFR-02 | No-stub discipline | No marketing table, route, job, chunk, or placeholder UI may exist in any other feature or release before the amendment |

## Future build (at amendment)

| ID | Requirement | Threshold |
|---|---|---|
| NFR-03 | Preference saves | Effective immediately (≤ 1 s to next-eligibility visibility); confirmation receipt issued every time |
| NFR-04 | Proof exports | Full consent history per address within ≤ 5 min of request; audit-logged |
| NFR-05 | Eligibility re-check | At confirm AND at send, every time; suppression beats grants in both checks |
| NFR-06 | Webhook transitions | Bounce/complaint state transitions ≤ 60 s from verified event; idempotent under replay |
| NFR-07 | Reputation guardrails | Complaint-rate breach → sends paused ≤ 60 s + ops paged ≤ 5 min; transactional delivery unaffected during pause |
| NFR-08 | Import scale | 10k-row CSV ≤ 5 min with exact row-level report; per-row idempotency |
| NFR-09 | Dispatch idempotency | Per-recipient idempotency: resumed batches never double-send; mid-batch pause halts ≤ 60 s |
| NFR-10 | Rate limits | Preference center 60/h/token; unsubscribe 30/h/token; import 10/h/operator; export 5/h/operator; re-consent 3/address/year; 429 with `Retry-After` |
| NFR-11 | Volume caps | Per-campaign and per-day ceilings from config; no hardcoded numbers exist in code |
| NFR-12 | Data minimization | Suppression checks use address hashes; zero addresses or tokens in logs beyond hashed refs; proof retention per policy |
| NFR-13 | Observability | Metrics: audience states by class, campaign stats, complaint rate vs threshold, pause events, import/export outcomes, proof exports — no address labels |
