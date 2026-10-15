# AI Design Assistance (token-safe suggestions, human approves)

PLAN ref: none — new scope beyond PLAN P0–P5, flagged explicitly. PLAN's only AI use is P3-3 (JD-tailor résumé help); this doc proposes a second AI surface and needs a plan update before build. Do NOT build in P1–P2; design-complete only.

## Purpose

Give creators link.me-style AI design help — theme picks, bio polish, link ordering — without breaking the tokens-only guardrail. The assistant may suggest and draft, but it may never emit custom CSS/JS/HTML, and nothing it proposes goes public without explicit human approval.

## Users

- Creator: asks for design help, previews suggestions, accepts or discards each one.
- Visitor: sees only human-approved output; never AI-experimental markup.
- Platform (cost control): every generation draws from a capped budget shared with P3-3 AI credits.

## Functional requirements

1. Suggestion surfaces (closed set, v1): (a) theme recommender (quiz or auto: content-type + vibe → ranked `src/themes/` picks with reasons); (b) bio/display-name polish (3 draft options, tone selector, length cap); (c) link-order/title tidy (proposed `position` + title edits from click history where available, labels-only where not). No freeform page generation, no image generation, no custom-font/CSS output in v1.
2. Tokens-only enforcement: AI output is constrained to valid inputs of existing endpoints (a `theme_id` from the gallery, plain-text bio/title strings through the same XSS/length validators, reorder ops through the blocks API). A suggestion that cannot be expressed as legal API input is rejected before display.
3. Human-in-the-loop: suggestions stage in dashboard as diffs (before/after preview); Apply is per-suggestion explicit; publish still follows the normal publish + cache-purge path. No auto-apply, no auto-publish, no background mutation of live pages.
4. Budgeting and abuse: generations draw from the P3-3 `ai_generations{credits}` model (shared quota, KV-capped rate per user/day); prompts and outputs logged for safety review with PII minimization; Turnstile + rate limits on the suggestion endpoints as anonymous-adjacent abuse paths.
5. Quality and honesty: suggestions disclose AI origin in dashboard (never visitor-facing labels required); low-confidence suggestions say so and offer fewer options rather than inventing; Vietnamese + English supported, matching creator locale.
6. Degradation: AI outage or quota exhaustion disables only the assist panel — all manual editing (blocks, themes, profile) works unchanged.

## Data / API touches

- Reuses `ai_generations{user_id, kind, credits, created_at}` from P3-3 with new `kind='design-assist'`; no new business tables. Suggestion drafts are ephemeral dashboard state (persisted at most as unapplied `suggestion_drafts` with short TTL if needed for multi-step UX), never public-page inputs.
- Endpoints: `POST /api/v1/assist/theme`, `POST /api/v1/assist/bio`, `POST /api/v1/assist/ordering` (all authenticated, quota-checked, prompt-injection-hardened: user content treated as data, system instructions never overridable); apply path reuses existing `PUT /api/v1/profile*` and blocks endpoints (validators + RLS unchanged). All AI calls run server-side via the `AI` binding; keys via `wrangler secret`, never in repo.
- Privacy: profile content sent to the model is limited to what the suggestion needs (theme quiz answers, current bio, link titles — never follower evidence docs, tokens, or other users' data).

## Acceptance criteria

- Guardrail drill: adversarial prompt demanding custom CSS/JS/font-URL returns only gallery-valid suggestions or a refusal; any output failing API validators never renders as applicable.
- Human-loop drill: no suggestion reaches the public page without an explicit Apply + publish; audit shows actor = user on every applied change.
- Quota drill: exhausted credits block generation with a clear upsell-safe message while manual editing stays fully functional.
- Injection drill: bio text containing instruction-override attempts (`ignore previous instructions…`) is treated as data — system behavior unchanged, output stays within the closed suggestion set.
- IDOR probe: user A assist calls scoped to user B `profile_id` return 403; RLS denies at DB on apply.
- Cost drill: per-user/day KV caps hold under burst; generation latency never blocks dashboard render (async with progress state).

## Out of scope

- JD-tailor résumé AI (P3-3, separate surface and prompts); theme-gallery authoring and avatar pipeline (see `appearance/themes-avatar.md`); block CRUD and scheduling (see `blocks/link-blocks.md`); public SSR/cache (see `public-page/share-seo.md`).
- AI image/avatar generation, auto-publishing agents, chatbot support (none in v1); P4 publishing/automation prompts; P5 scaling.

## Open questions

- Q1: Plan gating — assist on free with tight quotas, or pro-only like realtime analytics?
- Q2: Shared vs separate AI credit pool with JD-tailor — one wallet, or per-surface budgets?
