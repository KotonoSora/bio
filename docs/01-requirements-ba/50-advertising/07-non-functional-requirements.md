# Advertising (Inventory, Serving, Creator Share, Retargeting) - Non-Functional Requirements (feature 50) - GATED

Phase 1 BA · GATED: NFR-01/NFR-02 are live now (gate enforcement); the rest bind the future build at the amendment. Pricing, rates, thresholds, and caps are finance/ops config values (`ads.*`) — never invented here.

## Gate enforcement (live now)

| ID | Requirement | Threshold |
|---|---|---|
| NFR-01 | Absence verification | Every release: schema diff + OpenAPI diff + source/bundle grep return zero ad surface; violation blocks promotion by tooling (AC-01) |
| NFR-02 | No-stub discipline | No ad table, route, job, chunk, or placeholder UI may exist in any other feature or release before the amendment |

## Future build (at amendment)

| ID | Requirement | Threshold |
|---|---|---|
| NFR-03 | Render budget | Ad decision ≤ 50 ms; collapse-on-timeout; measurement pings fire-and-forget, never render-blocking; zero layout shift |
| NFR-04 | Decision caching | Per (placement, coarse segment) with short TTL; eligibility recomputed at each cache miss; deterministic tie-breaks |
| NFR-05 | Counting honesty | Dedupe window 10 min per visitor/slot/creative; viewport-verified impressions only; filter counts always reported |
| NFR-06 | Outage tolerance | Measurement outage: bounded-retry queue + sweep-and-repair; no silent loss, no double count (dedupe keys) |
| NFR-07 | Takedown latency | One audited action pauses a creative everywhere ≤ 5 min |
| NFR-08 | Earnings cadence | Daily estimates ≤ 24 h behind events; monthly close → confirmed; payout cycle matches 49's runner |
| NFR-09 | Money math | Integer VND; rate snapshots versioned, forward-only; clawbacks proportional to tainted share only; zero silent adjustments |
| NFR-10 | Segment freshness | Minutes-scale evaluation; lag labeled in-UI; sizes shown with `as of`; no per-hit synchronous writes |
| NFR-11 | Privacy | First-party only; coarse targeting only (no behavioral profiling, no cross-site identity); membership TTL expiry; opaque per-creator refs; zero visitor PII in advertiser payloads |
| NFR-12 | Rate limits | Click redirect 600/h/IP (edge budgets); decision internal 3000/h/service; segment preview 120/h/creator; ops actions 120/h/operator; 429 with `Retry-After` |
| NFR-13 | Retention | Raw NDJSON per the analytics TTL precedent then aggregate-only; evidence bundles per billing/dispute retention; statements per finance retention |
| NFR-14 | Observability | Metrics: impressions/clicks/CTR, filter counts by reason, quarantine volumes, decision latency, collapse rate, earnings/clawbacks, segment sizes — no visitor labels |
