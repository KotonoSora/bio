# AI Design Assist - Context and Scope (feature 16) - GATED

Phase 1 BA · Feature 16 of 53 (link-to-bio, appearance; closes the link-to-bio major) · GATED: design-complete only - no build, no routes, no tables, no stubs until a spec amendment approves it (AGENTS.md gate). This directory is the only place assist scope exists; every other feature shows zero assist surface (BR-01, AC-01). Implemented by `docs/02-architecture-techlead/16-ai-design-assist/` (5 files, gated the same way).

## Business objectives

| # | Objective | Measure |
|---|---|---|
| BO-1 | Token-safe design help: theme picks, bio polish, link-order tidy - suggestions only, never custom CSS/JS/HTML | Guardrail drill: adversarial CSS/JS demand returns gallery-valid suggestions or a refusal (AC-02) |
| BO-2 | Human approval mandatory: nothing reaches the public page without explicit per-suggestion Apply plus the normal publish path | Human-loop drill: audit shows actor = user on every applied change (AC-03) |
| BO-3 | Capped shared budget: every generation draws from the plan-owned AI credit model | Quota drill: exhausted credits block generation with manual editing fully intact (AC-04) |
| BO-4 | Honest assistance: AI origin disclosed in-dashboard, low confidence flagged, VN + EN per creator locale | Diff drill green (AC-08); locale drill green (AC-11) |
| BO-5 | Graceful absence: AI outage or quota disables only the assist panel; manual editing never degrades | Outage drill green (AC-09) |

## In scope (design only until the gate lifts)

- Closed suggestion set v1: theme recommender (quiz or auto -> ranked gallery picks with reasons), bio/display-name polish (3 drafts, tone selector, length cap), link-order/title tidy (proposed positions + title edits; from click history where available, labels-only where not).
- Tokens-only guard mapping every output to legal inputs of existing endpoints (theme gallery id; plain-text bio/title through the same validators; reorder through the blocks API).
- Dashboard diff staging with per-suggestion Apply; apply flows reuse existing profile/theme/blocks endpoints unchanged.
- Shared credit consumption (`kind='design-assist'`), per-user/day caps, abuse controls, injection-hardened prompts, PII-minimized logging.
- Gate enforcement itself: absence drills in every release until the spec amendment lands.

## Out of scope

- JD-tailor resume AI (separate surface and prompts; credit ledger owned by the plans slice, feature 35); image/avatar generation; auto-publishing agents; chatbots; freeform page generation; custom CSS/fonts; P4 prompts; P5 scale-out; resume/shop/publish majors consume nothing from here.

## Personas and RBAC matrix

| Role | CRUD Permissions | Description |
|---|---|---|
| Creator | Generate suggestions for own profile; apply/discard per suggestion via existing endpoints | Asks for design help, previews each suggestion, accepts or discards |
| Visitor | None - sees only human-approved output | No AI-experimental markup ever reaches the public page |
| Platform cost controller | Read quota + cost metrics; no profile data access | Confirms the capped shared budget holds before anything ships |
| Safety reviewer (audited ops) | Read PII-minimized prompt/output logs | Reviews safety flags; no cross-user business-data reads |

## Resolved open questions

| # | Question | v1 resolution | Decide at |
|---|---|---|---|
| Q-01 | Free with tight quotas vs pro-only | Both designs supported (quota keys per user + period); the choice is a plan-matrix decision, not a code branch | spec amendment (with feature 35) |
| Q-02 | Shared wallet vs per-surface budgets with JD-tailor | Ledger rows are kind-keyed; either fits without DDL change; selection deferred | spec amendment |
| Q-03 | Panel placement (inline per-editor vs drawer) | Inline contextual entries in `/app/profile` and `/app/blocks` (near the acted-on editor) | - |
| Q-04 | Draft persistence across sessions | Ephemeral only in v1; the short-TTL drafts table exists only if multi-step UX proves necessary | spec amendment |

Boundary notes: this feature holds no executable tasks while gated; its implementation plan marks every build task as `AT PLAN UPDATE` and keeps only absence drills live. Numeric credit caps are finance/plan values set with feature 35's plan catalog - never invented here. Model calls run server-side through a platform text capability with keys from the environment/vault only. The legacy "PLAN ref: none" maps to our gate: assist is outside the approved baseline stack until the amendment.

Related specs: themes `docs/01-requirements-ba/09-themes-and-avatar/`; blocks `docs/01-requirements-ba/08-link-blocks/`; profile `docs/01-requirements-ba/06-public-profile/`; entitlement + plan window `docs/02-architecture-techlead/00-platform-baseline/`; ledger consumption contract with feature 35 (monetize batch, pending).
