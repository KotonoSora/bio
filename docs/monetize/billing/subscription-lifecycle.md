# Subscription Lifecycle (periods, renewal, grace, downgrade)

PLAN ref: P3-3 (decides the plans doc's open downgrade Q2 and the unwritten renewal story). Period bounds live on the ledger (`billing/entitlement-ledger.md`); this doc moves them through time.

## Purpose

Define what happens after the grant: how long pro lasts, how it renews, what expiry and grace look like, and exactly what downgrade does to a user's content. Seven of the eight provider paths are one-shot payments with no native recurring billing, so renewals are explicit re-purchases with reminder UX — only Stripe may use native subscriptions. One lifecycle absorbs both, so the buyer's plan experience is identical regardless of rail.

## Users

| Role | Permissions | Notes |
|---|---|---|
| Pro subscriber | See period end, renew before lapse, cancel cleanly | Understands exactly what downgrade touches |
| Lapsed subscriber | Receive reminders, a grace window, then predictable downgrade | Non-destructive and provably lossless |
| Support/ops | Cancel/refund with clear period consequences | Answers "will I lose my pages" with a documented no |

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Pro subscriber | See period end, renew before lapse, cancel cleanly, and understand exactly what downgrade touches | The plan experience is identical regardless of rail | Must |
| US-02 | Lapsed subscriber | Get reminders, a grace window, then a predictable, non-destructive downgrade | Downgrade is provably lossless | Must |
| US-03 | Support/ops | Cancel/refund with clear period consequences | I answer "will I lose my pages" with a documented no | Must |

## Functional requirements

1. Terms: pro sells fixed terms (monthly default; yearly only if Q1 in the ledger doc says so). `period_end` is set at grant; there is no auto-charge on one-shot rails — renewal is a fresh checkout of the same plan, linked by `user_id` history, never by card-on-file.
2. Renewal reminders: `period_end − 7 days` and `− 1 day` in-app + email nudges (Resend receipts channel, not campaigns) deep-linking to checkout with the plan preselected. Early renewal extends from the later of now/previous `period_end` (no double-pay penalty for renewing early).
3. Expiry job (scheduled): flips lapsed rows `active → grace` at `period_end`, and `grace → expired` after the grace window (default 7 days, see Q1), opening a fresh `free/active` row at expiry. The job is idempotent per row and audited.
4. Grace semantics: during `grace`, pro gates still pass (generosity converts better than lockouts), but dashboard shows a persistent renew banner; AI credits do not refill during grace.
5. Downgrade execution (decides plans-doc Q2 — hide newest): at expiry, profiles beyond the free quota (all but the oldest active profile) become hidden from public + read-only in dashboard, never deleted; custom domain detaches (DNS stays untouched, serving stops); premium templates fall back to the closest free token set on render (stored `template_id` preserved for re-upgrade); badge reappears. Re-upgrade restores everything byte-identical — downgrade is provably lossless.
6. Cancel: buyer-initiated cancel sets `cancelled_at` (pro runs to `period_end`, no mid-term refund by default — refund requests follow the originating provider's settlement doc), stops reminders, and pre-schedules the downgrade above. Stripe-native subscriptions additionally cancel at the PSP (no further invoices); one-shot rails need no provider call.
7. Upgrades mid-term (free → pro): immediate, full-term from grant time. Pro → pro renewals stack per rule 2. No proration math in v1.

## Data / API touches

- Mutates `subscriptions.status/period_end/cancelled_at` via the expiry job + cancel endpoint; reads `orders` history for renewal linking. All PG via `worker/db/*`; RLS preserved.
- Endpoints (owner-scoped): `GET /api/v1/plan` (adds `period_end, status, renew_url`), `POST /api/v1/plan/cancel`, renewal is the standard provider checkout with `{plan}` (no separate renew API). Restricted ops endpoint for manual period fixes (audited).
- Notifications ride the receipts channel (see `billing/invoices-receipts.md`); reminder state (`reminded_7d, reminded_1d`) stored on the subscription row to keep the job idempotent.

## Acceptance criteria

| AC ID | Scenario | Given | When | Then |
|---|---|---|---|---|
| AC-01 | Time-travel drill (test clock): grant to reminders at -7d/-1d to grace with gates passing + banner to expiry with lossless downgrade (hidden/read-only extras, domain detached, template fallback, badge back) to re-upgrade restoring everything | A grant advanced by test clock | Reminders, grace, expiry, and re-upgrade run | Reminders fire at -7d/-1d, grace passes gates with banner, expiry downgrades losslessly, and re-upgrade restores everything |
| AC-02 | Early-renewal drill: renewing mid-term extends from `period_end`, never from now; single current ledger row throughout | A mid-term renewal | Renewal is granted | The term extends from `period_end` with a single current ledger row throughout |
| AC-03 | Cancel drill: service runs to `period_end`, reminders stop, downgrade pre-scheduled and then executed as specified | A buyer-initiated cancel | Cancel is set | Service runs to `period_end`, reminders stop, and downgrade is pre-scheduled then executed as specified |
| AC-04 | Stripe-native path (if enabled): provider subscription canceled alongside; no stray invoices; ledger still the visible truth | A Stripe-native subscription cancel where enabled | Cancel runs | The provider subscription is canceled alongside with no stray invoices and the ledger remains the visible truth |
| AC-05 | Auth probe: user A cancelling/reading user B lifecycle returns 403; RLS denies cross-user rows | User A cancelling/reading user B lifecycle | The operation is attempted | The API returns 403 and RLS denies cross-user rows |
| AC-06 | Expiry-job rerun safety: second run changes nothing (idempotent, audited once) | An already-run expiry job | The job reruns | Nothing changes with the run audited once |

## Out of scope

- Ledger schema and grant writes (see `billing/entitlement-ledger.md`); receipts and history UX (see `billing/invoices-receipts.md`).
- Provider checkout/webhook/settlement internals (their own minors); plan matrix and AI caps (`plans/`); agency seats (`organizations/`); dispute-driven revocations (each card provider's settlement doc); P4/P5.

## Open questions

- Q1: Grace length — 7-day default acceptable, or shorter/longer?
- Q2: Mid-term cancel refunds — strict no-refund, or prorated goodwill window (and who funds it)?
