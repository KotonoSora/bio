# AI Design Assist Gated Frontend Plan (assist panel — buildable at plan update)

PLAN ref: none — new scope beyond P0–P5; GATED with the backend. BA source: `docs/link-to-bio/appearance/ai-design-assist.md`. Server truth in `docs/link-to-bio/appearance/ai-design-assist-gated-backend-plan.md` (endpoints, guard, quota — never redefined here). Storage contract in `docs/link-to-bio/appearance/ai-design-assist-gated-database-plan.md`.

## Purpose

Hold the buildable panel design for AI suggestions so the dashboard shows AI help without ever letting unreviewed output near the public page: staged diffs, per-suggestion Apply, quota/outage states that leave manual editing intact. Nothing in this file renders before the plan update.

## Users

- Frontend developer (future): builds the `Assist` panel reusing profile/blocks forms.
- QA (now): asserts zero assist UI exists; (future) the loop/quota/outage drills.
- Creator (future): previews each suggestion as a diff, applies explicitly or discards.

## Functional requirements

1. Placement (future): `Assist` panel inside `/app/profile` and `/app/blocks` (contextual entry — theme quiz near the gallery, bio polish near the bio field, ordering tidy near the block list). No standalone AI page (suggestions act on visible editors, not in a vacuum).
2. Step 1 — theme recommender flow: quiz (content-type + vibe, 3 questions max) or auto → `POST assist/theme` → ranked picks with reasons + `Preview` per pick (applies tokens to the preview iframe only — the gallery's unsaved-preview mechanism, reused) → `Apply` calls the existing theme PUT (server-gated, audited) → refused responses render `No safe suggestion — try different answers` (never an error-styled dead end).
3. Step 2 — bio polish flow: tone selector + `Suggest` → `POST assist/bio` → 3 draft options as selectable cards with diff-highlight vs current bio + character count → `Use this draft` fills the bio field (still unsaved — the profile save flow owns persistence + purge chip) → discard clears. Low-confidence suggestions arrive flagged and render `Low confidence` with fewer options shown (honesty, not padding).
4. Step 3 — ordering tidy flow: `Suggest order` → `POST assist/ordering` → proposed position map + title edits rendered as a before/after list → per-row accept checkboxes + `Apply selected` (commits through the reorder endpoint with the standard rollback semantics — AI-proposed orderings fail exactly like human drags on conflict).
5. Step 4 — quota/outage states: `429 assist_quota_exhausted` → panel shows the upsell-safe message + `Continue manually` focus (panel collapses to a banner; editors fully functional); backend outage/degraded → panel shows `Assistant unavailable` with retry (no cached suggestions ever displayed as fresh — suggestions are never cached client-side at all).
6. Step 5 — modern UI discipline: diffs use the shared diff component (no AI-specific visual language that implies authority); AI-origin disclosed per suggestion (`Suggested by assistant` caption — dashboard-only, never visitor-facing); no auto-apply toggles exist anywhere (their absence is a requirement, asserted in review).
7. Step 6 — speed rules: suggestion calls show progress state without blocking the editors (async, cancellable — navigating away cancels the fetch); applied drafts flow through the normal save/publish pipeline with its purge chip (no AI fast-lane around publishing).

## Data / API touches

- Panel → endpoint map: quiz/bio/ordering → the three assist endpoints (all gated, absent now); Apply paths → existing theme PUT, profile PUT, blocks reorder (validators + RLS + audit unchanged). No new endpoints for Apply (the panel has zero write surface of its own).
- State: `suggestions[]` (ephemeral, never persisted client-side beyond the session); applied state = ordinary editor dirty-state afterwards.

## Acceptance criteria

- Absence drill (now): zero assist UI in the dashboard bundle (grep) and zero assist routes.
- Loop drill (at build): no suggestion reaches preview-persist without explicit Apply; public page needs the normal publish afterwards.
- Diff drill: every suggestion renders before/after; low-confidence flagged.
- Quota drill: exhausted state collapses panel to banner; manual editing intact; no cached suggestions shown.
- Outage drill: unavailable state with retry; navigation cancels cleanly.
- Authority drill: no auto-apply control exists anywhere (grep).

## Out of scope

- Suggestion generation and guard (backend plan above); ledger/drafts (database plan above); profile/blocks forms (their frontend plans); JD-tailor UI (P3-3); image generation, agents, chatbots (never v1).

## Open questions

- Q1: Panel placement — inline per-editor (current) vs one assistant drawer?
- Q2: Draft persistence across sessions — ephemeral only, or short-TTL drafts (see database plan rule 3)?
