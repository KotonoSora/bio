# Data Analysis (Pipeline Standards, Experiments, Metrics) - Security, Logging, and Rate Limiting (feature 52) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/52-data-analysis/`. GATED: S-1 is live now; all other controls bind the future build at the spec amendment. Baseline controls: `docs/02-architecture-techlead/00-platform-baseline/`.

## Controls

| # | Control | Rule |
|---|---|---|
| S-1 | Gate enforcement (LIVE) | Tooling asserts zero data-analysis surface each release (schema/OpenAPI/source/bundle); a hit blocks promotion — the gate is tool-enforced, not policy |
| S-2 | Assignment integrity | Server-side deterministic hash; sticky rows written once (no UPDATE path); variant never trusted from the client; pinned to the order at creation |
| S-3 | Fairness floor | Differing-price treatments require explicit disclosure + legal sign-off before launch; no undisclosed per-user price differences for the same SKU; review gate enforced |
| S-4 | Finance guardrail | No variant may alter closed-period figures or grandfathered terms; catalog versioning carries variant stamps; guardrail breach auto-pauses + pages ≤ 5 min after aggregate refresh |
| S-5 | Transport integrity | Queue-only writes; `event_id` idempotency keys (KV + UNIQUE); at-least-once with exactly-once effects; replay storms cannot drift aggregates |
| S-6 | Registry discipline | Unregistered events rejected at edge; malformed quarantined + owner-notified; registries append-only/versioned, reviewed like code |
| S-7 | Reporting integrity | Single metrics library (grep gate); snapshots carry `version`; dual reporting before retirement; closed snapshots immutable — adjustments net in the posting period with notes |
| S-8 | Privacy | No raw IPs/UAs in PG aggregates (hashes only); deletion propagates to raw stores within the documented window; aggregates stand; zero PII in quality dashboards |
| S-9 | Access scoping | KPI endpoints finance/operator only; assignments have no client grant; earner scopes see only their own ledgers (49/50) — never business KPIs |
| S-10 | Alert integrity | Quality + guardrail alerts share the money-alert ops channel; thresholds config-owned and versioned; no silent suppression |

## Rate limits (at amendment; analytics-service Redis db 6)

| Surface | Limit | On breach |
|---|---|---|
| Registry / definition writes | 60/h/operator | 429 `throttled` |
| Experiment launches | 30/h/analyst | 429 |
| Readout / KPI reads | 300/h/role | 429 |
| Internal assign | 6000/h/service | 429 |
| Sales-facts internal read | 600/h/service | 429 |

## Degrade matrix (at amendment)

| Failure | Behavior |
|---|---|
| Consumer backlog | Quality dashboard shows lag; aggregates lag honestly; alerts fire on threshold; no fabricated numbers |
| KPI rebuild job down | Dashboards serve last snapshot with timestamp; alert after 2 missed runs; facts unaffected |
| Sales-facts read down | KPI rebuilds pause (fail closed); existing snapshots readable; console (51) unaffected |
| Assignment store unavailable | Checkout proceeds on last cached registry hash (deterministic, no writes lost); retry writes; no re-bucketing ever |
| Guardrail watcher down | Experiments auto-pause on watcher outage for running pricing tests (fail closed) until resumed |
| R2 raw store degraded | Queue backpressure; no silent drops; quality loss-rate alerts |

## Audit and observability (at amendment)

Audits (24-month): registry changes, experiment lifecycle + decisions, guardrail pauses, metric version publishes, dual-report periods, close-version naming, retention sweeps. Metrics: per-pipeline dupe/late/loss, queue lag, rebuild outcomes, experiment states, guardrail events, KPI version usage, assignment latency — zero buyer labels.

## Support answers (at amendment)

"Why did the number change?" → metric version + dual-report period, or posting-period adjustment with restatement note. "Which definition did finance close on?" → close names the version; history unrewritten. "Why was my test stopped?" → guardrail breach + slice, kill-switch trail. "Why can't I see this KPI?" → role scope (earner scopes keep their own ledgers). "Why was my event rejected?" → unregistered taxonomy — register with owner + retention class first.
