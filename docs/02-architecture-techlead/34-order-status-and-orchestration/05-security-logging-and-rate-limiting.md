# Order Status and Orchestration - Security, Logging, and Rate Limiting (feature 34)

Phase 2 TL · BA sources: `docs/01-requirements-ba/34-order-status-and-orchestration/` (BR-05..BR-14, NFR-09..NFR-13). Baseline: `docs/02-architecture-techlead/00-platform-baseline/`; kernel controls in `docs/02-architecture-techlead/32-monetize-overview/05-security-logging-and-rate-limiting.md`.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | Scoping: session + RLS on every read; foreign order → 403 + RLS deny, absent → 404; guest rows invisible to sessions by construction; guest reads only via 37's signed single-order magic link | AC-09 |
| S-2 | Projection hygiene: payloads, DOM, and logs carry canonical vocabulary only — `status_detail` server-composed; provider-native codes, raw confirmation payloads, and secrets never surface (grep-asserted) | AC-05 |
| S-3 | Transition authority: the guard is the single status writer; no public route transitions; buyer client input never reaches a status write; terminal states latch | AC-02, AC-04 |
| S-4 | Replay/receipts: anti-replay keys are UNIQUE (V6, 40); redelivery is success-without-effect; DLQ replay and reconciliation re-emit idempotently | AC-07 |
| S-5 | Abuse: read envelopes (NFR-03); poll loop is client-bounded (3 s + backoff + hidden-tab pause, 33's contract); breaches return `throttled` 429 without touching rows | AC-09 |
| S-6 | Secrets: no new secrets here — webhook signatures and PSP keys are 40's; existing CI secrets grep covers the repo, logs, and bundles | grep clean |
| S-7 | Privacy: ship-to and buyer emails never appear in list projections, logs, or fulfillment responses; fulfillment carries status fields only; title resolution degrades without exposing cross-service internals | log greps; AC-10 |
| S-8 | Audit: append-only `audit_log`; every transition + the exception re-link appends with `{from,to,eventId,rail}`; per-order trail replayable via 32's trace; redactions applied | AC-01, audit sample |
| S-9 | Self-heal honesty: the reconciliation re-emit is the only automated retry authority; no automatic re-link exists for late money (asserted by absence); amount mismatch quarantines, never grants | AC-03, AC-06 |

## Redis inventory (db 10 additions)

| Key | Type | Use |
|---|---|---|
| `billing.events` | stream | `order.granted.v1` production (consumer groups per consumer service) |
| `billing.events.dlq` | stream | failed publishes parked after bounded retry; replay tool re-publishes; alert when non-empty > 5 min |

## Rate envelopes

| Route class | Envelope | On breach |
|---|---|---|
| Order list | 120/min per session | 429 |
| Order detail | 120/min per session (poll 3 s ≈ 20/min) | 429 |
| Delete-guard read | 600/min per caller service | 429 |

## Degrade matrix

| Condition | Behavior |
|---|---|
| Feature 23 down | detail fulfillment block `degraded: true`; money fields render; no 5xx |
| Feature 22 down (delete guard) | 22 blocks the delete with 503 `orders_check_unavailable`; reads unaffected |
| Feature 35 not yet built | plan titles fall back to the fixture catalog (33's precedent); contracts unchanged |
| `billing.events`/DLQ down | `paid` stands; emission retries then parks; backlog metric + alert; reconciliation heals ≤ 24 h |
| Redis down | reads unaffected (no cache dependency); emission retries in-process until Redis returns; alert |
| `billing_db` down | reads 503; nothing partial (transitions are single-statement) |
| Trace route (32) down | support falls back to direct ops-context reads; audit still appends |

## Gates

- G4: OpenAPI diff additive (two public reads + one internal read + `order_not_found`); ops/internal routes service/role-guarded.
- G5: IDOR + guest-invisibility probes; projection greps (no native codes, no PII); no-sessionless-read proof.
- G7: AC-01..AC-14; edge-matrix + late-event + replay + race drills attach to the tag; container pair boots; log greps (no email/ship-to/native codes).
