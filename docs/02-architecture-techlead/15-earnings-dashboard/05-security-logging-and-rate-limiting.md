# Earnings Dashboard - Security, Logging, and Rate Limiting (feature 15)

Phase 2 TL · BA sources: `docs/01-requirements-ba/15-earnings-dashboard/` (BR-01, BR-04..BR-10). Baseline controls: `docs/02-architecture-techlead/00-platform-baseline/`; inherited read controls: `docs/02-architecture-techlead/12-profile-analytics/07-security-logging-and-rate-limiting.md`; entitlement pattern: `docs/02-architecture-techlead/13-audience-insights/05-security-logging-and-rate-limiting.md` (S-4); live separation: `docs/02-architecture-techlead/14-realtime-global/05-security-logging-and-rate-limiting.md`.

## Controls

| # | Control | Rule |
|---|---|---|
| S-1 | Owner scoping | Server-set user scope on JSON and CSV; `profile` must match else `403 foreign_profile`; no cross-user support read exists (support traces via owner-shared session or audited internal tooling only) |
| S-2 | No-restatement, structural | Zero fee/net math in this feature; zero dashboard tables; grep gates + finance review fail any violation (AC-11); money figures pass through verbatim with source labels |
| S-3 | Export abuse control | CSV uses a stricter owner bucket (6/min) than JSON (60/min); streamed pull-based with bounded memory; cancel stops the stream; repeated `422` probing counted, never punished |
| S-4 | Cache privacy | `private, max-age=60` per profile + range; merged views never stored beyond the response cache; no shared caches; live ticks bypass cache per 14's contract |
| S-5 | Partial-failure honesty | Timeout -> `unavailableCells` (never zero); unshipped -> absent (never stub); money totals hidden when incomplete; any fabricated zero fails review |
| S-6 | Fail-closed capabilities | Flags read server-side only (never client-supplied); unknown flags ignored; entitlement snapshot miss -> free clamp (13 pattern, <= 30 s staleness bound stated) |
| S-7 | Grain separation | Live counters never enter unified payloads or CSV; ticks render client-side via 14's component; grep gate forbids server mixing (AC-12) |
| S-8 | Logging minimization | Logs carry route, status, source timing, and error counters only - never money values, titles, or profile ids; metrics never profile-labeled |
| S-9 | Trace integrity | Trace links and origins share the range param; no figure can be opened out of its window (broken traceability fails review, AC-08) |

## Threat checklist

| Threat | Mitigation |
|---|---|
| IDOR across engagement or revenue sources | S-1: owner check precedes the entire fan-out; both endpoints checked; RLS inherited (12) |
| CSV flood / memory exhaustion | S-3: stricter throttle + streaming + cancel; memory flat under flood drill (AC-06) |
| Silent money restatement drift | S-2: no math exists; labels verbatim; finance review + grep gates |
| Unauthorized net derivation | S-2: fee data never reaches this feature; net only ever a pass-through label |
| Flag spoofing via client input | S-6: flags resolved server-side from pipeline-owned storage; request params cannot set flags |
| Cache leakage across profiles | S-4: per-profile cache keys + `private` header; shared caches forbidden |
| Stale entitlement widening ranges | S-6: <= 30 s staleness bound, then re-resolution; downgrade re-clamps next fetch |
| Live/hourly number confusion | S-7 + UI labeling: distinct grains rendered in distinct components with lag labels (AC-12) |

## Logging and degrade

- Metrics: `unified.fanout_ms{source}`, `unified.source_unavailable{source}`, `unified.csv_bytes`; alerts: source unavailable > 1% of reads over 15 min, fanout p95 > 2 s, CSV byte volume anomaly.
- Logs: status + timing + counters only; never money values, titles, or per-profile labels.
- Degrade matrix: source timeout -> per-cell `unavailableCells` (view renders); capability store unavailable -> flags treated absent + counter (fail-closed; engagement-only render); CSV failure mid-stream -> client retry (no server residue); entitlement miss -> free clamp. No path reads or writes money tables, and none exists to fall back to.
