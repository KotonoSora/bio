# Automate Contacts - User Stories and Business Rules (feature 43)

Phase 1 BA · Enforced by `docs/02-architecture-techlead/43-automate-contacts/`. Scope: `docs/01-requirements-ba/43-automate-contacts/01-document-context-and-scope.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Creator | see who automation reached, filter by source keyword/post, and export to leads/CSV | conversations become a durable consent-clean list | High |
| US-02 | Contact (IG user) | be messaged once as promised and suppressible in one action | one comment never becomes a newsletter | High |
| US-03 | Creator | trust that quota and consent are enforced before sending, not apologized for after | compliance is mechanical | High |
| US-04 | Ops | see quota/backlog aggregates without raw contact access | support hygiene without privacy exposure | Medium |

## Business rules

- **BR-01 Contact row (one per person per network per creator).** `automation_contacts` stores `profile_id`, `network` (instagram in v1), opaque `network_user_ref`, first automation + keyword + source media, `first_seen_at`, status, and provenance events; dedupe key `(profile_id, network, network_user_ref)` — repeat comments update `last_seen` + append events, never duplicate rows.
- **BR-02 Capture writes once.** Capture happens in 44's delivery pipeline at send intent (the moment the comment is answered); a failed send leaves no contact row (intent state decides). Capture is idempotent per (automation, comment).
- **BR-03 Consent boundary.** A comment is consent for the promised single reply/DM only (transactional class). Marketing class is never set by automation flows. Every sender consults the single eligibility check (`eligible = status active AND not suppressed AND consentClass ⊇ needed class`); no sender bypasses it.
- **BR-04 Suppression in one action.** `OPTOUT` keyword in any thread, the profile-link removal center, or the platform removal button (where offered) all land `status='suppressed'` + `suppressed_at` + reason, effective immediately; suppressed rows are excluded by the eligibility check everywhere (send, export-for-sending, future campaigns).
- **BR-05 Suppression is per-creator.** One creator's suppression never blocks another creator's independent consent; platform-level blocks are the platform's duty (rows flip `blocked_platform` on provider signals where available).
- **BR-06 Hygiene.** Platform-blocked rows prune to `blocked_platform`; duplicate identity merges keep the strictest consent class and earliest provenance; rows with no interaction for 12 months (`staleContactMonths`) park to `stale` — parked rows never receive sends and re-engage only after a fresh comment.
- **BR-07 Export discipline.** CSV export: owner-scoped, includes provenance columns, no PII beyond what the contact shared, audited (`contacts.exported` with row count), rate-limited, expiring download. Leads handoff: one idempotent `POST /internal/v1/leads` per eligible row (27's contract) with `source="automation:{automationId}:{keyword?}"`, `sourceRef=contactId`, `marketingConsent=false`, and the consent copy version actually shown.
- **BR-08 No consent upgrading.** Exports, handoffs, and dashboards carry the actual consent class and `consent_at`; nothing backdates, upgrades, or infers consent. An email captured mid-conversation (e.g., through the linked destination's form) keeps its own consent class from that capture surface.
- **BR-09 Quota (enforced pre-send in 44).** Free: 100 unique contacts/year; pro: 100k/month (`publish.quotas`); unique recipient per period counts once; one SQL counting function backs the gate, the meter, and reconciliation; over-quota holds sends as `quota_held` with upgrade-or-wait copy (44 surfaces it).
- **BR-10 Privacy.** Opaque refs only; no username scraping, no lookup; PII minimized to what the contact shared; ops sees aggregates only; RLS owner-scoped reads; zero token material in this feature.
- **BR-11 Audit.** Capture, suppression (all three mechanisms), merges, prunes, parks, exports, handoffs append to the service's `audit_log` (actor, action, ids, counts; never message bodies).
- **BR-12 Cross-creator isolation.** The same IG user may be a contact of many creators; rows, suppression, quota, and exports are strictly per-profile; no shared identity graph exists in v1.

## Acceptance criteria pointers

Drills in `docs/01-requirements-ba/43-automate-contacts/06-acceptance-criteria.md`: boundary, suppression, export, reconciliation, IDOR. GWT forms live there.
