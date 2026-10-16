# Short-Link Analytics - Non-Functional Requirements (feature 31)

Phase 1 BA · Every threshold numeric.

## Performance and freshness

| # | Requirement |
|---|---|
| NFR-01 | Resolve latency delta with counting enabled vs disabled ≤ 5 ms p95 (feature 29's gate; counting is post-response only). |
| NFR-02 | Freshness: hits appear in readouts p95 ≤ 5 min from the open; consumer flush ≤ 60 s batches; `generated_at` + lag shown on every readout. |
| NFR-03 | CSV streamed; 90-day single-code export completes p95 ≤ 30 s; no full-history in-memory assembly. |
| NFR-04 | Quarantine alert fires ≤ 1 min after the velocity threshold trips. |

## Filters and thresholds

| # | Requirement |
|---|---|
| NFR-05 | Dedupe window 10 min per visitor/link; visitor key = salted hash with 24 h salt rotation; no raw IP or visitor id at rest. |
| NFR-06 | Velocity threshold: ≥ 500 hits/minute from one source → quarantine branch; legitimate traffic unaffected. |
| NFR-07 | Bot/prefetch posture: money-tied counting uses the strictest setting; the shared matrix is recorded once; divergences labeled. |

## Evidence and retention

| # | Requirement |
|---|---|
| NFR-08 | Raw NDJSON retention 90 days (repair + dispute window); aggregates follow the account's plan window via feature 13's prune job (free 90 d / pro 365 d); quarantine rows until decided; audit 24 months. |
| NFR-09 | Repair within the raw window restores totals with no loss and no doubles; raw gaps alert ≤ 5 min after detection. |
| NFR-10 | Replay lookups and decide actions are ops-only and audited; creators have no raw-evidence route (probe → 404). |

## Security and privacy

| # | Requirement |
|---|---|
| NFR-11 | Owner scoping + RLS on all three tables; foreign reads 404/zero rows; ops context explicit and audited. |
| NFR-12 | No visitor-id columns exist; cross-link identity resolution is unqueryable by construction; counts are aggregate-safe regardless of consent state (no identity involved). |
| NFR-13 | Rate envelopes: stats 120/min per session; CSV 6/h per user; ops routes 30/h per operator. |

## Localization and accessibility

| # | Requirement |
|---|---|
| NFR-14 | vi/en copy; numbers/curves localize; CSV stays machine-stable (no localized headers). |
| NFR-15 | Estimated badges announced (aria-label), not color-only; curves have tabular alternatives; equations readable as text. |
