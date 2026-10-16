# Short-Link Analytics - Security, Logging, and Rate Limiting (feature 31)

Phase 2 TL · BA sources: `docs/01-requirements-ba/31-short-link-analytics/` (BR-01..BR-16, NFR-01..NFR-13). Baseline: `docs/02-architecture-techlead/00-platform-baseline/08-security-baseline.md`; gates: `docs/02-architecture-techlead/00-platform-baseline/06-release-gates.md`.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | Hot path untouched: no PG or queue write before a redirect completes; the producer emits after the response; counting on/off bench ≤ 5 ms p95 delta | AC-02; bench gate |
| S-2 | Intake integrity: the stream is internal-only; the consumer validates payload shape and ranges per event; malformed events are dropped with an `intake_invalid` metric — never partially applied | poison fixtures; metric set |
| S-3 | Privacy: visitor keys are salted hashes with 24 h rotation; no visitor-id columns exist anywhere; no raw IPs at rest; cross-link identity resolution is unqueryable by construction | schema attr; log greps |
| S-4 | Scope: RLS owner + ops policies on all three tables; foreign reads 404 with zero rows; the ops context is explicit, role-re-verified, and audited | AC-10; RLS probes |
| S-5 | Quarantine integrity: held floods never auto-count; release replays through the full pipeline (dedupe applies); decisions carry operator + reason; held counts persist until decided | AC-03, AC-08 |
| S-6 | Replay safety: ops-only; sample view with hashed ids; every lookup audited; beyond the 90-day window → 410 (no fabricated evidence) | AC-05; boundary drill |
| S-7 | Money-tied posture: billed/affiliate/ad-attributed counting uses the strictest bot/prefetch settings; the shared matrix is recorded once; divergences labeled | matrix doc; footnote check |
| S-8 | Separation: link hits never enter `profile_views`/`link_clicks` — different service and database, zero cross-write code paths; verified under load | AC-04; load drill |
| S-9 | Evidence + retention: raw NDJSON 90-day lifecycle (append-only, marker-verified); aggregates pruned via feature 13's extended scope; purge extends the feature 02 analytics participant; audit 24 months | retention + purge drills |
| S-10 | Logging: ids + counters + decisions only — no raw IPs, no hashed visitor keys beyond the quarantine `source_hash` (needed for case evidence), no PII in logs; decide/replay actions append audit rows | log greps; audit samples |

## Rate envelopes

| Route class | Envelope | On breach |
|---|---|---|
| Stats reads | 120/min per session | 429 |
| CSV export | 6/h per user | 429 |
| Ops replay / decide | 30/h per operator | 429 + audit |
| Consumer intake | producer-controlled post-response; no client-reachable path exists | - |

## Degrade matrix (fail safe, never fail open)

| Condition | Behavior |
|---|---|
| Redis db 6 down | Consumer pauses (dedupe/velocity substrate unavailable — no approximation, no blind counting); backlog persists (AOF); readouts footnote the freshness |
| Stream backlog above freshness SLA | Alert > 5 min p95; readouts keep serving with lag shown |
| MinIO down | Repair unavailable; aggregates keep serving; `rawgap` risk flagged until raw is verifiable; no counting loss |
| link-service owner read down | Events hold in a bounded retry buffer (no counting without owner scope — never guess); overflow quarantined as `unresolved_owner` with a metric |
| `analytics_db` down | Consumer pauses; backlog persists; readouts 503 |
| Prune job down | Aged rows persist; retention-breach alert |

## Threat checklist

Flood/poisoning (velocity quarantine + shape validation), replay-based inflation (dedupe window + rebuild convergence), cross-tenant probing (RLS + 404 no-oracle), ops abuse (audited decide/replay, role re-verify), CSV formula injection (numeric/sanitized columns only; `referrerHost` keys prefix-escaped for `=+-@`), evidence tampering (append-only raw + completeness markers), estimated-label misuse (data-level flag, visual distinction), dual-count confusion (documented explainer with matched windows), queue eavesdropping (internal network only; no client-reachable queue surface).

## Gates

- G4: OpenAPI diff additive; codes registered; stats session-scoped; ops routes role-guarded; no client-reachable intake.
- G5: RLS probes, poison fixtures, decide/replay auth matrix, flood isolation, separation-under-load.
- G7: AC-01..AC-10 attach to the tag; container drill (Docker + Podman) per baseline release gates; hot-path bench recorded (counting on/off).
- Greps: zero visitor-id columns, zero raw IPs in logs, single consumer group, prune scope extended, equation fields present in the contract.
