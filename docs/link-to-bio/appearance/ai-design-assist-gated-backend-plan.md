# AI Design Assist Gated Backend Plan (suggestion endpoints — buildable at plan update)

PLAN ref: none — new scope beyond P0–P5; needs a plan update before build. GATED: design-complete only, never in P1–P3 images. BA source: `docs/link-to-bio/appearance/ai-design-assist.md`. Storage contract in `docs/link-to-bio/appearance/ai-design-assist-gated-database-plan.md` (ledger reuse, nothing created now). Panel in the AI frontend plan (splits next to this file). JD-tailor (P3-3) is a separate surface and quota.

## Purpose

Hold the buildable server design for token-safe AI suggestions so that, at the plan update, a developer implements with no further decisions — and until then, nothing ships. The assistant may suggest and draft; it never emits custom CSS/JS/HTML, and nothing goes public without explicit human approval.

## Users

- Backend developer (future): ships three suggestion endpoints behind the shared credit model.
- QA (now): runs the absence drill; (future) the guardrail/quota/injection/cost drills.
- Platform (cost control): caps spend via the shared ledger before a single suggestion ships.

## Functional requirements

1. Stack and topology (when built): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — monolith suggestion endpoints (package `com.app.bio.assist`, GATED). Monolith pros: suggestions validate against the same `ThemeRegistry`/block validators/RLS in-process; quota checks share the ledger transaction. Microservices cons avoided: an AI-service split adds prompt/PII boundary plumbing and a second quota ledger for a dashboard-low-volume path. Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; unevaluated until the plan update. Starters (when built): web, validation, security, actuator, micrometer (no new starters; AI calls go through the platform text capability, not a new dependency).
2. Step 1 — `api/AssistController.kt` (GATED): `POST /api/v1/assist/theme` (quiz/auto → ranked gallery `theme_id` picks + reasons), `POST /api/v1/assist/bio` (3 drafts, tone selector, length cap), `POST /api/v1/assist/ordering` (proposed positions + title edits from click history where available) — all authenticated, quota-checked, Turnstile + rate-limited (suggestion endpoints are anonymous-adjacent abuse paths: prompt-cost attacks are the bot model here — each generation costs platform money, so throttling is cost defense, not just fairness). User content treated as data (system instructions never overridable); model calls server-side (keys via env/vault, never repo).
3. Step 2 — `service/AssistGuard.kt`: tokens-only enforcement — output constrained to legal inputs of existing endpoints (gallery `theme_id`, plain-text bio/titles through XSS/length validators, reorder ops through blocks API); anything inexpressible as legal API input is rejected before display (adversarial custom-CSS/JS demand → `200 {suggestions:[], refused:true}`).
4. Step 3 — `service/AssistQuota.kt`: draws from the ledger (database plan rule 2) with per-user/day cap; exhausted → `429 assist_quota_exhausted` with upsell-safe message; manual editing fully unaffected. Prompts/outputs logged with PII minimization; Vietnamese + English per creator locale; AI origin disclosed in-dashboard (no visitor-facing labels).
5. Step 4 — human-in-the-loop: suggestions stage as dashboard diffs (before/after preview); Apply is per-suggestion explicit via existing profile/blocks endpoints (validators + RLS unchanged, actor = user in audit); no auto-apply, no auto-publish, no background mutation. AI outage/quota disables only the assist panel.
6. Step 5 — gate enforcement NOW: zero assist tables/endpoints/jobs in current images — grep + migration audit fails the tag on any `assist/` route, `suggestion_draft`, or `kind='design-assist'` writer before the plan update. Load relevance: suggestions are dashboard-low-volume; cost (not traffic) is the scaling dimension — capped by rule 3's ledger math, plus per-day KV caps holding under burst.
7. Step 6 — tests (at plan update): guardrail matrix (CSS/JS/font-URL demands → gallery-valid or refusal); human-loop test (no public effect without Apply + publish); quota test (exhausted → 429, editing intact); injection test (`ignore previous instructions…` treated as data); IDOR test (cross-profile 403); cost test (burst held by caps, dashboard render never blocked — async progress state).

## Data / API touches

- Schema: database plan above (none now; ledger reuse later). Endpoints (GATED, absent from current OpenAPI): `POST /api/v1/assist/theme|bio|ordering`; apply path reuses existing profile/blocks endpoints. Metrics (when built): `assist.generated{kind}`, `assist.refused`, `assist.quota_exhausted`, `assist.cost_units`.

## Acceptance criteria

- Absence drill (now): E0–E3 tags boot Docker AND Podman with zero assist surface (schema + OpenAPI + source grep).
- Guardrail drill (at build): adversarial prompts return valid-or-refusal; validator-failing output never applicable.
- Human-loop drill: nothing public without Apply + publish; audit actor = user.
- Quota drill: exhausted credits block generation; editing intact.
- Injection/IDOR/cost drills per step 6.

## Out of scope

- Ledger/draft contracts (database plan above); assist panel (frontend plan); JD-tailor (P3-3); theme/block/profile logic (their backend plans); image/avatar generation, auto-publishing agents, chatbots (none in v1); P4 prompts; P5 scaling.

## Open questions

- Q1: Free with tight quotas vs pro-only?
- Q2: Shared vs separate credit pool with JD-tailor?
