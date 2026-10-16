# Agency Seat Billing - User Stories and Business Rules (feature 46)

Phase 1 BA · Enforced by `docs/02-architecture-techlead/46-agency-seat-billing/`. Scope: `docs/01-requirements-ba/46-agency-seat-billing/01-document-context-and-scope.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Agency owner (billing contact) | see seat count, per-seat price, upcoming total, add/remove seats, pay, and read invoices | every charge traces to a dated seat count | High |
| US-02 | Manager/member | consume a seat and see plan status without touching billing instruments | staffing stays separated from payment | High |
| US-03 | Finance/support | resolve "why was I charged X?" from seat-history evidence alone | no invoice is a surprise | High |
| US-04 | Agency owner | transfer the billing contact with dual confirmation | payment responsibility moves deliberately | Medium |
| US-05 | Client | never be affected by the agency's billing state | my profiles are never held hostage | High |

## Business rules

- **BR-01 Seat model (closed).** Billable seats = active staff memberships (owner + managers + members); clients free and unlimited; invited-but-unaccepted unbilled; suspended unbilled from the suspension date (reactivation re-bills from the reactivation date). Seat counts snapshot daily (`seat_history{org_id, day, seats, by_role}`) from 45's internal seat counts — the invoice cites snapshots, never a live count.
- **BR-02 Day-weighted charging.** The agency subscription = base platform fee + N × seat price per cycle (VND integers; values from the versioned catalog `agencyBaseVnd`/`agencySeatVnd`; absent → `catalog_incomplete` refusal). Cycle charge = base + `round(Σ seat-days × seatPrice ÷ cycleDays)` (round half-up). Prepaid at renewal; the day-weighted true-up versus the prepaid seat count settles on the next invoice: positive as a line, negative as account credit.
- **BR-03 Evidence and immutability.** Every invoice line links to its seat snapshots in one hop from the overview screen; closed-period snapshots and invoices are immutable under later roster changes; corrections are forward-only (ops reconciliation with reason, audited).
- **BR-04 Subscription mechanics.** The agency subscription is the billing contact's own subscription row (`plan='agency'`, plan domain evolved by V8); activation, renewal, dunning, grace, and cancellation ride 36's lifecycle with agency-wide effect stated plainly (all staff powers freeze together). Payment goes through 33's front door with an explicit `provider` (no default; eight equal rails).
- **BR-05 Seat changes.** Follow the roster (45) — no billing-side seat mutation endpoint exists; additions bill from the day of acceptance; removals credit from the day of removal via the true-up; invite-but-not-accepted consumes nothing.
- **BR-06 Owner-pays posture.** One billing contact per org, defaulting to the owner; transfer is a dual-confirmation action (outgoing + incoming), audited, effective only when both confirm; managers see counts-only (who holds seats, never amounts or instruments) unless designated contact.
- **BR-07 Enforcement + no-hostage.** A lapsed subscription keeps the roster readable but freezes staff powers (new invites + Act-as-user starts refuse, `agency_subscription_required`) until cure — client profiles, pages, and client-owned surfaces never go dark; cancel, lapse, or org delete never deletes or privatizes client profiles (cancel-flow copy states it).
- **BR-08 Reconciliation.** A daily job compares billed seats (snapshots) against roster truth; mismatches alert ops and correct forward-only (never rewriting closed periods); every correction audited with reason.
- **BR-09 Refunds and credits.** Removed-seat value lands as account credit applied to the next renewal (default); cash refunds ride 38's machinery on request; no refund fees or percentages are invented by this feature (finance-owned).
- **BR-10 Plan integrity.** Agency is a superset of pro for the billing contact's own account; staff tooling entitlements flow from the org's active agency subscription, never from a staffer's personal plan (orthogonal); the catalog version used is stamped on every invoice line.
- **BR-11 Audit.** Subscriptions, snapshot writes, true-up settlements, contact transfers (both confirmations), cancellations, and reconciliation corrections append to the service's `audit_log` (24 months; ids + amounts, never instruments).
- **BR-12 Privacy.** Billing reads are contact/owner-scoped (RLS); managers' counts-only view carries no amounts; clients have no billing surface; zero token material in logs or payloads.

## Acceptance criteria pointers

Drills in `docs/01-requirements-ba/46-agency-seat-billing/06-acceptance-criteria.md`: count, pro-rate, transfer, dispute, lapse/no-hostage, IDOR. GWT forms live there.
