# Marketing Consent and Broadcasts - User Stories and Business Rules (feature 48) - GATED

Phase 1 BA · GATED: design-only; AC-01 (absence) runs every release, all other drills run at/after the amendment. Scope: `docs/01-requirements-ba/48-marketing/01-document-context-and-scope.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Lead/buyer | give, refine, or withdraw marketing permission in plain language, with proof available on request | my inbox obeys me, not a settings maze | High |
| US-02 | Operator/marketer | send only to the provably willing and prove it per address on demand | campaigns never burn the shared sender reputation | High |
| US-03 | Deliverability stakeholder | keep bounces and complaints leaving the active set automatically and staying out | transactional mail keeps delivering | High |
| US-04 | Complaint investigator | replay any address's consent history as evidence | investigations resolve from facts alone | Medium |
| US-05 | Creator | keep my form leads mine, never merged into platform sends | my audience is my own | Medium |

## Business rules

- **BR-01 Gate (LIVE).** This feature is design-complete only: no table, route, job, chunk, or UI string may exist in any image or bundle until a spec amendment; absence drills run every release and a hit blocks promotion. The pipeline refuses `marketing` class today (`marketing_not_accepted` in `docs/02-architecture-techlead/32-monetize-overview/03-api-contracts-and-payloads.md`); the gate is tool-enforced, not policy.
- **BR-02 Consent classes (closed, product-wide).** `transactional` (receipts, grant confirmations, security notices — never gated, never unsubscribable, scoped to the triggering event), `lifecycle` (reminders, trial/expiry/dunning tied to the account — opt-out allowed; account deletion ends them), `marketing` (broadcasts, promos, win-back — explicit opt-in, one-click out). Every send declares exactly one class; misclassification is a consent violation in review.
- **BR-03 Capture proof.** Marketing opt-in stored append-only (`consents{owner_key, class, status, captured_at, source, copy_version}`); checkout and lead-form boxes unchecked by default, never pre-ticked; withdrawing is always easier than granting.
- **BR-04 Preference center.** Signed token, no login required; in-account view alongside; per-class toggles with honest consequences stated ("off = no promo emails; receipts and security notices continue"); effective immediately on save with a confirmation receipt of the change itself; account deletion withdraws everything and suppresses the address (re-opt-in starts clean history, never restores old grants).
- **BR-05 Enforcement.** One send-eligibility function (owned here, used by broadcasts and all future senders) checks class + status + suppression at confirm AND at send — consent withdrawn between quote and send is honored; new senders cannot ship without calling it (review gate); suppression beats any grant.
- **BR-06 Audience lifecycle (closed states).** `active → unsubscribed | suppressed | bounced | complained`; hard bounce = immediate suppression; soft bounce = three-strike suppression with decay; complaint is terminal (return only via explicit re-consent); transitions append history; states mutually exclusive, deduped at ingest; conflicting states resolve to the strictest.
- **BR-07 Ingestion.** All points normalize to one record: form-block leads (`profile_id, email, source, consent_at` — the 27 shape preserved), checkout opt-ins, CSV import (validated, row-level error report, duplicate-tolerant upsert that never overwrites a stricter state with a looser one), ops-added addresses (audited, reason required).
- **BR-08 Hygiene.** Resend bounce/complaint webhooks transition states immediately (verified, idempotent, stats-only); stale addresses (12 months no engagement) park out of eligibility without history loss; periodic list-health report (active share, bounce/complaint rates) to ops.
- **BR-09 Exports.** CSV with consent-state columns; owner/role scoped; audited; rate-limited; suppressed addresses' full data only via a support case (audited); no bulk export to personal email or third-party tools in v1.
- **BR-10 Broadcasts.** Sends go to `active` + marketing consent only, by query not trust; pre-send count shown and confirmed; two-step (compose → review-with-count → send); send-now only (no scheduling); sanitized template system (no raw HTML, no scriptable content); one-click List-Unsubscribe (mailto + URL) + sender identity footer on every send; honest subjects (no deceptive RE:/FWD:, no false urgency); prohibited content mirrors the ad-inventory list (adult, gambling, misleading earning claims).
- **BR-11 Stats and reputation.** Verified webhook records delivered/open/click/bounce/complaint per campaign; per-campaign and per-day caps are config (`marketing.volume`); complaint-rate breach auto-pauses further sends and pages ops; dedicated marketing subdomain keeps a burned sender away from receipts and grants.
- **BR-12 Silo and privacy.** Creator audiences never merge into platform sends; RLS scopes owners to own consents/leads, support to proof lookups, marketing to aggregates + active rows for sends; address handling minimizes exposure (hashes in suppression checks); zero addresses or tokens in logs beyond hashed refs.

## Acceptance criteria pointers

Drills in `docs/01-requirements-ba/48-marketing/06-acceptance-criteria.md`: absence (live), classification, proof, withdrawal, enforcement, deletion, lifecycle, import, hygiene, export, silo, eligibility, two-step, stats, reputation, exclusions. GWT forms live there.
