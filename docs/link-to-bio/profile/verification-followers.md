# Verification Badge + Follower Display (trust + social proof)

PLAN ref: P1a-2 (profile identity extension). Follower-count fetching without OAuth is v1; full connected-account OAuth vault is P4-1 scope and is NOT built here. App integrations below mean display-level links/embeds, not token vaulting.

## Purpose

Give visitors two trust signals on a Link-to-Me page: a verification badge that marks authentic notable profiles, and a total follower count that aggregates audience size across connected networks. Both must be abuse-proof — badges that anyone can self-award and follower totals that anyone can inflate destroy the trust they exist to create.

## Users

- Creator: *As a Creator, I want to apply for verification and link social accounts so that my page shows a combined follower total as platform-attested trust.* Applies for verification, links social accounts for display, sees combined follower total on the page.
- Visitor: *As a Visitor, I want to see badge + total at a glance so that I trust they are platform-attested, not self-typed.* Sees badge + total at a glance and trusts they are platform-attested, not self-typed.
- Reviewer (ops): *As a Reviewer (ops), I want to approve, reject, and revoke badge applications so that impersonation loses its badge within the cache-purge SLA.* Approves/rejects/verifies badge applications, revokes on impersonation.

## Functional requirements

1. Verification badge: application flow (dashboard request + notability evidence: press, official-site backlink, or government/brand document check per ops runbook); states `none|pending|verified|rejected|revoked`; badge renders as a platform-owned SVG next to `display_name` (never a user-uploadable image or emoji, so it cannot be faked via bio text). `verified` requires manual ops approval in v1 — no auto-award rules.
2. Anti-impersonation: username-claim checks flag near-match handles of verified profiles for ops review; verified profile renames keep the badge only after re-review (rename triggers `pending` re-check, badge hidden meanwhile). Revocation is one audited action with a public-badge-off effect within the cache-purge SLA.
3. Follower total display: single `Total followers` figure + optional per-network breakdown tooltip. Sources in v1: (a) creator-entered counts (labeled `self-reported`), (b) public-count fetches where a network exposes them without auth (server-side fetch, cached 24 h, labeled `auto`). No OAuth token storage in v1 — anything needing a token waits for the P4-1 vault.
4. Count integrity: self-reported counts are capped, rate-limited on change, and versioned (sudden 10x jumps flag for review); auto counts refresh on schedule, never on visitor load (public page reads the cached total, so a network outage never slows `/:username`). Totals never include purchased-follower guarantees; display copy avoids implying audit.
5. App integrations (display level): `social`-type blocks for Instagram, TikTok, YouTube (+ generic URL) with network icon, handle, and deep link (companion doc `public-page/deep-links-mobile.md` owns open-in-browser behavior). oEmbed-style previews where a provider allows keyless fetch; otherwise a clean link card. Full connect/post-on-behalf OAuth is P4-1, never stubbed here.
6. Data-ownership note: badge + follower data export with the P2-5 profile JSON export; deleting the profile purges verification evidence per retention policy.

## Data / API touches

- Profile row extension: `profiles{verification_status, verified_at?, follower_total_cached INT DEFAULT 0, follower_breakdown_json, follower_updated_at?}` plus `verification_requests{profile_id, status, evidence_ref, reviewer?, decided_at?}` (evidence files in R2, never inline). All rows carry standard `user_id TEXT NOT NULL` + index + RLS; public reads scope `username + is_public=1` and receive only badge state + cached totals, never evidence.
- Endpoints: `POST /api/v1/profile/verify-request`, `GET /api/v1/profile/verify-status` (owner), ops `POST /api/v1/ops/verify/:id/{approve,reject,revoke}` (audited); `PUT /api/v1/profile/socials` (links + self-reported counts, validated), `POST /api/v1/profile/followers/refresh` (rate-limited; server refetch of public counts only). Public SSR reads cached totals (see `public-page/share-seo.md`); no per-hit writes.
- Scheduled refresh worker for auto counts (24 h cadence, per-network budget, failures keep last-good with stale label).

## Acceptance criteria

- Fake-badge probe: bio/display-name containing badge emoji/SVG markup renders as plain text, never as the platform badge component. *GWT: Given bio/display-name input containing badge emoji/SVG markup; When rendered; Then it appears as plain text, never as the platform badge component.*
- State drill: `pending` shows no badge; `verified` shows badge within purge SLA; `revoked` removes it within purge SLA; rename of a verified profile hides badge until re-approved. *GWT: Given verification states `pending`, `verified`, `revoked`, and a verified-profile rename; When the page renders; Then `pending` shows no badge, `verified` shows it within purge SLA, `revoked` removes it within purge SLA, and a rename hides the badge until re-approved.*
- Follower drill: self-reported edit applies with version history; auto-refresh updates totals without slowing public page (p95 unchanged); network outage keeps last-good total with stale indicator. *GWT: Given a self-reported edit, a scheduled auto-refresh, and a network outage; When applied; Then the edit versions history, the refresh updates totals with p95 unchanged, and the outage keeps the last-good total with a stale indicator.*
- IDOR probe: user A verify/follower writes against user B `profile_id` return 403; RLS denies at DB. *GWT: Given user A verify/follower writes against user B `profile_id`; When evaluated; Then the API returns 403 and RLS denies at DB.*
- XSS probe: handle/URL/count fields reject `javascript:` and markup; CSP holds; anchors carry `rel="noopener ugc"`. *GWT: Given handle/URL/count input with `javascript:` or markup; When saved and rendered; Then it is rejected, CSP holds, and anchors carry `rel="noopener ugc"`.*
- Ops audit: every approve/reject/revoke row appears in `audit_log` with actor + reason. *GWT: Given an approve, reject, or revoke action; When recorded; Then a row appears in `audit_log` with actor + reason.*

## Out of scope

- Block authoring and scheduling (see `blocks/link-blocks.md`); themes/avatar (see `appearance/themes-avatar.md`); public SSR/cache mechanics (see `public-page/share-seo.md`); per-click analytics (see `analytics/profile-analytics.md`).
- OAuth token vault, posting, and automations (P4-1–P4-3, never stubbed here); shop products/courses/tips, leads/CSV, custom domains (separate majors); AI design help (see `appearance/ai-design-assist.md`).

## Open questions

- Q1: Auto-verification signals in v2 (e.g. mutual official-site backlink = instant verify) — worth speccing, or keep manual review permanently?
- Q2: Follower-total freshness SLA — is 24 h cache acceptable at launch, or do top profiles need faster refresh?
