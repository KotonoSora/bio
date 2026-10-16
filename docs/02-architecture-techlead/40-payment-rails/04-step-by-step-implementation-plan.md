# Payment Rails - Step-by-Step Implementation Plan (feature 40)

Phase 2 TL · Order matters: schema, verifiers, intake pipeline, sweeps, exceptions, disputes, wallets, probes. Package `com.app.billing.rails`. BA sources: `docs/01-requirements-ba/40-payment-rails/`.

## Tasks

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `V7__provider_rails.sql` per `02-database-schema-design.md` + drills (replay collapse, uniqueness, deadline CHECK, RLS probes) | 39's V6 applied | V7 drills green |
| T-02 | BE | `RailVerifier` per provider: raw-byte HMAC (SHA256/512), constant-time compare, timestamp tolerance (stripe), bearer refresh (napas), merchant-session validation (apple) | T-01 | AC-01 |
| T-03 | BE | `ProviderEventRecorder` + `OrderResolver` + `RailResponder`: insert-first pipeline, amount asserts, frozen response codes per rail | T-02 | AC-02, AC-03, AC-14 |
| T-04 | BE | Intake endpoints × 8 (payos, sepay, vnpay, zalopay, napas, stripe, googlepay, applepay PSP webhook) wired to 34's guard | T-03 | AC-01..AC-03 |
| T-05 | BE | `RailSweeper`: 15-min ticks, per-rail advisory locks, stale threshold + bank 48 h lookback ≤ 200/batch, adapter status queries | T-03 | AC-05, AC-15 |
| T-06 | BE | Bank-rail exceptions: intake mapping (all reasons), queue reads, ops link/refund-record/dismiss with grace clock + second-party confirm | T-04 | AC-06..AC-08, AC-16 |
| T-07 | BE | Dispute cases: open/freeze, evidence submit, win/loss edges, 38's revocation helper call, `order.chargeback_reversed.v1` post-commit | T-04 | AC-09 |
| T-08 | BE | Wallet charge: apple validate-merchant + google/apple authorize, per-order serialization, PSP idempotency key = orderCode, timeout → status-query recovery | T-04 | AC-04, AC-15 |
| T-09 | BE | On-demand verify (ops route) + per-order throttle + honest outcomes | T-03 | AC-13 |
| T-10 | BE | Kill-switch contract: delist read (33 surfaces), wallet-charge refusal, intake/sweep independence | T-04, T-08 | AC-10 |
| T-11 | BE/DevOps | Probe battery: per-rail conformance suite (fixtures for every rail's signature + codes) + secrets grep + rotation dry-run; release gate | T-02..T-10 | AC-11, AC-12 |
| T-12 | FE | Ops rail console: exceptions queue + actions, dispute cases + evidence, verify action, rail health cards, sweep log | T-06, T-07, T-09 | UI flows complete |
| T-13 | FE | Buyer confirming-state copy per rail family (return page + order status polling) | T-04, T-08 | AC-10 copy |
| T-14 | BE/DevOps | Cross-edits per `01-architecture-and-tech-stack.md` (32 chain/config/registry/event; 23/24/25 consumer seam) | T-04..T-07 | Diff review green |
| T-15 | BE/DevOps | Drills: tamper matrix × 8 rails, replay 10× concurrent, amount ±1 VND, wallet double-submit, sweep suppression, grace boundary, dispute lifecycle, kill-switch flip | T-01..T-14 | AC-01..AC-16 |
| T-16 | DevOps | Metrics + alerts (verify outcomes, duplicates, stale count, exception backlog age, dispute deadlines, kill flips, secret age) + conformance report archival | T-11..T-15 | NFR-18 |

## Sequencing notes

1. T-01/T-02 first: the recorder + verifiers are the trust floor; nothing consumes rails before they exist.
2. T-04 is the only intake writer — review it with 34's guard owner; no rail code may write status (drill-gated).
3. T-06/T-07 are ops-money surfaces: land with T-12 so the console and the audit trail ship together.
4. T-08 touches buyer money: per-order serialization and single-charge semantics are reviewed with 33's checkout owner.
5. Phase-2 boundary: this feature ships rails end-to-end for all eight; PSP merchant onboarding, contract negotiation, and production credentials are ops/legal work, tracked outside the docs tree.
6. No feature flag: rails are live only when their secrets/config exist; absent config refuses intake verification (fail closed, never a permissive default).

## Definition of done

AC-01..AC-16 green in CI; probe battery archived per rail; full-tree validation (word caps + backticked path resolution) green; greps: no `orders.status` writes outside 34's guard, no payload-body persistence, no secret material in repo/logs, one V7 DDL owner; `MIGRATION.md` row 40 flips to `done` at the batch tail with the `monetize/*` rail sources deleted.
