# Affiliate Tracking, Program, and Payouts - Non-Functional Requirements (feature 49) - GATED

Phase 1 BA · GATED: NFR-01/NFR-02 are live now (gate enforcement); the rest bind the future build at the amendment. Rates, thresholds, and caps are finance-owned config values (`affiliate.rates`, `affiliate.payout`) — never invented here.

## Gate enforcement (live now)

| ID | Requirement | Threshold |
|---|---|---|
| NFR-01 | Absence verification | Every release: schema diff + OpenAPI diff + source/bundle grep return zero affiliate surface; violation blocks promotion by tooling (AC-01) |
| NFR-02 | No-stub discipline | No affiliate table, route, job, chunk, or placeholder UI may exist in any other feature or release before the amendment |

## Future build (at amendment)

| ID | Requirement | Threshold |
|---|---|---|
| NFR-03 | Redirect latency | `/r/` redirect p95 ≤ 100 ms at the edge; cached affiliate lookup; no checkout-path dependency on the redirect |
| NFR-04 | Stats freshness | Clicks/conversions/pending/confirmed visible to the affiliate ≤ 60 s after the underlying event |
| NFR-05 | Window precision | Attribution windows respected to the second from server-side timestamps; no client-clock trust |
| NFR-06 | Precedence determinism | Every credit decision replayable from the evidence bundle (click id, code, timestamps, rate snapshot, order ref); identical inputs → identical outcome |
| NFR-07 | Anti-gaming budgets | Attribution rate-limits mirror checkout budgets; stuffing signals rejected at ingest; floods cannot skew dashboards |
| NFR-08 | Payout idempotency | Batch-line idempotency: retries and re-confirms never duplicate payouts; mid-batch failures block nothing else |
| NFR-09 | Dual control | Payout execution requires two approvers (same discipline as manual bank refunds); instruction file + statements generated atomically |
| NFR-10 | Money math | Integer VND throughout; rounding (if any) favors the affiliate and is logged per line; per-order rate snapshots are immutable |
| NFR-11 | Privacy and retention | First-party tracking only; raw click logs follow the analytics raw-event TTL precedent then aggregate-only; evidence bundles follow order retention; payout details vault-encrypted; zero secrets in logs |
| NFR-12 | Rate limits | Application intake 5/h/IP (Turnstile-guarded); affiliate stats 300/h; ops actions 120/h/operator; batch endpoints 30/h/finance; 429 with `Retry-After` |
| NFR-13 | Audit retention | Enrollment, status ladder, tier changes, batch freeze/instruct/confirm, reversals, forfeitures: 24 months minimum, full audit chain |
| NFR-14 | Observability | Metrics: clicks, conversion rate, pending/confirmed/reversed counts, batch outcomes, exposure vs cap, stuffing rejects — no buyer PII labels |
