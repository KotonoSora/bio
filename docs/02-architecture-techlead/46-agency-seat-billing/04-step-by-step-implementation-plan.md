# Agency Seat Billing - Step-by-Step Implementation Plan (feature 46)

Phase 2 TL · Package `com.app.billing.agency`. BA sources: `docs/01-requirements-ba/46-agency-seat-billing/`. Order: schema + math first; snapshots + entitlement second (45's gates depend on the read); transfer/reconciliation third.

## Tasks

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | `V8__agency_seat_billing.sql`: plan-domain ALTER + three tables + indexes + RLS + drills | V7 applied | V8 drills green |
| T-02 | BE | `SeatChargeMath`: single half-up VND implementation + fixture parity | T-01 | AC-02, grep gate |
| T-03 | BE | `SeatSnapshotJob`: page 45's seats route, idempotent upserts, miss alerts, same-day backfill | T-01 | AC-01, NFR-01 |
| T-04 | BE | Entitlement read (`GET /internal/v1/billing/agency/entitlement`) + event emission | T-01 | AC-05, 45 gate seam |
| T-05 | BE | `AgencySubscriptionService`: subscribe via 33 (explicit provider), activation mapping, cancel via 36, state mirroring | T-04 | AC-07 |
| T-06 | BE | Renewal settlement: seatDays from snapshots, cycleCharge, true-up into order metadata; credit path via 39 | T-02, T-03 | AC-02 |
| T-07 | BE | `ContactTransferService`: pending row, dual confirmations, expiry job, contact flip + audit | T-01 | AC-03 |
| T-08 | BE | `ReconcileJob` + ops routes (open-day corrections only, reason + audit) | T-03 | AC-08 |
| T-09 | BE | Overview + history + invoices reads (37 delegation); catalog resolution + version stamping | T-02, T-05 | AC-04, AC-07 |
| T-10 | FE | Billing overview: seats, prices, projection, true-up, snapshots table, subscribe/cancel dialogs | T-05, T-09 | UI flows complete |
| T-11 | FE | Transfer dialog (dual confirm states, expiry) + lapse banners + no-hostage cancel copy | T-07 | AC-03, AC-05 |
| T-12 | BE/DevOps | Cross-edits: 32 TL02 chain row + TL03 registry rows; 45 TL03 seam citations; baseline 01 feature list; MIGRATION at batch tail | T-01..T-04 | Diff review green |
| T-13 | BE/DevOps | Drills: count, pro-rate parity, transfer, dispute immutability, lapse/no-hostage, IDOR, catalog, reconciliation | T-01..T-11 | AC-01..AC-08 |
| T-14 | DevOps | Metrics + alerts: snapshot success/misses, true-up totals, mismatches, transfer states, lapse counts | T-03..T-13 | NFR-11 |

## Sequencing notes

1. T-01/T-02 first: the math function is the single proration implementation — a second one anywhere is a CI failure.
2. T-03/T-04 gate 45's staff-power checks: land the entitlement read with 45's cross-edit citation before enabling subscribe.
3. T-06 rides 33/34's order metadata — review with the 34 owner; no new money tables may appear in the diff.
4. T-08's corrections touch the open day only; grants + code review enforce closed-day immutability.
5. Feature gate: E4 scope audit — zero agency billing tables/endpoints in E0–E3 images.

## Definition of done

AC-01..AC-08 green; single-math + immutability + zero-new-money-table greps green in CI; full-tree validation (word caps + backticked path resolution) green; `MIGRATION.md` row 46 flips to `done` at the batch tail with `agency/billing/seat-billing.md` and `monetize/organizations/agency-skeleton.md` deleted.
