# Automate Contacts - Document Context and Scope (feature 43)

Phase 1 BA · Feature 43 of 53 — publish/automate 3/4. Turns automation conversations into a durable, consent-clean contact list: every DM recipient recorded with provenance, messaged exactly as far as they agreed, exportable as leads — never a spam database. Sources: `automate/contacts/contact-lists.md`, `platform/delivery/publish-automate.md` (FR-5). Implemented by `docs/02-architecture-techlead/43-automate-contacts/`. Service: publish-service (port 8089, `publish_db`, Redis db 11).

## Context

A comment is consent for one reply, not for a newsletter — this feature draws that line in schema. Contact capture extends the lead precedent (`leads{profile_id, email, source, consent_at}`, features 27/28, profile-service): capture here records automation provenance and keeps the same consent classes; the leads handoff uses 27's `POST /internal/v1/leads` with an automation source stamp. The legacy audience/consent docs under `monetize/marketing/*` are gated (48–53) — this feature must not depend on or stub them; its own consent boundary stands alone.

## Objectives

| ID | Objective |
|---|---|
| OBJ-01 | Every automation-reached recipient becomes one deduped contact row with provenance (automation, keyword, post/media) |
| OBJ-02 | Consent boundary absolute: transactional-only by default; marketing requires a separate opt-in; every sender consults one eligibility check |
| OBJ-03 | Suppression that works in one action: `OPTOUT` keyword, profile-link removal center (platform button where offered), per-creator suppression |
| OBJ-04 | Export: owner-scoped CSV + leads handoff carrying real provenance and actual consent class — never upgraded, never backdated |
| OBJ-05 | Hygiene: platform-blocked pruning, strictest-consent merge on duplicates, 12-month stale parking (re-engagement requires a fresh comment) |
| OBJ-06 | Quota fairness enforced pre-send through one counting function (free 100/year, pro 100k/month, unique recipient per period) |

## Scope

In scope (SC-01..SC-06): `automation_contacts` + `contact_events` (V3); capture from 44's delivery pipeline; suppression evaluation; export (CSV + leads handoff); hygiene jobs (prune/merge/park); quota counting consumed by 44's pre-send gate; owner UI list/filters; ops read-only aggregates.

Out of scope: trigger matching and DM delivery (44); broadcast campaigns, audience lists, email marketing (gated 48–53); lead forms/search UX (27/28 own the lead surface); purchasing/checkout consent capture (33/34 own checkout opt-ins).

## Personas and permissions

| Role | CRUD Permissions | Description |
|---|---|---|
| Creator (owner) | R own contacts; U status via suppression; C export | Sees who automation reached, filters by source, exports |
| Contact (IG user) | U own row to suppressed (one action) | Messaged once as promised; suppressible without an account |
| Ops | R aggregates only | Quota/backlog dashboards; never raw contact lists |
| System (44's pipeline) | C capture rows; R eligibility | Pre-send gate consults the single eligibility check |

## Open-question resolutions

| # | Question (source) | Resolution |
|---|---|---|
| Q-01 | Self-removal mechanism (contacts Q1) | `OPTOUT` keyword (any thread) + profile-link removal center; platform removal button used where the API offers one; all three land the same `suppressed` transition |
| Q-02 | Stale window (contacts Q2) | 12 months (`publish.policy.staleContactMonths`); parked rows re-engage only after a fresh comment (new consent event) |
| Q-03 | Marketing gating | Marketing consent is a separate class that automation flows never set; the leads handoff carries the actual class only; gated 48–53 surfaces must re-check via 27's eligibility seam |
| Q-04 | Export scope | CSV = full contact list (opaque refs; no PII beyond what the contact shared); leads handoff = rows with a captured email + a real consent class, one idempotent `POST /internal/v1/leads` per row |

## Constraints carried

`network_user_ref` is opaque (IG scoped id; no scraping, no lookup by username); capture writes once (dedupe on `(profile_id, network, network_user_ref)`); every mutation audits; suppression is per-creator (platform-level blocks handled by the platform); exports audited and rate-limited; zero token material anywhere in this feature.
