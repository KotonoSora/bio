# Verification Followers Frontend Plan (trust screens + integration — UI build steps)

PLAN ref: P1a-2 trust display UX. BA source: `docs/link-to-bio/profile/verification-followers.md`. Server truth in `docs/link-to-bio/profile/verification-followers-backend-plan.md` (state machine, codes — never redefined here).

## Purpose

Give the frontend developer an ordered UI procedure for trust: the verification card flow, the socials editor flow, the ops review route, and badge preview rules. Follow steps 1–6 in order. The server owns state transitions; the client renders states and collects inputs.

## Users

- Frontend developer: builds `/app/trust` and the ops review route.
- QA: executes the state-click and injection drills.
- Creator: applies for verification, links socials, watches status. Ops reviewer: decides with reasons.

## Functional requirements

1. Route and state: `/app/trust` (owner-only) holds two cards — verification status card + socials editor card. Client state: `trust {status, decidedAt, total, breakdown, stale}` from `verify-status` + socials GET on mount. Ops review lives on a separate ops-only route (role-gated link, invisible to creators).
2. Step 1 — verification card flow: `none` → `Request verification` button opens evidence step (notability evidence upload: file input → direct-to-blob upload w/ progress → returns key; form sends `POST verify-request` with key) → `201` flips card to `pending` timeline (submitted → under review, no badge) → polling `verify-status` every 60s while pending (stops on terminal state) → `verified` shows badge preview (platform SVG component, same as SSR) + `verified_at` → `rejected` shows reason + re-apply entry → `revoked` shows revocation notice. `409 verify_already_pending` collapses to the pending view (no duplicate forms).
3. Step 2 — rename interplay (UI side): if the profile rename wizard (profile frontend plan) succeeds while `verified`, this card flips to `pending` with copy `Re-review required after rename — badge hidden meanwhile`. The frontend learns this from the next `verify-status` poll, not from the wizard (no cross-tab coupling).
4. Step 3 — socials editor flow: per-network rows (icon + handle input + URL input + self-reported count input with cap hint `max 100,000,000`) → add/remove rows → save `PUT socials` → `200` updates total + breakdown tooltip data + `stale` chip when present → `422` maps per-row (`bad_handle` under handle, `bad_social_url` under URL, `follower_count_capped` with the cap restated) → `follower_jump_review` shows an amber `under review` notice on the row (count still applied — copy says so). Auto-refreshed totals arrive with `updated_at`; outage shows `last updated … (stale)` instead of an error.
5. Step 4 — badge preview rule: the ONLY badge visual in the dashboard is the shared `VerifiedBadge` SVG component bound to `status==verified`. Bio/display-name fields get no badge picker, no emoji shortcuts — a pasted badge emoji renders as text (asserted in the fake-badge test, mirrored in preview).
6. Step 5 — ops review route flow (role-gated): queue table (profile, status, evidence link opening the blob object in a sandboxed viewer, history) → row opens decide panel (approve/reject/revoke + mandatory reason textbox, revoke requires typing confirmation) → decide call → `200` updates row + appends to visible audit excerpt. Non-ops hitting the route get 403 → re-login upsell to ops (no queue data leaks in the 403 body).
7. Step 6 — edge cases: evidence upload failure (retry, key never half-sent); `refresh` button (rate-limited; `429` shows cooldown); expired session mid-decide (reason text preserved in session storage, re-login resumes).

## Data / API touches

- Screen → endpoint map: card → `GET verify-status` (poll while pending); apply → `POST verify-request`; socials → `PUT socials` + `POST followers/refresh`; ops → `POST /api/v1/ops/verify/:id/{approve,reject,revoke}`. Session cookie + CSRF throughout.
- Type mirror: `Trust {status: 5-union, decidedAt?, total, breakdown, stale, updatedAt?}`. Code → UI map: `verify_already_pending` → pending view; `bad_social_url|bad_handle|follower_count_capped` → row fields; `403` → re-login (ops route: role notice).

## Acceptance criteria

- State drill: click through none→pending→(ops approve)→verified in staging; badge preview appears only at verified; rename flips card to pending.
- Editor drill: invalid handle/URL/capped count each map to their row field; 10x jump shows review notice with count applied; stale outage shows timestamp, not error.
- Fake-badge drill: emoji pasted in bio preview renders as text next to (not as) the badge component.
- Ops drill: decide without reason blocked client-side AND server-side; revoke requires typed confirmation; audit excerpt updates.
- Security drill: no evidence bytes in client state (keys only); ops route invisible without role.

## Out of scope

- State machine and totals logic (backend plan above); table DDL (database plan above); profile/block/theme screens; SSR badge markup (share-seo plans, same SVG component).

## Open questions

- Q1: Evidence upload — single file or multi-file dossier in v1?
- Q2: Ops queue filters (by status/age) — needed at launch volume?
