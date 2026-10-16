# Automate Contacts - Non-Functional Requirements (feature 43)

Phase 1 BA · Numbers with units; enforcement: `docs/02-architecture-techlead/43-automate-contacts/05-security-logging-and-rate-limiting.md`.

| ID | Requirement | Threshold |
|---|---|---|
| NFR-01 | Capture latency | Row written ≤ 500 ms after send acceptance (in the same transaction as the intent transition); zero capture for failed sends |
| NFR-02 | Eligibility latency | Single check ≤ 50 ms p95 (indexed suppression/status lookup); every sender path calls it — zero bypasses exist |
| NFR-03 | Suppression effect | ≤ 5 s from mechanism to exclusion on all send paths (fresh reads); idempotent across repeat actions |
| NFR-04 | Export | CSV build ≤ 30 s at 100k rows; download link expires ≤ 24 h; handoff ≤ 10 rows/s (27's endpoint pacing) |
| NFR-05 | Quota precision | Free 100/year and pro 100k/month exact to the period boundary (minute precision); unique recipient per period counts once; one counting function (gate = meter = reconciliation) |
| NFR-06 | Hygiene cadence | Prune/merge/park job daily; 12-month stale boundary exact to the day (`staleContactMonths`); reactivation only via fresh consent event |
| NFR-07 | Audit retention | Capture, suppression, merges, prunes, parks, exports, handoffs retained 24 months; counts + ids only, never message bodies |
| NFR-08 | Privacy | Opaque network refs only (no username scraping/lookup); PII minimized; ops sees aggregates only; RLS owner-scoped; zero token material |
| NFR-09 | Rate limits | Export 6/h/creator + 30/day/creator; list reads 600/h; suppression 120/h; removal-center 30/h/IP token attempts; 429 with `Retry-After` |
| NFR-10 | Degrade | Provider signals down → suppression still works via keyword + removal center; Redis down → rate limits degrade to conservative fixed windows; hygiene job down → rows stay active (sends still gated by quota + suppression) |
| NFR-11 | Observability | Metrics: captures, suppressions by mechanism, exports, handoff imported/skipped, quota holds, hygiene counts by status — zero PII labels |
