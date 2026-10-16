# Marketing Consent and Broadcasts - Architecture and Tech Stack (feature 48) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/48-marketing/` (7 files, gated). GATED: design-complete only — no component below exists in any image, bundle, or migration until a spec amendment approves it (BR-01, AC-01). Stack pins per `docs/02-architecture-techlead/00-platform-baseline/`. Pipeline seam: `docs/02-architecture-techlead/32-monetize-overview/03-api-contracts-and-payloads.md` (`sendClass` + 27's suppression read; no stub).

## Service boundary (future): `marketing-service`

When built: new service `marketing-service` (port 8091, `marketing_db`, Redis db 12) — consent, audience, and campaigns are marketing's own data, kept out of the money kernel's database. The billing notification pipeline extends to route any non-transactional, non-lifecycle class through marketing's send-eligibility read; transactional and lifecycle senders keep their current path and consult only the shared suppression check.

| Component (at amendment) | Change | Notes |
|---|---|---|
| `ConsentService` (new) | Append-only `consents` + proof exports | One permission model; every sender declares exactly one class |
| `EligibilityCheck` (new) | Single send-eligibility function (class + status + suppression) | Mandatory internal call at confirm AND at send; new senders cannot ship without it |
| `PreferenceCenter` (new) | Signed-token public surface + in-account mirror | No login; immediate effect; confirmation receipt |
| `AudienceService` (new) | Lifecycle states, ingestion, hygiene jobs, exports | Siloed by owner; strictest-state wins |
| `CampaignService` (new) | Compose → review → send-now → stats; caps + auto-pause | Sanitized templates; per-recipient idempotency; no scheduling/segments/A-B |
| `ResendWebhook` (new) | Verified, idempotent bounce/complaint/stats events | Transitions states ≤ 60 s; stats-only, no grant-like side effects |
| Pipeline extension (billing-service) | `sendClass='marketing'` path calls `EligibilityCheck` | Today it refuses `marketing_not_accepted`; the refusal stays until amendment |

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | New `marketing-service` owns consents/suppressions/audience/campaigns; `marketing_db`; Redis db 12 for rate/token state | Microservice doctrine: each service owns its database; marketing data stays out of `billing_db` |
| D-2 | One `EligibilityCheck` implementation; every sender calls it (review gate) | BR-05; misclassified or unchecked senders fail review — structure, not discipline |
| D-3 | Consent history append-only; suppression separate and global (address-hash keyed) | Suppression beats grants; proof survives every later change |
| D-4 | Audience states closed and mutually exclusive; strictest-state resolution on merge | BR-06/BR-07; no resurrection paths except explicit re-consent |
| D-5 | Broadcasts = sanitized templates + two-step confirm + send-now; v1 exclusions absent from UI/API | BR-10; the highest-abuse surface gets the most restraint |
| D-6 | Reputation guardrails: config-owned caps, complaint-rate auto-pause, dedicated marketing subdomain | BR-11; a burned marketing sender must never take down receipts/grants |
| D-7 | Webhooks verified + idempotent; transitions synchronous into the shared suppression check | BR-08; next send is already clean |
| D-8 | Creator audiences siloed by owner key from day one | Q-04; the future split needs no migration |

## Cross-edits (all at amendment, not now)

| Target | Edit (at amendment) |
|---|---|
| `docs/02-architecture-techlead/00-platform-baseline/01-architecture-and-service-topology.md` | new `marketing-service` row (8091, `marketing_db`, Redis 12) |
| `docs/02-architecture-techlead/32-monetize-overview/03-api-contracts-and-payloads.md` | pipeline extension: `marketing` class path via `EligibilityCheck`; error catalog note |
| `docs/02-architecture-techlead/28-lead-search/` | preference-center superset link (28's page is the in-product surface; the center is canonical) |
| `MIGRATION.md` | feature 48 row (doc migration landed with this batch; build at amendment) |
