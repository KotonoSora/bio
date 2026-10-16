# Verification Badge and Follower Display - User Stories and Business Rules (feature 07)

Phase 1 BA · Stories seed `06-acceptance-criteria.md`; rules `BR-xx` are quoted by every later file.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Creator | apply for verification with evidence | my page carries a platform-attested badge | Must |
| US-02 | Creator | see my application status and the decision | I know where I stand and can re-apply | Must |
| US-03 | Ops reviewer | approve, reject, or revoke with a reason | impersonation loses its badge and every action is accountable | Must |
| US-04 | Creator | see my badge hidden after a rename until re-review | renamed accounts cannot launder a badge | Must |
| US-05 | Creator | edit social links and self-reported counts | my combined total reflects my audience | Must |
| US-06 | Creator | refresh auto counts on demand (rate-limited) | totals stay current without waiting a day | Should |
| US-07 | Visitor | see badge + total with honest provenance and staleness | I trust the signal is attested, not self-typed | Must |
| US-08 | Ops reviewer | see near-match claims of verified names flagged | impersonation attempts surface before they grow | Should |

## Business rules

- **BR-01 Badge ownership.** The badge is a platform-owned SVG rendered only from `verification_status=verified`; badge-like emoji or markup inside `bio`/`display_name` renders as plain text. No user-uploadable badge asset exists.
- **BR-02 States.** Exactly five: `none`, `pending`, `verified`, `rejected`, `revoked`. `verified` requires a manual ops decision - no auto-award rules in v1. A profile holds at most one open (`pending`) request.
- **BR-03 Evidence.** Evidence lives in blob storage under random keys (`verification/{ulid}.ext`), referenced by `evidence_ref` only - never inline in PG, never served to creators after submission, never rendered inline (sandboxed viewer only). Single file per application, <= 10 MB, PDF/PNG/JPEG.
- **BR-04 Rename interplay.** A rename of a verified profile sets status back to `pending` atomically with the rename; the badge hides until re-approved.
- **BR-05 Revocation.** Any -> `revoked` is one audited ops action with a reason; the public badge disappears within the cache-purge SLA (<= 2 min worst case).
- **BR-06 Self-reported counts.** Capped at 100,000,000 per network, rate-limited on change, and versioned (history rows). A 10x jump vs the previous value raises `follower_jump_review` (count still stored; review decides). Display copy never implies audit.
- **BR-07 Auto counts.** Server-side keyless fetches only (no tokens anywhere); cached 24 h; refreshed by a scheduled job and on-demand at most 6x/h; never fetched during visitor load. On failure the last-good value stays with a `stale` flag.
- **BR-08 Public exposure.** Public reads receive badge state + cached totals only; evidence references, reviewer identities, and rejection reasons are never public.
- **BR-09 Socials validation.** Links must be `https:`; handles must match the per-network pattern (Instagram/TikTok/YouTube/generic); violations map to row-level codes.
- **BR-10 Scoping.** Owners act only on their own profile (session `user_id` + RLS); ops endpoints require `ROLE_OPS` re-verified per request.
- **BR-11 Audit.** Every approve/reject/revoke writes an append-only row with actor + reason, retained 24 months.
- **BR-12 No OAuth vault (v1).** No token storage, no posting, no automations; the build gate greps for vault-shaped tables or endpoints.
