# Earnings Dashboard - Non-Functional Requirements (feature 15)

Phase 1 BA · Quantified thresholds enforced in `docs/02-architecture-techlead/15-earnings-dashboard/`. Extends `analytics-service` (12-14); zero new tables; revenue sources owned by shop 22-26 / monetize 32-40.

## Performance and capacity

| # | Requirement | Target |
|---|---|---|
| NFR-01 | Read latency | Engagement-only unified read p95 <= 300 ms; with money sources p95 <= 800 ms warm |
| NFR-02 | Fan-out bound | <= 6 source reads per view (one local aggregate read + flagged remote rollups); 2 s timeout per source; partial failure degrades cell groups, never the view |
| NFR-03 | Payload bounds | 25 rows/page server-side; page <= 32 KB; series `points` cap 120 with exact totals |
| NFR-04 | CSV streaming | Chunked, backpressure-safe; server working memory <= 32 MB per export; cancel stops the stream within 1 s (pull-based) |
| NFR-05 | Idle discipline | Zero requests when idle; refetch on focus + manual only (hourly data, no interval polling) |

## Correctness and freshness

| # | Requirement | Target |
|---|---|---|
| NFR-06 | Formulas | CTR consumed from `ctr_x1000` (13); `rpc = revenue / clicks` same-window, round-half-up, null when clicks = 0; mismatched windows fail review |
| NFR-07 | No restatement | Zero dashboard-side net math; zero money tables; violation gates fail review (AC-11) |
| NFR-08 | Totals integrity | Engagement sums always; money totals only when `moneyComplete`; never summed across missing rows |
| NFR-09 | Freshness honesty | Hourly aggregate grain; every widget carries lag labels; live ticks bypass cache and are never merged server-side |

## Security and privacy

| # | Requirement | Target |
|---|---|---|
| NFR-10 | Owner scoping | Server-set user scope on JSON + CSV; `profile` param must match else `403 foreign_profile`; RLS inherited (12); no cross-user support read exists |
| NFR-11 | Cache privacy | `private, max-age=60` per profile + range; merged views only, never source truth; no shared caches |
| NFR-12 | Throttling | JSON 60 req/min/user; CSV 6 exports/min/user (stricter); excess -> `429 rate_limited` + `Retry-After`; throttles counted, probing not punished |
| NFR-13 | Data minimization | No PII in payloads or CSV beyond owner-visible titles; money figures are owner-only; no new identifiers introduced |

## Observability, schema, and localization

| # | Requirement | Target |
|---|---|---|
| NFR-14 | Metrics + alerts | `unified.fanout_ms{source}`, `unified.source_unavailable{source}`, `unified.csv_bytes`; alert when a source is unavailable > 1% of reads over 15 min or fanout p95 > 2 s |
| NFR-15 | Schema | Zero new tables or migrations in this feature (assertion; flags + rollups storage owned by revenue pipelines) |
| NFR-16 | Localization | VND integers only; client-side locale formatting; copy VN default with EN fallback; gross/net labels localized display of verbatim source labels |
| NFR-17 | PWA parity | Same endpoint and payload on browser and installed PWA; values never differ (feature 11) |
