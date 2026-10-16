# Order Status and Orchestration - Non-Functional Requirements (feature 34)

Phase 1 BA · Every threshold numeric; platform envelopes from `docs/01-requirements-ba/32-monetize-overview/07-non-functional-requirements.md` are consumed.

## Performance

| # | Requirement |
|---|---|
| NFR-01 | Order list p95 ≤ 300 ms; order detail p95 ≤ 300 ms with the fulfillment composite budgeted ≤ 150 ms or degraded inline; pending poll p95 ≤ 200 ms (32 NFR-01). |
| NFR-02 | Alert → visible: `paid` on the poll ≤ 200 ms after commit; ledger/receipt effects ≤ 60 s p95 (32 NFR-02); audit append in the transition transaction. |
| NFR-03 | Read envelopes: `GET orders` 120/min per session; detail 120/min per session; internal delete-guard 600/min per caller service — breaches produce `throttled` 429 without touching rows. |

## Correctness and evidence

| # | Requirement |
|---|---|
| NFR-04 | Zero illegal transitions and zero late grants on all eight rails at release (drill-verified, AC-02/03); terminal states latch. |
| NFR-05 | Replay: redelivery → one row, one audit chain; fast-path replays require no lock; DLQ replay is idempotent. |
| NFR-06 | Conformance: 8×N matrix green before any rail change ships; artifact recorded per release (32 NFR-06). |
| NFR-07 | Audit retention 24 months; per-`orderCode` trail replayable; secrets/signatures redacted in trail output. |
| NFR-08 | VND integers end-to-end; 5 s statement timeout on guard and read statements; no floating point. |

## Security and privacy

| # | Requirement |
|---|---|
| NFR-09 | Reads scope by session + RLS: foreign order → 403 + RLS deny; absent → 404; guest rows invisible to sessions; guest reads only via 37's signed single-order magic link. |
| NFR-10 | Data minimization: ship-to and buyer emails never appear in list projections, logs, events beyond the frozen payload, or fulfillment responses (status fields only); title resolution degrades without leaking cross-service internals. |
| NFR-11 | Rendering: CSP `default-src 'self'`; `status_detail` is escaped and server-composed only; provider-native codes and raw payloads never rendered. |

## Operations and localization

| # | Requirement |
|---|---|
| NFR-12 | Alerting numbers: late confirmations ≥ 5 in 60 min per rail; grant event DLQ non-empty > 5 min; any amount mismatch pages immediately; event-emission lag metric visible. |
| NFR-13 | Degrade: feature 23 down → fulfillment block unavailable, money reads unaffected; 22 down → delete blocked 503, reads unaffected; 35 catalog rows absent → plan titles fall back to labeled placeholders; DLQ down → paid stands, backlog visible, replay heals. |
| NFR-14 | vi/en buyer copy; `status_detail` localized server-side; amounts stay numeric (formatting only in display). |
| NFR-15 | Accessibility: status changes announced via live regions; keyboard-operable actions; color never the sole state signal. |
