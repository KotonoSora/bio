# AI Design Assist - Database Schema Design: Gated Future Contract (feature 16)

Phase 2 TL · BA sources: `docs/01-requirements-ba/16-ai-design-assist/` (BR-10, BR-12, `07-non-functional-requirements.md` NFR-01, NFR-10). GATED: nothing is created now; this file records the future storage contract so the gate is checkable today.

## Present state: zero

- Zero tables, zero columns, zero migrations, zero endpoints for assist in any image today. `profile-service`'s schema is unchanged by this feature.
- Absence is verified, not assumed: the release drill greps schema diffs, OpenAPI diffs, and source/bundle text for `assist/`, `suggestion_draft`, `kind='design-assist'`, and assist strings; any hit blocks promotion (AC-01). A credit or draft table appearing before the amendment fails the gate.

## Future contract (designed, not built)

| Item | Shape | Owner |
|---|---|---|
| Credit consumption | Plan-owned `ai_generations`-style ledger rows keyed by `user_id` + `kind='design-assist'` + period window; per-user/day caps as ledger counters | Storage + period math owned by feature 35 (plans slice); this feature defines only the kind string and shared-quota semantics |
| Exhaustion contract | Cap reached -> `429 assist_quota_exhausted`; manual editing unaffected; no partial generation bills | Defined here; counting in 35 |
| Drafts (conditional) | `suggestion_drafts` short TTL, only if multi-step UX proves necessary (Q-04): dashboard-staging state, never public-page inputs, never long-lived, never backed up | profile_db (only at amendment) |
| Applied provenance | None here: applied changes are ordinary audited profile/theme/block writes with actor = user (standard `audit_log` rows) | Existing features 06-09 |

## Forbidden forever (schema level)

- Storing prompts with PII beyond the suggestion's need (quiz answers, current bio/display-name, link titles only - never follower evidence, tokens, or other users' data).
- Logging full model I/O without minimization; a full-prompt archive table is out of bounds.
- Any assist-owned table that could become a public-page input, or any dashboard-owned money-adjacent table pattern (feature 15's no-restatement rule extended here: no assist table may carry applied content history).
- Per-request credit checks implemented in application memory instead of ledger counters - bursts must not outspend the row math.

## Drills

| Drill | When | Assertion |
|---|---|---|
| Absence | Every release (live) | Zero assist surface across schema, OpenAPI, source, bundle; tooling blocks promotion on a hit |
| Design | At amendment | Ledger reuse confirmed (no new credit table); draft TTL stated if used; PII minimization list matches this file |
| Violation | Any time | An assist table/endpoint/job before the amendment blocks promotion by tooling, not policy |

## Out of scope

- Endpoints + DTOs (`03-api-contracts-and-payloads.md`); components (`01-architecture-and-tech-stack.md`); ledger mechanics + caps (feature 35, plans); JD-tailor surface (its own major); theme/block/profile storage (06-09).
