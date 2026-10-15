# AI Design Assist Gated Database Plan (future credit ledger — nothing stored now)

PLAN ref: none — new scope beyond P0–P5 (PLAN's only AI use is P3-3 JD-tailor); needs a plan update before build. GATED. BA source: `docs/link-to-bio/appearance/ai-design-assist.md`. Server design in the AI backend plan; panel design in the AI frontend plan (both gated, split next to this file). No migration — nothing is created until the plan update.

## Purpose

Record the future storage contract so the gate is checkable today: which ledger the assist will draw from, what ephemeral state it may hold, what it must never persist — and the proof, right now, that none of it exists. A credit or draft table appearing before the plan update fails the gate.

## Users

- Backend developer (future, post-plan-update): reuses the ledger below, adds at most ephemeral drafts.
- QA (now): runs the absence drill every release.
- Cost reviewer: confirms the shared-budget design caps spend before it ships.

## Functional requirements

1. Present state: zero tables, zero columns, zero endpoints for design-assist. Absence is verified, not assumed (Acceptance drill).
2. Future ledger (designed, not built): P3-3 `ai_generations{user_id, kind, credits, created_at}` with `kind='design-assist'` — storage owned by the JD-tailor slice (monetize major); this slice defines only the kind string and the shared-quota semantics (per-user/day cap, exhausted → `429 assist_quota_exhausted`, manual editing unaffected). One wallet vs per-surface budgets stays an open question until the plan update (see Q2) — the schema supports either (kind-keyed rows), so no DDL hinges on the answer.
3. Future ephemeral state (designed, not built): `suggestion_drafts` with short TTL for multi-step UX at most — dashboard-staging state only, never public-page inputs, never long-lived, never backed up. If the UX ships single-step, even this table never exists.
4. Forbidden forever: storing prompts with PII beyond the suggestion's need (quiz answers, current bio, link titles only — never follower evidence, tokens, other users' data); logging full model I/O without PII minimization; persisting applied-suggestion provenance anywhere except the standard `audit_log` actor rows (applied changes are ordinary profile/block writes with actor = user).
5. Cost containment at the schema level: per-user/day caps are ledger counters, not application memory — bursts cannot outspend the row math; generation latency never blocks dashboard render (async with progress state, specified in the backend plan).

## Data / API touches

- DDL now: none — assertion: `grep -rn "design-assist\|suggestion_draft\|assist/theme\|assist/bio\|assist/ordering" src/ db/ 2>/dev/null` over E0–E3 tags returns nothing (gate in the release checklist). Future: ledger kind string + optional TTL drafts per rules 2–3.

## Acceptance criteria

- Absence drill (every release until plan update): schema + OpenAPI + source diffs show zero assist surface.
- Design drill (at plan update): ledger reuse confirmed (no new credit table); draft TTL stated; PII minimization list matches rule 4.
- Violation drill: any assist table/endpoint/job before the plan update blocks promotion by tooling, not just policy.

## Out of scope

- Suggestion endpoints and guard (backend plan); assist panel (frontend plan); JD-tailor ledger mechanics (P3-3, owning slice); theme/block/profile storage (their database plans).

## Open questions

- Q1: Free with tight quotas vs pro-only (see BA Q1 — decide at plan update)?
- Q2: One shared wallet with JD-tailor vs per-surface budgets?
