# Subscription Lifecycle Database Plan (period movement — no new tables)

PLAN ref: P3-3 (terms, reminders, expiry job, grace, lossless downgrade, cancel, mid-term upgrades) + period-bound math. BA source: `docs/monetize/billing/subscription-lifecycle.md`. Consumed by the lifecycle backend plan (expiry job, reminders, cancel, downgrade execution; splits next to this file). Ledger rows live in `docs/monetize/billing/entitlement-ledger-database-plan.md` (single `subscriptions` table INCLUDING `reminded_7d/reminded_1d` + `cancelled_at` — owned there, never altered here). Renewals are standard provider checkouts (orders owned by the checkout-session slice). No migration — this slice creates zero tables.

## Purpose

Define period movement as data contract without owning storage: term bounds, reminder state transitions, expiry/grace progression, lossless-downgrade effects per surface, cancel semantics, and no-proration math. No `CREATE TABLE` here by design — lifecycle moves rows the ledger owns through states the status discipline governs.

## Users

- Backend developer: implements the expiry job + cancel + downgrade against ledger rows.
- QA: time-travels the full lifecycle (grant → reminders → grace → expiry → re-upgrade) proving losslessness.
- Support: answers "will I lose my pages" with a documented no.

## Functional requirements

1. Terms (data): pro sells fixed terms (monthly default; yearly only if the ledger Q1 says so). `period_end` set at grant; one-shot rails have NO auto-charge (renewal = fresh checkout of the same plan linked by `user_id` history, never card-on-file — no standing-charge column exists anywhere, asserted by absence). Early renewal extends from `max(now, previous period_end)` (no double-pay penalty — the max() is the entire proration policy of v1).
2. Reminder state: `reminded_7d/reminded_1d` booleans on the subscription row (owned by the ledger DDL) transition false→true as nudges fire at `period_end − 7d/−1d` (in-app + email via the receipts channel — Resend transactional, not campaigns). Flags make the job idempotent (re-run skips flagged rows — exactly-once nudges without a sent-log table).
3. Expiry progression: `active → grace` at `period_end` → `grace → expired` after the grace window (7d default per Q1) → fresh `free/active` row opens at expiry (free-from-signup invariant preserved across lapses — no user ever holds zero current rows). Transitions audited; job idempotent per row (re-run changes nothing — guarded by current-status checks, tested by double-run).
4. Grace semantics (data): gates still pass (generosity converts); AI credits do NOT refill (credit computation reads active-only periods — rule owned by the plans slice, period source owned here); dashboard renew banner persists (UI reads `grace` status — no separate banner flag needed).
5. Lossless downgrade effects (per surface, all reversible): profiles beyond free quota (all but oldest active) → hidden from public + dashboard read-only (service-port calls to the bio module — never direct cross-schema writes; content rows untouched, only visibility/editability flags); custom domain → detached serving, DNS untouched (service-port to domains); premium `template_id` → render-time fallback to closest free token set (stored value preserved — fallback is a read rule, not a migration); badge reappears (entitlement-gated display flips back automatically). Re-upgrade restores byte-identical state (downgrade writes nothing destructive — proven by the restore test, not by policy).
6. Cancel semantics: buyer cancel sets `cancelled_at` (service runs to `period_end`, no mid-term refund by default — refund requests follow rail settlement docs); reminders stop (flag check short-circuits); downgrade pre-scheduled at `period_end` (same execution path as natural expiry — one downgrade code path, two triggers). Stripe-native subscriptions additionally cancel at the PSP (no further invoices); one-shot rails need no provider call (nothing to cancel remotely — asserted by absence of provider calls in the cancel path).
7. Mid-term moves: free→pro immediate full-term from grant; pro→pro renewals stack per rule 1; no proration math in v1 (proration code appearing anywhere fails review — simplicity is the policy until finance demands otherwise).

## Data / API touches

- DDL: none — assertion: no `CREATE TABLE` for lifecycle state anywhere under `docs/monetize/billing/` for this slice (review greps it). Mutated columns (owned by ledger DDL): `status/period_end/cancelled_at/reminded_7d/reminded_1d`. Read history: `orders` (renewal linking). Downgrade effects: service ports (bio visibility, domains detach, render fallback) — never direct cross-schema writes.

## Acceptance criteria

- Time-travel drill: grant → −7d/−1d nudges (once each) → grace (gates pass + banner, no credit refill) → expiry (lossless downgrade per surface) → re-upgrade byte-identical restore.
- Early-renewal drill: extension from `period_end`; single current row throughout.
- Cancel drill: service to `period_end`, reminders stop, pre-scheduled downgrade executes identically to natural expiry.
- Stripe drill (if enabled): PSP subscription canceled alongside; no stray invoices; ledger still visible truth.
- Rerun drill: expiry job second run changes nothing (idempotent + audited once).
- Auth drill: cross-user cancel/reads 403 + RLS deny.

## Out of scope

- Ledger DDL/writer/reads (entitlement plans); expiry/reminder/cancel/downgrade implementation (backend plan); lifecycle screens (frontend plan); receipts/history (invoices-receipts plans); rail settlement internals; plan matrix/AI caps; agency seats; dispute revocations (rail settlement docs); P4/P5.

## Open questions

- Q1: Grace length 7d — hold, shorter, longer (BA Q1)?
- Q2: Mid-term cancel refunds — strict no-refund vs prorated goodwill + funder (BA Q2)?
