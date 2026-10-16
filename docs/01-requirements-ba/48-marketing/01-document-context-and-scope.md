# Marketing Consent and Broadcasts - Document Context and Scope (feature 48) - GATED

Phase 1 BA · Feature 48 of 53 — gated backlog 1/6. GATED: design-complete only — no table, route, job, or UI string ships until a spec amendment approves it (BR-01, AC-01); the existing gate stays closed (the notification pipeline refuses `marketing` class with `marketing_not_accepted` today). Sources: `marketing/consent-preferences.md`, `marketing/audience-lists.md`, `marketing/campaign-broadcasts.md`. Implemented (future) by `docs/02-architecture-techlead/48-marketing/`.

## Context

Receipts, reminders, and lead captures already exist and each claims "consent-gated" in its own doc — without one shared model those claims mean different things per sender, and the first complaint investigation finds gaps instead of proof. This feature defines the single permission model every marketing send obeys: closed consent classes with capture proof, a no-login preference center, a living hygienic audience, and one broadcast path (compose → review → send-now → measure). The future host is `marketing-service` (port 8091, `marketing_db`, Redis db 12); the pipeline extension seam is recorded in `docs/02-architecture-techlead/32-monetize-overview/03-api-contracts-and-payloads.md` (sendClass + 27's suppression read; no stub). Nothing here is built while gated.

## Objectives

| ID | Objective |
|---|---|
| OBJ-01 | Closed consent classes (`transactional`, `lifecycle`, `marketing`) with append-only capture proof (timestamp, source, copy version) |
| OBJ-02 | Preference center without login (signed token) + in-account view; one-click unsubscribe beats the complaint form to the punch |
| OBJ-03 | Living audience: closed lifecycle states, continuous hygiene, exports that respect consent |
| OBJ-04 | One broadcast path with volume caps and complaint-rate auto-pause; transactional delivery provably unaffected |
| OBJ-05 | Structural enforcement: one send-eligibility function at confirm AND at send; suppression beats any grant |
| OBJ-06 | Reputation isolation: dedicated marketing sending subdomain; creator audiences siloed from platform sends |

## Scope

In scope (at amendment, SC-01..SC-06): consent classes + proof history; preference center + unsubscribe; audience states, ingestion, hygiene, exports; broadcast compose/review/send/stats; eligibility enforcement point; ops health + proof-of-consent lookups.

Out of scope: capture UX (form blocks 06–16, checkout 33 — constrained by these rules, owned there); transactional receipts and lifecycle reminders (their own senders — shared suppression only); promo/affiliate/advertising content (their own features); segments, scheduling, A/B testing, creator co-sends (named v1 exclusions); legal jurisdiction mapping (legal-owned input).

## Personas and permissions

| Role | CRUD Permissions | Description |
|---|---|---|
| Lead/buyer | R own consent; U grant/withdraw | Gives, refines, withdraws permission in plain language, proof on request |
| Operator/marketer | C/R campaigns; R audience + health; C imports/exports (audited) | Sends only to the provably willing; proves it per address |
| Complaint investigator | R consent history + proof exports | Replays any address's consent history as evidence |
| Deliverability stakeholder | R health + reputation metrics | Keeps bounces/complaints out and the shared reputation shielded |

## Open-question resolutions

| # | Question (source) | Resolution |
|---|---|---|
| Q-01 | Lifecycle opt-out granularity (consent Q1) | One lifecycle toggle in v1; per-topic splits deferred — honest simplicity beats a granularity matrix nobody reads |
| Q-02 | Re-consent after suppression (consent Q2) | Explicit re-consent flow only (fresh signed flow + confirmation receipt); suppression never lifts automatically; attempts rate-limited 3/address/year as an abuse guard — no statute-derived cooling period invented |
| Q-03 | Stale-address parking (audience Q1) | 12 months without engagement parks the address out of broadcast eligibility; history kept; new engagement unparks |
| Q-04 | Creator-owned audiences (audience Q2) | Siloed by owner: creator form leads export via 27's creator-scoped CSV and never merge into platform broadcasts; platform-on-behalf sends deferred |
| Q-05 | Launch volume caps (broadcasts Q1) | Config-owned (`marketing.volume`); launch values set with ops at the amendment; auto-pause + paging are the mechanism, thresholds are config |
| Q-06 | Sending-domain separation (broadcasts Q2) | Dedicated marketing subdomain from launch — reputation isolation is the point; transactional delivery provably separate |

## Constraints carried

Suppression beats any grant (a suppressed address with a stale grant row sends nothing); consent history append-only, never overwritten; misclassified sends are treated as consent violations in review; marketing opt-in unchecked by default, never pre-ticked; withdrawing always easier than granting; no raw HTML paste; v1 exclusions absent from UI and API (attempts 404/422, not silent no-ops).
