# Affiliate Tracking, Program, and Payouts - Step-by-Step Implementation Plan (feature 49) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/49-affiliate/`. Contracts: `docs/02-architecture-techlead/49-affiliate/03-api-contracts-and-payloads.md`. GATED: T-01/T-02 run now; every `AT AMENDMENT` task is blocked until the spec amendment (BR-01) and must not ship in any current batch.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | DevOps | LIVE: absence-drill tooling — CI step asserting zero affiliate surface (schema diff, OpenAPI diff, source + bundle grep: `affiliate_*`, `/r/` route, dashboard strings, payout jobs) | - | AC-01 green; violation blocks promotion by tooling |
| T-02 | DevOps | LIVE: release-checklist entry — affiliate remains gated; an amendment record is required before any build task starts | T-01 | Gate cannot be lifted silently |
| T-03 | BE | AT AMENDMENT: `affiliate_db` bootstrap + `V1__affiliate.sql` (six tables + RLS + append-only evidence grants) | amendment; 01 D-1 | Schema drills green |
| T-04 | BE | AT AMENDMENT: `RedirectHandler` + `ClickIngest` (cached lookup, signed cookie, deep-links, stuffing rejects, rate budgets) | T-03 | AC-05; NFR-03 |
| T-05 | BE | AT AMENDMENT: `AttributionResolver` + billing checkout seam (`orders.extras` stamp at creation) | T-04; 32 contract | AC-02, AC-03 |
| T-06 | BE | AT AMENDMENT: `EnrollmentService` (intake, manual approval only, ladder, terms versions + re-acceptance, vault fields) | T-03 | AC-08, AC-10 |
| T-07 | BE | AT AMENDMENT: `CommissionLedger` (event consumers, rate snapshots, proportional reversals, rounding log, exposure cap) | T-05 | AC-04, AC-12, AC-13 |
| T-08 | BE | AT AMENDMENT: `PayoutBatchService` (freeze → instruct → execute → reconcile, dual control, idempotency, statements + emails) | T-07; 40 discipline | AC-14, AC-15, AC-16 |
| T-09 | BE | AT AMENDMENT: `EvidenceStore` + ops evidence route (immutable bundles) | T-05 | Dispute replay drill |
| T-10 | FE | AT AMENDMENT: affiliate dashboard (stats, links/codes, earnings, statements) + application flow | T-06, T-07 | UI flows complete |
| T-11 | FE | AT AMENDMENT: ops console (queue, ladder actions, tiers editor, exposure panel) + finance payout console | T-08, T-09 | UI flows complete |
| T-12 | DevOps | AT AMENDMENT: drill battery (precedence, window, reversal, stuffing, privacy, auth, enrollment, tier, conduct, buyer-price, cap, computation, cycle, duplicate-execution, tax) | T-03..T-11 | AC-02..AC-16 green |
| T-13 | DevOps | AT AMENDMENT: release gates + metrics; absence-drill tooling flips to surface-drift checking | T-12 | NFR-14; digest pair boots (docker + podman) |

## Sequencing notes

1. T-01/T-02 are the live tasks: the gate is tool-enforced; no affiliate surface may leak into any current image.
2. T-04/T-05 before anything earns: resolver parity fixtures (code+cookie, two cookies, direct, expired window) gate the ledger work.
3. T-08 reuses the 40 discipline (batch instruction + dual control) — review with the rails owner; no new rail integration in v1.
4. Rates/tiers/thresholds/caps are config appends (finance-owned); code must contain zero hardcoded rate or threshold values (grep gate).
5. Feature gate: E4 scope audit — zero affiliate surface in all pre-amendment images; the amendment record is the only way to open the gate.

## Definition of done (at amendment)

AC-01 stays green through the flip; AC-02..AC-16 green; resolver-parity + no-hardcoded-rates + append-only-evidence greps green in CI; full-tree validation green; `MIGRATION.md` feature 49 row notes the amendment when it lands.
