# Monetize Overview - Step-by-Step Implementation Plan (feature 32)

Phase 2 TL · Kernel first: features 33-40 land on this. Acceptance goals cite `docs/01-requirements-ba/32-monetize-overview/06-acceptance-criteria.md`.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | Scaffold `billing-service` (Spring Boot 4.1.1, Kotlin); service registration (landed: baseline + `MIGRATION.md`); compose slice; RLS-ready datasource | - | boots; digest pair |
| T-02 | BE | Registry enum `PaymentRail` + capability map + tests pinning the eight members | T-01 | AC-01 |
| T-03 | BE | Config surfaces: `ops_config` usage (append-versioned), 5 s Redis cache, audited flip route | T-01 | AC-06; effect ≤ 5 s |
| T-04 | BE | No-default enforcement: create validation, constant-free refund routing, CI grep gate + unit tests | T-02 | AC-02 |
| T-05 | BE | Conformance harness skeleton: 8×N runner + sandbox adapter interface + release artifact writer | T-02 | harness green on stubs; rows ride with 40 |
| T-06 | BE | Audit spine writer + `GET /api/v1/ops/billing/trace/{orderCode}` (reads degrade gracefully until tables land) | T-01 | AC-10 |
| T-07 | BE | Money API conventions: non-sequential `orderCode` generator, RFC 9457 layer, OpenAPI diff gate | T-01 | BR-07 checks |
| T-08 | BE | Notification pipeline: `notifications:q`, consumer, template registry, lead-suppression read, bounce write-back, metrics | T-01 | AC-08 |
| T-09 | FE | Shared money components per BA 04 (ProviderList, PendingPoll, StatusBadge, ProblemMoney, PriceLine) | T-03 | BA 04 states render |
| T-10 | DevOps/QA | Drills: registry, no-default, config flip, kill-switch, display-order, notification, trace; container pair; mixed-load gate | T-02..T-09 | AC-01..AC-10 |
| T-11 | DevOps | Release wiring: conformance artifact attaches to releases; secrets grep + registry greps in CI | T-05, T-10 | release gates green |

## Notes

- Build order is strict: 33-40 add endpoints and tables on this kernel; shop consumers (23/24/25) enable when 33/34/38 exist — their build-order gate is satisfied by this batch, not before.
- Features 25/27/28 built against the queue boundary with the pipeline mocked (recorded); the wiring completes here — no production stubs remain.
- The registry, no-default gate, and conformance artifact are release-gate items, not documentation.
- Config flips, kill switches, and display order are exercised by drills (T-10), never spot checks.
