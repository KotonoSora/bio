# Verification Badge and Follower Display - Context and Scope (feature 07)

Phase 1 BA · Feature 07 of 53 (link-to-bio) · Implemented by `docs/02-architecture-techlead/07-verification-and-followers/` (5 files). Extends the identity of feature 06; consumers: feature 10 (SSR badge/total), feature 11 (social deep links), feature 21 (export).

## Business objectives

| # | Objective | Measure |
|---|---|---|
| BO-1 | Authentic trust signal: the badge is only ever awarded by a manual ops decision and cannot be self-awarded or faked through markup. | Fake-badge probe green (AC-01); zero auto-award paths in v1. |
| BO-2 | Attested audience size: a combined follower total labeled by source, not a self-typed claim presented as audited. | Every total carries `self-reported` or `auto` provenance (AC-05). |
| BO-3 | Anti-impersonation: near-match claims are flagged, verified renames hide the badge until re-review, and revocation goes public within the purge SLA. | AC-03, AC-04, AC-12. |
| BO-4 | Page speed is never traded for freshness: totals are served from cache and a network outage never slows `/:username`. | Public p95 unchanged during refresh and outage drills (AC-07, AC-08). |
| BO-5 | No OAuth in v1: only keyless public-count fetches; a token vault is never stubbed. | Grep gate: zero vault tables/endpoints in the build. |

## In scope

- Verification application with evidence upload (blob keys only) and the 5-state badge machine `none|pending|verified|rejected|revoked`.
- Ops review queue with audited approve/reject/revoke (mandatory reason).
- Near-match advisory flagging of claims against verified usernames.
- Follower totals: capped and versioned self-reported counts + keyless auto fetches refreshed on a 24 h schedule, last-good + stale on failure.
- Socials editor data (`social` blocks' handles/URLs) and the public read of badge state + cached totals.

## Out of scope

- OAuth token vault, posting, and automations (P4; never stubbed here).
- SSR templates, cache, and purge mechanics (feature 10); blocks authoring (feature 08); themes/avatar (feature 09); per-click analytics (feature 12).
- Shop, leads, custom domains (their majors); AI design help (feature 16).

## Personas and RBAC matrix

| Role | CRUD Permissions | Description |
|---|---|---|
| Creator | Create/read own applications and socials; request refresh | Applies for verification, uploads evidence, edits socials and self-reported counts, watches status. |
| Visitor | Read public badge + totals only | Sees the badge and the combined total; never evidence, never drafts. |
| Ops reviewer | Read queue + evidence; decide approve/reject/revoke | Every decision carries actor + reason in the audit log. |
| Public-site service | Internal read (badge state + cached totals) | SSR renders the platform badge component; never touches evidence. |

## Open questions (non-blocking; v1 defaults)

| # | Question | v1 default | Revisit trigger |
|---|---|---|---|
| Q-01 | Auto-verification signals in v2 | manual review only | ops volume exceeds 100 applications/week |
| Q-02 | Follower freshness SLA | 24 h cache | top-profile complaints or a faster source appears |
| Q-03 | Evidence per application | single file (PDF/PNG/JPEG, <= 10 MB) | legal or ops requests dossiers |
| Q-04 | History retention per profile | keep full history | row count per profile exceeds 20 in practice |

Related specs: identity rows `docs/01-requirements-ba/06-public-profile/02-user-stories-and-business-rules.md`; public rendering `docs/02-architecture-techlead/06-public-profile/03-api-contracts-and-payloads.md` (additive extension); deep links `docs/01-requirements-ba/11-deep-links-and-pwa/`.
