# Subscription Lifecycle Backend Plan (expiry + reminders + downgrade — server build steps)

PLAN ref: P3-3 (periods, renewal-as-repurchase, grace, lossless downgrade, cancel) + S-1/S-6/S-7. BA source: `docs/monetize/billing/subscription-lifecycle.md`. Phasing per `docs/platform/delivery/execution-overview.md` (E3). Contracts in `docs/monetize/billing/subscription-lifecycle-database-plan.md` (terms, flags, progression, downgrade effects — never redefined here). Lifecycle screens in the frontend plan (splits next to this file).

## Purpose

Give the backend developer an ordered procedure for moving subscriptions through time: idempotent expiry job, once-only reminders, cancel with pre-scheduled downgrade, and lossless downgrade execution via service ports (never direct cross-schema writes). Follow steps 1–7 in order. Downgrade destroys nothing — the restore test proves it every build.

## Users

- Backend developer: executes steps 1–7 in package `com.app.billing.lifecycle`.
- QA: time-travels grant → reminders → grace → expiry → re-upgrade proving losslessness.
- Lapsed subscriber/support: gets predictable grace then reversible downgrade, answerable support.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — monolith lifecycle (in-process with ledger writes). Monolith pros: expiry transition + reminder flags + audit commit per row atomically; downgrade orchestration calls bio/domain ports in-process with one audit chain. Microservices cons avoided: splitting lifecycle from the ledger opens expiry-without-downgrade races. Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; none fire. Starters: data-jpa, validation, security, scheduling, actuator, micrometer. Load posture: expiry job scans the bounded `(status, period_end)` index range (never full-table); reminders are row-flagged once-only; downgrade calls are per-lapsed-user paced (daily batch, not spikes).
2. Step 1 — expiry job `@Scheduled(cron="0 10 2 * * *")`: select `active` rows with `period_end < now` → flip `active → grace` (+ audit); select `grace` rows past window → flip `grace → expired` + open fresh `free/active` + execute downgrade (step 4) → audit each. Idempotent per row (status-guarded transitions make reruns no-ops — double-run test asserts zero second-run writes).
3. Step 2 — reminders (inside the same job, before expiry): `active` rows with `period_end − 7d/−1d` reached and flag unset → send nudge (in-app + email via the receipts channel — Resend transactional, owned by the invoices-receipts slice; this job calls the send port, never SMTP directly) with plan-preselected checkout deep-link → set flag → audit. Early renewal extends from `max(now, previous period_end)` (no double-pay penalty — the max() is the whole v1 proration policy).
4. Step 3 — cancel `POST /api/v1/plan/cancel` (owner-scoped): set `cancelled_at` (service runs to `period_end` — no mid-term cutoff) → stop reminders (flag check short-circuits) → pre-schedule downgrade at `period_end` (same execution path as natural expiry — one downgrade implementation, two triggers). Stripe-native subscriptions additionally cancel at the PSP (no further invoices); one-shot rails need zero provider calls (nothing remote to cancel — asserted by absence of provider calls in this path). Refund requests on cancel follow rail settlement docs (cancel itself refunds nothing — separation stated in the response copy).
5. Step 4 — lossless downgrade execution (one method, both triggers): extra profiles beyond free quota (all but oldest active) → bio port: hide from public + dashboard read-only (content rows untouched); custom domain → domains port: detach serving, DNS untouched; premium `template_id` → render-time fallback (stored value preserved — fallback is a read rule in templates, invoked with the stored id); badge returns via entitlement-gated display. NEVER direct cross-schema SQL (ports only — ArchUnit forbids `billing` writes outside the `billing` schema). Re-upgrade restores byte-identical state (restore test compares pre/post snapshots of all four surfaces).
6. Step 5 — mid-term moves: free→pro immediate full-term from grant (ledger writer path); pro→pro renewals stack per rule 2 of the database plan; no proration code exists anywhere in v1 (grep-verified absence).
7. Step 6 — ops endpoint: restricted manual period fixes (`ROLE_OPS`, reason mandatory, audited — backdating a period moves money-adjacent truth, so every fix carries actor + reason + before/after).
8. Step 7 — tests + container: time-travel test (test clock through grant → nudges-once-each → grace gates-pass + banner + no-refill → expiry lossless → re-upgrade identical); early-renewal test (extension math + single current row); cancel test (service-to-end, reminders stop, pre-scheduled downgrade executes identically); Stripe test (PSP cancel alongside, no stray invoices); rerun test (second run zero writes); IDOR test (cross-user 403 + RLS deny). Same image via `docker run` + `podman run` per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`.

## Data / API touches

- Schema: database plan above (mutates ledger-owned columns; reads `orders` history for renewal linking). Endpoints: `GET /api/v1/plan` (adds `period_end, status, renew_url` — owned by the ledger read path, extended here); `POST /api/v1/plan/cancel`; renewal = standard provider checkout with `{plan}` (no separate renew API — renewal IS checkout, keeping one purchase path); ops period-fix (audited). Notifications via the receipts channel (invoices-receipts slice).

## Acceptance criteria

- Time-travel drill: full lifecycle green with once-only nudges, passing grace gates, lossless downgrade, identical restore.
- Renewal drill: early-renew math exact; single current row.
- Cancel drill: to-end service, stopped reminders, identical pre-scheduled downgrade.
- Stripe drill: PSP-side cancel, no strays, ledger truth intact.
- Rerun drill: second run zero writes, audited once.
- Container drill: digest pair boots, compose green, rollback green.

## Out of scope

- Period/flag contracts (database plan above); lifecycle screens (frontend plan); ledger writer/reads (entitlement plans); receipts/history (invoices-receipts plans); rail settlement internals; plan matrix/AI caps; agency seats; dispute revocations; P4/P5.

## Open questions

- Q1: Grace length 7d (see database plan Q1)?
- Q2: Cancel-refund goodwill window (see database plan Q2)?
