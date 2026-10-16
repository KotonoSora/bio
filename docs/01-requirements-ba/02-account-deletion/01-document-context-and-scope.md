# Account Deletion - Context and Scope (feature 02)

Phase 1 BA · Feature 02 of 53 (account) · Implemented by `docs/02-architecture-techlead/02-account-deletion/` (5 files). Extends `account-service` (feature 01); consumers: profile-service (public-surface 404), public-site-service (cache purge), features 41-44 (cancel scheduled work), 36 (billing stop), 45-47 (org-owner departure).

## Purpose

Let users leave completely and verifiably: request deletion in account settings, pass a 30-day grace period with full undo, then have personal data purged across every service — while money records required for tax/audit survive in minimized form and the platform never retains a usable ghost of the account. Deletion is as easy to find as signup and as trustworthy as the privacy notice claims.

## Objectives

- O-1 One request path: step-up re-auth, plain-language consequences, export-first offers, typed confirmation, immediate deactivation.
- O-2 Full undo in grace: login + confirm restores byte-identical state; reminder at day 23 carries the exact purge date.
- O-3 Verifiable purge: a daily idempotent job deletes owner-scoped rows across every registered service and emits a per-account certificate whose counts reconcile.
- O-4 Minimized survival: only financial lines, legal-hold evidence, audit rows, and the deletion record + certificate survive; nothing else.
- O-5 Collateral safety: co-members, buyers, and support counterparts are never purged as collateral.

## Personas and RBAC

| Role | CRUD Permissions | Description |
|---|---|---|
| Leaving user | Create request; read status; cancel (restore) within grace | Requests deletion, understands what vanishes vs stays, changes their mind, or confirms and departs. |
| Staying contact/buyer | none (their own rows unaffected) | Their receipts survive minimized; nothing of theirs is purged as collateral. |
| Finance/legal/auditor | Read retained financial footprint + certificates | Verifies the mandated footprint with nothing more. |
| Support/ops | Read deletion status + certificate for a ticket; never deletes, never purges | Answers "is it gone?" from the certificate; cannot trigger or bypass deletion. |
| Org co-members (indirect) | none | Org rows follow agency rules (features 45-47), never this purge. |

## Resolved open questions

| Q | Decision |
|---|---|
| Q-01 Legal retention floors | Counsel-owned config: exact tax/audit year counts and the hashed-email policy arrive as configuration before launch (launch blocker); v1 purge logic ships the minimized set and reads the floors as config — no invented numbers. |
| Q-02 Username cooling after purge | During grace the name stays held (profile suspended); at purge the name is permanently retired (consistent with feature 06's permanent-reservation decision) - release only via legal/abuse review. Email is reusable immediately post-purge; no consent/suppression row resurrects. |
| Q-03 Purge timing | Day 30 at the daily run (03:30 UTC); certificate emitted in the same run on full success. |
| Q-04 Reminder | Exactly one transactional email at day 23 (±2 h) carrying the purge date; a bounced reminder is surfaced to support. |

## In scope

- Request / restore / status endpoints, step-up and typed-confirmation gating, consequences + export offers.
- Deactivation fan-out: session revocation, public-surface 404 within 60 s, sitemap drop, scheduled-work cancellation, billing stop, username holding.
- Purge runner, participant contract, per-service purge handlers (profile, link, analytics now; later majors register as they migrate), certificates, weekly residual probes.
- Retention split (vanishes vs stays), cooling/retirement rules, audit coverage.

## Out of scope

- Subscription cancel/downgrade math and "no charges during grace" mechanics (feature 36 — consumes the deactivation event; asserted here as a contract drill).
- Export generation itself: profile JSON (features 17-21), leads CSV (27-28), invoices (37) — this feature only offers the links first.
- Per-feature purge implementation detail lives in each service's own spec; org dissolution/transfer (45-47); legal-hold policy contents (legal-owned, consumed).
- Account suspension for abuse (ops moderation, feature 29's link pattern extended later) — distinct from user-initiated deletion.

Related: rules in `docs/01-requirements-ba/02-account-deletion/02-user-stories-and-business-rules.md`; error surface in `docs/01-requirements-ba/02-account-deletion/05-exception-handling-and-errors.md`; contracts in `docs/02-architecture-techlead/02-account-deletion/03-api-contracts-and-payloads.md`.
