# Account Deletion (request, grace, purge)

PLAN ref: P1a-1 (30-day delete flow). The one-line promise in `link-to-bio/profile/public-profile.md` is executed here. Subscription/fee consequences defer to `monetize/billing/subscription-lifecycle.md` (cancel-first ordering); retention floors defer to legal input (see Q1).

## Purpose

Let users leave completely and verifiably: request deletion in account settings, pass a grace period with full undo, then have personal data purged — while money records required for tax/audit survive in minimized form and the platform never retains a usable ghost of the account. Deletion must be as easy to find as signup and as trustworthy as the privacy notice claims.

## Users

| Role | Permissions | Notes |
| --- | --- | --- |
| Leaving user | Request + cancel/restore own deletion (step-up gated, login + confirm within grace) | Requests deletion, understands exactly what vanishes and what legally must stay, changes their mind within grace, or confirms and departs. |
| Staying contact/buyer (indirect) | — | Their transactional receipts survive in minimized form; their data is never purged as collateral. |
| Finance/legal/auditor | — | Retains the mandated financial footprint with nothing more. |

| Story ID | As a | I want to | So that | Priority |
| --- | --- | --- | --- | --- |
| US-01 | Leaving user | request deletion with a clear vanishes-vs-stays explanation and full undo within grace | I can leave completely and verifiably | High |
| US-02 | Staying contact/buyer | my transactional receipts to survive minimized with no collateral purge | my records remain intact when another user leaves | High |
| US-03 | Finance/legal/auditor | the mandated financial footprint retained with nothing more | tax/audit duties are met without excess retention | High |

## Functional requirements

1. Request flow (gated like all destructive actions): account settings → Delete → step-up re-auth (see `access/password-security.md`) → plain-language consequences screen (vanishes vs stays, per §3) with export-first offers (profile JSON per P2-5, leads CSV, invoices) → typed confirmation (username or DELETE) → immediate deactivation. No support ticket, no retention call, no dark pattern — the button is as discoverable as plan settings.
2. Deactivation (immediate): sessions revoked everywhere; public pages (`/:username`, `/cv`, `/shop`, custom domains) return 404 and drop from `sitemap.xml`; scheduled posts/automations/campaigns cancelled; broadcasts exclude the addresses on next eligibility check; username enters a cooling reservation (re-registration blocked during grace to prevent impersonation squats — see Q2 for post-purge policy).
3. Grace (30 days): account restorable by login + confirm (full fidelity — nothing degrades during grace); reminder at day 23 (one email, transactional class, with the purge date); subscription billing stopped at request (service runs already-paid term per lifecycle; no new charges during grace); reactivation resumes billing only via fresh checkout (never auto-recharge).
4. Purge (day 30, job-executed, verified): deleted — profile rows, blocks, products, courses, leads owned, contacts, media in R2 (keys + derived assets), OAuth vault rows, sessions, preference/consent rows beyond the proof minimum. Minimized-retained — order/invoice financial lines (buyer email hashed where policy allows, amounts/dates intact for tax/audit), dispute evidence under legal hold, and append-only audit rows naming the deletion event itself. Purge job emits a per-account completion certificate (counts per table) retained as its own proof.
5. Collateral protection: shared/org data (agency rosters, other members' content, buyers' receipts) untouched — purge scopes strictly to the deleter's owned rows (org-owner departure transfers or dissolves the org per the agency docs instead of cascading deletes). Support cases referencing the account keep their metadata with identity minimized.
6. Re-signup: post-purge email/username reusable per the cooling policy (Q2); new account starts with zero history (old consent/suppression rows do not resurrect — a fresh `granted` requires a fresh action, per the consent model's suppression-beats-grant rule).

## Data / API touches

- New: `deletion_requests{user_id UNIQUE, requested_at, purge_due_at, status (grace|purged|cancelled), certificate_ref?}` + purge-runner job (daily, idempotent per user, per-table counters) + completion certificates (R2, retained per policy). Status transitions audited; grace-cancel (reactivation) is itself an audit row.
- Endpoints: `POST /api/v1/account/delete-request` (step-up gated), `POST /api/v1/account/delete-cancel` (login, within grace), `GET /api/v1/account/delete-status` (countdown + consequences recap). Purge job internal only; per-table delete functions owned by each major's schema (each major documents its rows' deletion behavior against this flow).
- Verification: post-purge probe suite (spot-check user id across all business tables + R2 key listing) runs per batch with failures paging ops — purge is tested, not trusted.

## Acceptance criteria

- Flow drill: request → immediate 404s + sitemap drop + session death + scheduled-work cancellation; login within grace restores byte-identical state; day-30 purge emits a certificate whose counts reconcile with pre-purge inventory. *GWT: Given a deletion request in grace; When the user logs in to restore or the day-30 purge runs; Then state restores byte-identical or a certificate emits with counts reconciled to pre-purge inventory.*
- Billing drill: no charges during grace; paid-term service honored; reactivation never auto-recharges. *GWT: Given an account in deletion grace; When billing runs or the user reactivates; Then no new charges occur, paid-term service is honored, and reactivation never auto-recharges.*
- Retention drill: financial lines + legal-hold evidence survive minimized; everything else (including R2 bytes and vault rows) gone; probe suite green. *GWT: Given post-purge state; When the probe suite spot-checks tables and R2 keys; Then financial lines plus legal-hold evidence survive minimized, everything else is gone, and probes are green.*
- Collateral drill: org co-members, buyers, and support-case counterparts unaffected (row-level verification). *GWT: Given a deleter with shared org/buyer/support rows; When purge scopes to owned rows; Then co-members, buyers, and counterparts are unaffected at row level.*
- Cooldown drill: username/email reuse follows the decided policy exactly (squat attempt during cooling fails; post-policy reuse starts clean with no resurrected consent). *GWT: Given the cooling policy; When a squat is attempted during cooling or reuse is attempted after; Then cooling squats fail and post-policy reuse starts clean with no resurrected consent.*

## Out of scope

- Subscription cancel/downgrade mechanics (`monetize/`); per-profile visibility toggles (see `link-to-bio/profile/public-profile.md`); consent classes (see `monetize/marketing/consent-preferences.md`).
- Org dissolution/transfer detail (`docs/agency/`); legal-hold policy contents (legal-owned input, consumed here).

## Open questions

- Q1: Legal retention floors (tax/audit years, minimized-field list) — counsel sign-off before launch?
- Q2: Username cooling after purge (permanent reservation vs timed release) — same answer as the profile doc's Q2, decided once here?
