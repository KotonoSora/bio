# Profile Analytics - Security, Logging, and Rate Limiting (feature 12)

Phase 2 TL · BA sources: `docs/01-requirements-ba/12-profile-analytics/` (BR-01, BR-05, BR-06, BR-10). Baseline controls: `docs/02-architecture-techlead/00-platform-baseline/`.

## Controls

| # | Control | Rule |
|---|---|---|
| S-1 | No oracle | Uniform `202 {ok:true}` on every accepted path; rejected/unknown targets indistinguishable; `400` only for malformed shape |
| S-2 | Rate limits | Gateway per-IP bucket on `/api/t/*` (120/min, burst 240) + service-side counted bucket; auto-tighten 50% when 429 ratio > 5% over 5 min; 15-min decay; reads keep standard gateway limits |
| S-3 | Bot filtering | Crawler UA denylist default-on, counted `beacon.rejected{bot}`, never stored; no client toggle exists |
| S-4 | IP minimization | Visitor key = `HMAC(salted ip+ua, dailySalt)` from the secret store; no raw IP/UA at rest; country-grain geo only; no IPs in service logs |
| S-5 | Owner scoping | Server-set `user_id` from the gateway internal JWT; optional `profile` param equality check -> `403 foreign_profile`; RLS as second layer; ops read metrics only |
| S-6 | Raw archive | MinIO bucket private; lifecycle 90d; ops-only access; no API ever serves or joins raw data |
| S-7 | Input hardening | Canonical JSON body with length caps (referrer <= 512 chars, `utm` <= 10 pairs, hints <= 32 chars); `:username` validated against profile rules before resolve |
| S-8 | Queue integrity | Stream private to the service (Redis db 6); consumer group at-least-once; poisoned message -> dead-letter list + counter; idempotent upserts make duplicates harmless |
| S-9 | Dashboard isolation | `/app/*` noindex (feature 11); reads authenticated; analytics numbers never appear on public pages (feature 10 exclusivity) |
| S-10 | Abuse forensics | `beacon.rejected{reason}` counters + tighten/recover audit events; no PII in any counter label |

## Threat checklist

| Threat | Mitigation |
|---|---|
| Count inflation via refresh/burst/replay | Dedupe window + upsert natural keys + rate bucket (drills AC-02, AC-06) |
| Oracle probing for username/block existence | S-1 + S-5; probes learn nothing |
| Cross-user analytics reads | S-5 + RLS (AC-07) |
| Raw-log exposure | S-6; dashboards structurally cannot join raw data |
| Dedupe bypass by forged headers | Visitor key includes IP+UA under HMAC; spoofing trades effort for a dedupe miss, accepted risk |
| Salt compromise | Daily rotation; salt in secret store, never in code |
| Queue flooding / dead-letter growth | S-2 auto-tighten; dead-letter counter alerts |
| Analytics scraping via reads | Standard session auth + gateway limits; no anonymous read path exists |

## Logging and degrade

- Logs: rejection counters, rollup result (rows written, lag), bucket tighten/recover; never raw IPs, UAs, referrers, or queue payloads with PII.
- Metrics: `consumer_lag_seconds`, `rollup.rows_written`, `beacon.rejected{reason}`, `beacon.abuse`, `analytics.query.latency`.
- Degrade matrix: Redis down -> intake `202` + `backend` counter + alert, reads unaffected (serve stale labelled data); MinIO down -> aggregates continue, raw flush retried; scheduler down -> lag alert, reads keep serving labelled stale; DB down -> reads `503` (standard), intake unaffected (queue-only path).
