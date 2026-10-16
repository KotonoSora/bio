# AI Design Assist - Architecture and Tech Stack (feature 16) - GATED

Phase 2 TL · BA sources: `docs/01-requirements-ba/16-ai-design-assist/` (7 files, gated). GATED: design-complete only - no component below exists in any image, bundle, or migration until a spec amendment approves it (BR-01, AC-01). Stack pins per `docs/02-architecture-techlead/00-platform-baseline/`.

## Service boundary (future): in-service extension of profile-service

When built, the assist surface extends `profile-service` (06-09) so the guard validates against the same `ThemeRegistry` (09), block validators and reorder ops (08), and RLS-scoped profile writes (06) in-process. No new service, container, or queue.

| Component | Change (at amendment) | Notes |
|---|---|---|
| `AssistController` (new class) | `POST /api/v1/assist/theme|bio|ordering` | Authenticated, quota-checked, challenge + rate-limited; absent today |
| `AssistGuard` (new class) | Constrains every output to legal inputs of existing endpoints | Gallery `theme_id`, plain text through shared validators, reorder ops; inexpressible -> `refused:true` |
| `AssistQuota` (new class) | Consumes the plan-owned credit model (`kind='design-assist'`) | Forward port to feature 35; per-user/day caps; exhausted -> `429 assist_quota_exhausted` |
| `ModelPort` (new interface) | Server-side model call via the platform text capability; keys via environment/vault | No AI dependency enters `build.gradle.kts`; if the platform capability changes, only this port changes |
| `AssistAudit` (new class) | PII-minimized prompt/output logging + safety flags | Compile-time whitelist DTO controls what can reach the model |
| Frontend `AssistPanel` | Contextual entries in `/app/profile` and `/app/blocks` | Apply flows reuse existing endpoints; zero assist write surface of its own |

## Key decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | In-service extension, not an AI microservice | Suggestions validate in-process against theme/blocks/profile rules; an AI-service split adds prompt/PII plumbing and a second quota ledger for a dashboard-low-volume path; split triggers stay unevaluated until the amendment |
| D-2 | Tokens-only enforcement is compile-time + validator reuse | The guard cannot emit what the DTO cannot express; anything failing shared validators is rejected before display (BR-02) |
| D-3 | Human-in-the-loop by construction: zero write surface | Apply routes through existing audited endpoints (09 theme PUT, 06/07 profile PUT, 08 reorder); no fast lane exists (BR-04) |
| D-4 | Credit consumption via a port owned by feature 35 (plans slice) | Kind-keyed rows support one-wallet or per-surface budgets without DDL change (Q-02); mid-period plan changes never rewrite consumed credits (BR-12) |
| D-5 | Model calls server-side only; keys via environment/vault | No key ever reaches the browser or the repo; prompt content bounded by `AssistAudit`'s whitelist DTO |
| D-6 | Gate enforcement is live tooling, not policy | Absence drill (schema/OpenAPI/source/bundle) blocks promotion of any release (AC-01) |
| D-7 | Suggestions ephemeral; optional short-TTL drafts only if multi-step UX requires | Drafts are dashboard-staging state, never public-page inputs; the Q-04 decision is deferred to the amendment |
| D-8 | Availability gating (free vs pro) resolved per request via the plan snapshot (13 pattern) | The Q-01 decision is a plan-matrix choice; the code path supports either without branching |

## Reuse map

- Theme registry + gallery preview mechanism: feature 09 (`docs/02-architecture-techlead/09-themes-and-avatar/`).
- Block reorder + validators: feature 08 (`docs/02-architecture-techlead/08-link-blocks/`).
- Profile write + RLS + publish/purge path: features 06-07, 10.
- Plan windows + credit ledger: feature 35 (monetize batch; forward); platform text capability + secrets baseline: `docs/02-architecture-techlead/00-platform-baseline/`.
