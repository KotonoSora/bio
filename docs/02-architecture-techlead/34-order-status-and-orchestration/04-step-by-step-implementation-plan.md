# Order Status and Orchestration - Step-by-Step Implementation Plan (feature 34)

Phase 2 TL · Status core after 33's checkout rows; rails (40) land on this guard — no per-rail forks. Acceptance goals cite `docs/01-requirements-ba/34-order-status-and-orchestration/06-acceptance-criteria.md`.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `TransitionGuard`: nine-value enum, `ALLOWED` matrix, authority table, row-locked conditional update, terminal fast-path, `transition.applied/rejected/late` metrics; enum–CHECK parity test | 33 T-01 | AC-01, AC-02, AC-04 |
| T-02 | BE | Grant path: verification-intake interface (40 wires adapters), resolve by ref, amount check, `psp_ref` first-write, transition + audit in one transaction | T-01 | AC-06 |
| T-03 | BE | Replay safety: fast-path pre-lock, redelivery no-op, double-webhook + sweep race collapse; race drills (cancel vs confirm) | T-02 | AC-07, AC-08 |
| T-04 | BE | Late-money refusal + metric + alert wiring; card post-cancel capture hand-off to rail settlement (contract note for 40) | T-01 | AC-03 |
| T-05 | BE | Order reads: list (cursor, filter, newest-first) + detail + projection function (shared with 37) + RLS scoping; title resolution via 22/35 with fallback labels | 33 T-01 | AC-09 |
| T-06 | BE | Fulfillment composite: 23 client (status fields only), 150 ms budget, `degraded` inline result | T-05 | AC-10 |
| T-07 | BE | `has-product-orders` internal read + 5 s timeout semantics + 22 parity drill | T-05 | AC-11 |
| T-08 | BE | Emitter: `billing.events` publish post-commit, bounded retry, DLQ + alert, `grant.emitted` marker, 24 h reconciliation re-emit | T-02 | AC-12 |
| T-09 | BE | Mapping registry template + conformance hookup for 40's per-rail rows (matrix runner from 32 T-05) | T-01 | AC-13 |
| T-10 | FE | History page + order detail + the nine-state rendering map (reuses 32 components; pending page wiring continues 33's T-10); aria-live updates; no client state machine | T-05, T-06 | AC-05, AC-09 half |
| T-11 | DevOps/QA | Drills AC-01..AC-14: edge matrix, late events, replays, races, reads under poll load, composites, delete guard, event shape, DLQ replay, dispute flow; container pair; log greps (no PII, no native codes) | T-01..T-10 | AC-01..AC-14 |

## Notes

- The guard is the only status writer: 33's sweep/cancel and 40's verifiers/sweeps call it; a direct `UPDATE orders SET status` outside the guard fails review.
- Statuses and edges are frozen by this feature: additions land here first (BA BR-01/02), then in rail mappings; rails never extend the machine locally.
- Dispute edges fire from verified settlement events (40) with 38's records; entitlement freeze/revoke and period math stay in 35 — this feature records the seam, invents no finance values.
- DLQ replay and reconciliation are idempotent-path drills, not manual scripts; grants never wait on sends (32 NFR-05 posture).
- Logging greps: no ship-to, no buyer email, no provider-native codes, no raw payloads beyond audit (redacted).
