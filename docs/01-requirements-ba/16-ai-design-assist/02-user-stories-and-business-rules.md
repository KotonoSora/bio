# AI Design Assist - User Stories and Business Rules (feature 16) - GATED

Phase 1 BA · GATED: rules describe the design to build at the spec amendment; `BR-01` (gate) is live now. Rules `BR-xx` are local to this feature.

## User stories (future build)

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Creator | ranked theme picks with reasons from a short quiz or auto mode | I can find a fitting look without browsing everything | Should |
| US-02 | Creator | bio/display-name drafts with a tone selector and length cap | I can polish wording quickly | Should |
| US-03 | Creator | link-order and title tidy proposals from my click history | my best links get seen first | Could |
| US-04 | Creator | each suggestion as a before/after diff with per-suggestion Apply or discard | I stay in control of every change | Must |
| US-05 | Visitor | only human-approved output | no AI-experimental markup ever reaches the public page | Must |
| US-06 | Platform cost controller | every generation capped by a shared budget | assist shares the AI credits without unbounded spend | Must |
| US-07 | Creator | honest confidence and my locale (VN/EN) | weak suggestions never masquerade as strong ones | Should |
| US-08 | Creator | quota/outage states that leave manual editing untouched | my workflow never depends on the assistant | Must |

## Business rules

- **BR-01 Gate (live now).** No assist table, route, job, bundle chunk, or stub may exist in any release before a spec amendment approves this feature. Absence is verified by tooling in every release (AC-01): schema diff, OpenAPI diff, source and bundle grep. Violations block promotion.
- **BR-02 Tokens-only enforcement.** Every suggestion must be expressible as a legal input of an existing endpoint: a `theme_id` from the gallery, plain-text bio/title/display-name through the same XSS and length validators, reorder operations through the blocks API. Anything inexpressible is rejected before display (`refused:true`), never rendered as applicable.
- **BR-03 Closed suggestion set v1.** Theme recommend, bio polish, ordering tidy - nothing else. No freeform page generation, no image generation, no custom fonts/CSS/JS/HTML, ever.
- **BR-04 Human-in-the-loop.** Suggestions stage as dashboard diffs; Apply is per-suggestion and explicit through existing endpoints (validators + RLS unchanged); publish follows the normal publish + cache-purge path. No auto-apply, no auto-publish, no background mutation of live pages; audit shows actor = user on every applied change.
- **BR-05 Budget.** Every generation draws from the plan-owned AI credit model (`kind='design-assist'`) with per-user/day caps; exhausted quota -> `429 assist_quota_exhausted` with an upsell-safe message; manual editing is never affected. Numeric caps are set with feature 35's plan catalog (finance value - never invented here).
- **BR-06 Abuse defense.** Suggestion endpoints are challenge-gated (Turnstile-class) and rate-limited; prompt cost is the bot model, so throttling is cost defense. User content is treated as data; system instructions are never overridable.
- **BR-07 Privacy minimization.** Model input carries only what the suggestion needs: quiz answers, current bio/display-name, link titles - never follower evidence, tokens, reserves, or other users' data. Logged prompts/outputs are PII-minimized.
- **BR-08 Honesty.** AI origin disclosed in-dashboard (`Suggested by assistant`, never visitor-facing); low-confidence suggestions are flagged and offer fewer options rather than padding; output language matches the creator locale (VN default, EN fallback).
- **BR-09 Isolation on failure.** AI outage or quota exhaustion disables only the assist panel; manual theme/block/profile editing works unchanged; suggestions are never cached client-side (nothing stale is ever shown as fresh).
- **BR-10 Draft posture.** Staged suggestions are ephemeral dashboard state, never public-page inputs; a short-TTL drafts table exists only if multi-step UX proves necessary (Q-04), and never stores applied history - applied changes are ordinary audited writes.
- **BR-11 Owner scoping.** Assist calls are scoped to the session user; a cross-profile target -> `403 foreign_profile` (inherited code) and RLS denies at apply time; no assist read path reaches another profile's data.
- **BR-12 Entitlement forward contract.** Credit consumption keys off the plans-owned period window (feature 35); mid-period plan changes never retroactively alter consumed credits; free-vs-pro availability is decided at the spec amendment (Q-01).
