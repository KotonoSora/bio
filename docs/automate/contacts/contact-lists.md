# Contact Lists (automation-built leads)

PLAN ref: P4-3 (contacts quota) + P2-4 (leads precedent: `leads{profile_id, email, source, consent_at}`). P4 scope — do NOT build in P1–P3. Capture here extends the audience model in `monetize/marketing/audience-lists.md` with automation provenance; consent classes in `monetize/marketing/consent-preferences.md` apply unchanged.

## Purpose

Turn automation conversations into a durable, consent-clean contact list: every DM recipient recorded with provenance (which automation, which keyword, which post), messaged exactly as far as they agreed, and exportable as leads — without ever becoming a spam database. A comment is consent for one reply, not for a newsletter; this doc draws that line in schema.

## Users

- Creator: *As a Creator, I want to see who automation reached, filter by source keyword/post, and export to leads/CSV so that conversations become a durable consent-clean list.* Sees who automation reached, filters by source keyword/post, exports to leads/CSV.
- Contact (IG user): *As a Contact, I want to be messaged once as promised and suppressible in one action so that a comment never becomes a newsletter subscription.* Messaged once as promised, suppressible in one action, never auto-subscribed to anything else.
- Marketer (later, P4 campaigns): *As a Marketer, I want to inherit only explicitly opted-in contacts so that broadcast use stays proven-consented.* Inherits only explicitly opted-in contacts — the list is guilty-until-proven-consented for broadcast use.

## Functional requirements

1. Contact row: `automation_contacts{profile_id, network_user_ref (opaque, never raw handle where avoidable), first_automation_id, first_keyword?, source_media_id?, first_seen_at, status}` with closed status set: `active|suppressed|unsubscribed|bounced_platform`. Deduped per `(profile_id, network, network_user_ref)` — one row per person per creator, with `contact_events` (append-only: every trigger, send, opt-out) as the history.
2. Consent boundary (absolute): automation contact grants transactional/lifecycle contact only (the promised reply + booking/account notices where a purchase follows). Marketing use requires separate explicit opt-in captured in the preference flow (see consent doc) — export-to-broadcast without it is refused by the eligibility function, not merely discouraged. The list UI labels every row with its usable-classes so creators never guess.
3. Suppression: platform opt-outs, `stop`-class replies, and one-click list-removal links (in any list-driven message) flip status immediately; suppressed rows are excluded by every sender through the shared eligibility check. Suppression is per-creator (Alice's suppression never blocks Bob's automation to the same user — but platform-level blocks do).
4. Lead export: CSV + `leads` handoff carrying `source=automation:{automation_id}:{keyword?}` and `consent_at` reflecting the actual class granted (never backdated, never upgraded). Exports are audited, rate-limited, and scoped to the owning profile (creator CSV contains only own contacts).
5. Hygiene: platform-deleted/deactivated accounts pruned on detection (status `bounced_platform`, excluded from counts); duplicate merges keep the strictest status; stale contacts (no event in 12 months, see Q2) park out of any future proactive use while remaining exportable history.
6. Quota accounting alignment: quota-counted contacts (per the delivery doc's unique-recipient-per-period rule) reconcile exactly with countable rows here — the quota dashboard and the list draw from one counting function, so "contacts used" never disagrees with list size.

## Data / API touches

- New tables `automation_contacts` + `contact_events{contact_id, automation_id?, event (triggered|replied|dm_sent|opt_out|suppressed|exported), at, detail}` (append-only; all PG via `worker/db/*`; standard `user_id` + RLS; owners see own contacts; ops sees aggregates + dispute rows only).
- Endpoints: owner list/filter (by automation, keyword, media, status), `POST /api/v1/automate/contacts/:id/suppress` (owner + signed-token self-removal), audited export (CSV + leads-handoff), quota-usage read (same function as the send gate). No public enumeration (no handle search, no bulk lookup).
- Retention: contact rows follow the platform retention policy; account deletion by the creator offers export-then-purge; evidence rows for disputes survive per the legal-hold rule only.

## Acceptance criteria

- Boundary drill: automation-only contact fails campaign eligibility (refused with the opt-in path stated); post-opt-in contact passes. *GWT: Given an automation-only or post-opt-in contact; When campaign eligibility is checked; Then automation-only fails with the opt-in path stated and post-opt-in passes.*
- Suppression drill: opt-out mid-campaign halts that recipient's pending intents; cross-automation suppression holds; other creators unaffected (except platform-level blocks). *GWT: Given an opt-out mid-campaign; When pending intents and cross-automation sends evaluate; Then that recipient's intents halt, suppression holds cross-automation, and other creators are unaffected except by platform-level blocks.*
- Export drill: CSV/leads handoff carries exact source + consent-class stamps; creator-scoped (no cross-profile rows); audited and rate-limited. *GWT: Given an export request; When the CSV/leads handoff runs; Then it carries exact source plus consent-class stamps, stays creator-scoped, and is audited and rate-limited.*
- Reconciliation drill: quota-used figure equals countable contact rows for the period (fixture-verified, no drift). *GWT: Given quota state and contact rows for a period; When reconciled against fixtures; Then the quota-used figure equals countable rows with no drift.*
- IDOR drill: user A contact reads/exports against user B profiles return 403; public endpoints expose nothing enumerable. *GWT: Given user A contact reads/exports against user B profiles; When requested; Then 403 results and public endpoints expose nothing enumerable.*

## Out of scope

- Trigger matching (see `triggers/comment-triggers.md`); reply/DM sending and quotas (see `delivery/dm-delivery.md`).
- Broadcast mechanics and stats (`monetize/marketing/`); form-block lead capture (P2-4, separate surface); agency cross-profile contact views (P4 agency scope).

## Open questions

- Q1: Self-removal UX inside IG (no email-style footer possible) — keyword OPTOUT + profile-link removal center sufficient, or platform-button integration where offered?
- Q2: Stale-contact parking window — 12 months proposed; shorter for high-volume creators?
