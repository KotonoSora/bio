# Audience Lists (lead states, hygiene, and exports)

PLAN ref: P2-4 (extends `leads{profile_id, email, source, consent_at}` + CSV export with lifecycle states). Capture mechanics stay in their majors (form blocks, checkout); broadcasts in `marketing/campaign-broadcasts.md`; permission in `marketing/consent-preferences.md`.

## Purpose

Define the audience as a living, hygienic list — not a pile of emails: lifecycle states for every address, ingestion from every capture point, hygiene that runs continuously, and exports that respect consent. Broadcasts are only as clean as the list they send to, and sender reputation is only as strong as the weakest address in it. This doc owns list quality end to end.

## Users

- Operator/marketer: *As an Operator/marketer, I want to see list health (active, suppressed, bounced, complained), import carefully, export for analysis so that the audience as a living, hygienic list as defined here.* sees list health (active, suppressed, bounced, complained), imports carefully, exports for analysis.
- Lead: *As a Lead, I want to be captured once with source + consent, never duplicated into multi-address noise so that the audience as a living, hygienic list as defined here.* captured once with source + consent, never duplicated into multi-address noise.
- Deliverability (as a stakeholder): *As a Deliverability, I want to keep bounces and complaints leaving the active set automatically and staying out so that the audience as a living, hygienic list as defined here.* bounces and complaints leave the active set automatically and stay out.

## Functional requirements

1. Address lifecycle (closed states): `active → unsubscribed | suppressed | bounced | complained`; `bounced` splits hard/soft (hard = immediate suppression; soft = three-strike suppression with decay); `complained` is terminal (return only via explicit re-consent flow per the consent doc). Transitions append history; states are mutually exclusive (one row per address per audience, deduped at ingest).
2. Ingestion points (all normalize to the same record): form-block leads (`profile_id, email, source, consent_at` — the P2-4 shape, preserved), checkout marketing opt-ins, CSV import (validated, row-level error report, duplicate-tolerant upsert that never overwrites a stricter state with a looser one), and ops-added addresses (audited, reason-required). Every record carries source + captured consent class + timestamp from birth.
3. Dedupe and merge discipline: identity key is normalized lowercase email (plus audience scope where multiple audiences exist); re-capture updates source history and consent per the consent doc without resurrecting suppressed states; conflicting states resolve to the strictest (suppressed/complained always win over active).
4. Continuous hygiene (scheduled job + event-driven): Resend bounce/complaint webhooks transition states immediately (shared with the broadcast stats flow); stale-address re-engagement rules park long-inactive addresses out of broadcast eligibility (see Q1) without deleting their history; periodic list-health report (active share, bounce/complaint rates vs reputation thresholds) to ops.
5. Exports: CSV export (the P2-4 promise, kept) with consent-state columns included, owner/role-scoped, audited, rate-limited; exports never include suppressed addresses' full data beyond counts unless a support case requires it (then audit-logged). No bulk export to personal email or third-party tools in v1 (named future integration with its own review).
6. Multi-audience posture (v1 = one platform audience; creator-owned audiences deferred): creator lead exports (their form-block CSV) stay creator-scoped and never merge into platform broadcasts — audiences are siloed by owner from day one so the future split needs no migration.

## Data / API touches

- Tables: `leads` (P2-4 shape, extended with `status` + history) + `lead_events{lead_id, transition, actor, at}` (append-only) with standard RLS posture (creators see own form leads; marketing sees platform audience aggregates + active rows for sends; full exports restricted). All PG via `worker/db/*`.
- Endpoints: ops import (validated batch with row-level report), `GET /api/v1/ops/audience/health`, audited export; public surface limited to capture forms + unsubscribe (consent doc). Import path shares the abuse budgets (Turnstile/rate limits) with other public writes.
- Suppression sync: state transitions from bounces/complaints propagate to the shared suppression check synchronously (next send already clean), with the history row as proof.

## Acceptance criteria

- Lifecycle drill: hard bounce → immediate suppression; three soft bounces → suppression; complaint → terminal; re-capture never loosens a strict state; all transitions in history.
  *GWT: Given Lifecycle drill: hard bounce; When the transition executes; Then immediate suppression; three soft bounces → suppression; complaint → terminal; re-capture never loosens a strict state; all transitions in history.*
- Import drill: 10k-row CSV with duplicates/invalid/suppressed rows imports cleanly with an exact row-level report; no suppressed resurrection; abuse budgets hold.
  *GWT: Given Import drill context; When 10k-row CSV with duplicates/invalid/suppressed rows imports cleanly with an exact row-level report; Then abuse budgets hold.*
- Hygiene drill: seeded stale addresses park out of eligibility per the rule; health report matches hand computation; bounce/complaint webhooks transition states within seconds.
  *GWT: Given Hygiene drill context; When seeded stale addresses park out of eligibility per the rule; Then bounce/complaint webhooks transition states within seconds.*
- Export drill: CSV contains consent columns, respects scoping (creator vs platform), audited and rate-limited; suppressed full-data export blocked without a case.
  *GWT: Given Export drill context; When CSV contains consent columns, respects scoping (creator vs platform), audited and rate-limited; Then suppressed full-data export blocked without a case.*
- Silo drill: creator form leads never appear in platform broadcast eligibility (cross-audience leak probe returns empty).
  *GWT: Given Silo drill context; When the checked action occurs; Then creator form leads never appear in platform broadcast eligibility (cross-audience leak probe returns empty).*

## Out of scope

- Broadcast mechanics and stats (see `marketing/campaign-broadcasts.md`); consent classes and preference center (see `marketing/consent-preferences.md`).
- Capture UX (form blocks, checkout — their own majors constrained by these rules); transactional sends; third-party ESP migrations; P4/P5.

## Open questions

- Q1: Stale-address rule — inactivity window before parking out of eligibility (e.g. 12 months no engagement)?
- Q2: Creator-owned audiences — platform sends on creators' behalf someday, or creators always export-and-send themselves?
