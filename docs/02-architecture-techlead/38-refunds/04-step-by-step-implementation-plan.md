# Refunds - Step-by-Step Implementation Plan (feature 38)

Phase 2 TL · Order matters: schema, state machine, strategies, finality transaction, revocation, wiring, cross-edits, drills. Package `com.app.billing.refunds`. BA sources: `docs/01-requirements-ba/38-refunds/`.

## Tasks

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `V5__refunds.sql` per `02-database-schema-design.md` + drift test (one-per-order, manual invariant, terminal guards) | 37's V4 applied | V5 drills 1-4 green |
| T-02 | BE | Request flow: eligibility checks (status, kind -> `tip_final`), idempotency key, row minting, owner reads (list/detail) | T-01 | AC-04, AC-06 (owner half) |
| T-03 | BE | Approval flow: ops queue read + approve/reject under row lock + audits + `refund_not_executable` guards | T-02 | AC-01 (half), AC-06 (ops half) |
| T-04 | BE | `RefundStrategy` registry: `kind` + idempotency capabilities for the eight members; unknown-provider rejection + no-default CI grep wired | T-01 | AC-05 |
| T-05 | BE | Execution pipeline: `approved → executing` commit, strategy call, final transaction (amount assert, 34's guard edge, `psp_ref`, credit note, marker), `failed` + backoff executor (≤ 5 / ≤ 24 h) | T-03, T-04 | AC-03, AC-10 |
| T-06 | BE | Manual-rail gates: evidence requirement, two-person enforcement, evidence-before-transition ordering | T-05 | AC-02 |
| T-07 | BE | Revocation orchestration: plan matcher (origin/extension via audits) calling 36's close-to-free path + `plan.refunded`; product path = event only; re-grant refusal verified against 34's guard | T-05 | AC-07, AC-09 |
| T-08 | BE | Credit notes: insert in final txn + `billing.credit_note` enqueue + `CN-` minting | T-05 | AC-08 |
| T-09 | BE | Emission: `order.refunded.v1` publish + marker + reconciliation (mirror 34's emitter) | T-05 | AC-09 |
| T-10 | BE | System-class internal route: allowlist, auto-approve, API auto-executor pick-up, manual queueing | T-03, T-05 | AC-12 |
| T-11 | BE | Cross-edits per `01-architecture-and-tech-stack.md` (32 registry, 34 V7 fix, 36 audit detail) | T-02..T-10 | Diff review green |
| T-12 | FE | Buyer request dialog + tracker; ops queue with approve/reject/execute forms, evidence field, pair notice | T-02..T-06 | AC-01..AC-04 UI |
| T-13 | DevOps | Metrics + alerts (queue SLA age, retry exhaustion, `refund.emitted` mismatches) + no-default grep in CI | T-04..T-10 | NFR-13, NFR-16 |
| T-14 | BE/DevOps | Drills: eight-rail matrix, manual two-person, idempotency races, revocation chain, consumer invalidation replay, PSP-fail budget | T-05..T-13 | AC-01..AC-12 complete |

## Sequencing notes

1. T-01 first: `uniq_rf_order` and the manual invariant gate everything that follows.
2. T-04 lands before T-05: an unknown provider must reject during development, not after rollout.
3. T-07 leans on 36's landed path — the close-to-free helper is called, never reimplemented; the matcher reads `plan.granted`/`plan.renewed` audits only after T-11's 36 cross-edit lands.
4. T-09 mirrors 34's emitter shape (marker + reconciliation); do not fork it — factor the publisher helper.
5. Rollout: no feature flag. Refunds begin unavailable in practice only via ops discipline (queue triage); the internal route ships with 25's callers behind their own build-order gates.
6. T-14's eight-rail drill runs against rail sandbox adapters (40's member conformance provides them); manual rails use the ops console fixture flow.

## Definition of done

AC-01..AC-12 green in CI; full-tree validation (word caps + backticked path resolution) green; greps: no-default gate, single DDL owner, no PAN material, no DELETE grants on refunds/credit notes; `MIGRATION.md` row 38 flips to `done` at the batch tail with `monetize/refunds/refund-processing.md` deleted.
