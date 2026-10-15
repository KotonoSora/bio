# Seat Billing (per-seat subscriptions)

PLAN ref: P4-5 (agency scope) + P3 billing posture (PayOS-first, VND integers, idempotent grants — money moves only through `monetize/payment/` + `monetize/billing/`; this doc adds no checkout, webhook, or ledger of its own). P4 scope — do NOT build in P1–P3. Roster source of truth in `roster/team-roster.md`; plan mechanics in `monetize/plans/subscription-plans.md`.

## Purpose

Charge agencies fairly for exactly the team they field: per-seat subscriptions where each active staff seat costs a known monthly amount, clients are free (they bring the profiles), and every charge traces to a dated seat count the owner can verify. No surprise invoices, no seat-count disputes — the bill cites its own evidence.

## Users

- Agency owner (billing contact): *As an Agency owner, I want to see seat count, per-seat price, and upcoming total, add/remove seats, pay, and read invoices so that every charge traces to a dated seat count.* Sees seat count, per-seat price, upcoming total; adds/removes seats; pays; reads invoices.
- Manager/member: *As a Manager/member, I want to consume a seat and see plan status without touching billing instruments so that staffing stays separated from payment.* Consumes a seat; sees plan status but never billing instruments.
- Finance/support: *As a Finance/support, I want to resolve "why was I charged X?" from seat-history evidence alone so that no invoice is a surprise.* Resolves "why was I charged X?" from seat-history evidence alone.

## Functional requirements

1. Seat model (closed rules): billable seats = active staff memberships (owner + managers + members); clients free (unlimited client links — clients are the product being served, not headcount); invited-but-unaccepted seats unbilled until acceptance; suspended seats unbilled from suspension date (reactivation re-bills pro-rata). Seat counts snapshot daily (`seat_history{org_id, day, seats, by_role}`) — the invoice cites snapshots, never a live count.
2. Per-seat plan: agency subscription = base platform fee + N × seat price (monthly/yearly per the plan matrix; VND integers; prices from the same versioned plan config as all billing). Seat-quantity changes mid-cycle pro-rate automatically (added seat bills remaining days; removed seat credits per policy, see Q1). Plan downgrade/cancel follows the standard subscription lifecycle (dunning, grace, entitlement loss) with agency-wide effect stated plainly (all seats lose pro powers together).
3. Owner-pays posture: exactly one billing contact per org (the owner); managers never see card/bank instruments; seat-usage visibility for managers is counts-only (who holds seats, not what they cost). Billing-contact transfer is an explicit owner action with dual confirmation (outgoing + incoming), audited.
4. Enforcement: over-seat-limit invites (where a cap applies, see Q2) refused with the add-seat path stated; lapsed-payment grace keeps roster readable but freezes staff powers (Act-as-user + publishing + new invites) until cure — client profiles themselves never go dark (their pages stay live; only agency tooling pauses, so clients are never held hostage by their agency's bill).
5. Invoicing: monthly seat invoice lines (base + per-seat with the daily-max or day-weighted count method stated — see Q1) through the standard receipt pipeline (`monetize/billing/invoices-receipts.md`); e-invoice/VAT handling inherited, never re-specified. Every line links to its seat snapshots for self-service dispute resolution.
6. No-hostage guarantee: agency subscription end (cancel, lapse, org delete) never deletes or privatizes client profiles — links sever, staff powers evaporate, creators keep everything (explicit in the cancel flow copy and enforced by the user-owned-profile invariant from the roster doc).

## Data / API touches

- New table `seat_history` (daily snapshots, append-only, written by a scheduled job from roster state — the billable record). No new money tables: subscriptions, orders, invoices, and ledger rows live in the existing billing minors; this doc's endpoints orchestrate seat counts into the standard subscription-quantity API.
- Endpoints: owner `GET /api/v1/agency/billing/overview` (seats, unit price, projected total, snapshots link), seat add/remove (roster-gated, pro-rating via subscription quantity change), billing-contact transfer, invoice reads (delegating). All PG via `worker/db/*`; all charges via idempotent provider flows.
- Downgrade/cancel propagation: subscription-lifecycle webhooks drive entitlement flips; agency tooling checks entitlements per request (same pattern as pro gates everywhere).

## Acceptance criteria

- Count drill: fixture roster churn (invite/accept/suspend/remove across a month) reproduces hand-computed billable seats from snapshots exactly; clients never counted; pending invites unbilled. *GWT: Given fixture roster churn across a month; When billable seats are computed from snapshots; Then they match hand-computed counts exactly with clients never counted and pending invites unbilled.*
- Pro-rate drill: mid-cycle seat add/remove yields exact day-weighted lines (method as decided in Q1); lapse freezes staff powers while client pages stay live (verified under the same session). *GWT: Given a mid-cycle seat add/remove or a lapsed payment; When billing or powers evaluate; Then day-weighted lines result per the decided method and lapse freezes staff powers while client pages stay live.*
- Transfer drill: billing-contact change requires both confirmations; single-sided attempt leaves billing untouched + audit. *GWT: Given a billing-contact transfer with one or both confirmations; When attempted; Then both confirmations change billing while a single-sided attempt leaves billing untouched plus audit.*
- Dispute drill: any invoice line traces to dated snapshots in one hop from the overview screen; closed-period invoices immutable under later roster changes. *GWT: Given any invoice line; When traced from the overview screen; Then it reaches dated snapshots in one hop and closed-period invoices stay immutable under later roster changes.*
- IDOR drill: non-owner seat/billing reads and all billing writes return 403; managers see counts-only. *GWT: Given non-owner seat/billing reads or any billing writes; When requested; Then 403 results except managers see counts-only.*

## Out of scope

- Roster, roles, invites, linking (see `roster/team-roster.md`); session mechanics (see `access/act-as-user.md`); reporting (see `reporting/client-reporting.md`).
- Checkout, webhook verify, grant, receipts, dunning internals (`monetize/payment/`, `monetize/billing/`, `monetize/subscription/`); plan matrix values (`monetize/plans/`); creator-individual subscriptions (orthogonal — a staffer's personal plan never substitutes for a seat).

## Open questions

- Q1: Seat accounting method — daily-max seats vs day-weighted (and removed-seat credit: pro-rata refund, account credit, or forfeit)?
- Q2: Seat caps per agency tier (e.g. 5/25/unlimited) vs pure pay-per-seat uncapped — which price ladder?
