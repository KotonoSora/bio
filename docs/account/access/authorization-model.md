# Authorization Model (ownership, roles, gates)

PLAN ref: P1a-1 (Better Auth + RLS + least-privilege app role) + P1a-2 (profiles/username) + P3-3 (plan gates) + P3-4 (org skeleton) + P4-5 (agency roles, Act-as-user, audit) + S-1/S-6. Authentication consumed from `account/access/sso-login.md` (SSO-only, no passwords) — this doc owns what an authenticated session may do.

## Purpose

Decide every "can X do Y" question in one place so individual feature specs never invent their own access rules. The model is deny-by-default: a session starts with nothing, gains only what ownership, plan entitlement, or an explicit org role grants, and loses it the moment the grant lapses. Enforcement happens twice — app-level `user_id` scoping plus Postgres RLS — so a missed check in one layer is still blocked by the other.

## Users

- Creator: owns profiles, blocks, documents, products, leads, domains; grants org roles to collaborators.
- Collaborator (manager/member): acts within an org role, never beyond it.
- Agency operator (P4): acts inside a scoped Act-as-user session with full audit, never via backdoor edits.
- Attacker (adversarial): replays another user's IDs, guesses private slugs, escalates role, rides a lapsed grant — and gets 403s.
- Auditor: reconstructs who did what from `audit_log` alone.

## Functional requirements

1. Authentication prerequisite: every non-public API call requires a valid SSO cookie session (see `sso-login.md`). No session → 401, never a degraded-owner path. `user_id` is taken from the server session only — client-supplied `user_id` is ignored or rejected.
2. Ownership rule (universal): every business row carries `user_id TEXT NOT NULL` + index + RLS per repo rule. Owner has full CRUD on owned rows (subject to plan gates and deletion-grace state). Dashboard queries scope by `user_id`; public reads scope by `username + is_public=1` and never accept a raw `user_id`/`profile_id` from anonymous callers.
3. Double enforcement: app middleware resolves `user_id` → scopes all queries; RLS policies on every business table re-check `current_user_id = user_id` (or membership for org rows) as the app role. Either layer denies independently. Direct-PG-as-app-role access without a session context sees zero foreign rows.
4. Public vs private: `is_public=0` rows are invisible to anonymous and non-owner sessions (404 on public SSR, 403-or-404 on API per §8 — decided once here: API returns 404 for private profile content to avoid existence oracle, 403 for forbidden mutations on known-owned IDs). `sitemap.xml`/`robots` list only `is_public=1`. Private assets (R2 keys, signed URLs) require an owner-scoped grant, never a guessable URL.
5. Plan gates (P3-3): feature checks (unlimited profiles, custom domain, realtime analytics, AI credits, lower fees) read the entitlement ledger (`monetize/billing/entitlement-ledger.md`), never a client flag or a stale `users.plan` string. Lapsed pro loses the capability within the entitlement window; in-flight work degrades gracefully (serving stops, data retained — see lifecycle/domain specs).
6. Org/team roles (P3-4 skeleton → P4 roster): roles `owner > manager > member` with a fixed capability matrix owned here (features reference it, never redefine it): owner (manage roles, billing-contact, delete org, all content), manager (invite member, edit/publish assigned profiles, view reports), member (edit assigned profiles only). Assignment is per-profile (`org_members{org_id, user_id, role, profile_ids[]}`); unassigned profiles are invisible to that member. Role changes and removals take effect on next request (≤60s cache TTL on membership, never longer).
7. Act-as-user (P4-5, consumed): agency operators enter only via `docs/agency/access/act-as-user.md` mechanics — scoped to assigned profiles, time-boxed, step-up gated, every action stamped with both `actor_id` and `subject_user_id` in `audit_log`. No global admin bypass exists in v1.
8. Sensitive-action step-up: billing changes, username change, provider unlink, account deletion, Act-as-user start, billing-contact transfer require fresh SSO re-auth ≤10 min (primitive from `password-security.md` with SSO substituted). Without it: 403 with the step-up path, never a silent downgrade to a weaker check.
9. Audit: `audit_log{actor_id, subject_user_id?, action, target, at, ip_class}` append-only for role/grant/plan/username/domain/impersonation events. No authorized action outside the audit surface; support-assisted recovery and role grants are audit rows or they did not happen.

## Data / API touches

- Enforcement point: Hono middleware in `worker/index.ts` (`requireSession` → `loadGrants(user_id)` → `requireCap(...)`) before any `/api/v1/*` or `/api/billing/*` handler. Grant load = ownership + active entitlement + org memberships (short-TTL cached, versioned on role/plan change). Beacon `POST /api/t/:username` stays anonymous via queue (never a session oracle) with KV-dedupe.
- Tables (all PG via `worker/db/*`, RLS on): `users`, `profiles{user_id, username UNIQUE lowercase, is_public}`, `profile_blocks`, `documents`, `products`, `orders`, `leads`, `custom_domains`, `orgs/org_members` (P3-4 skeleton), `entitlements` ledger, `audit_log` (append-only, no UPDATE/DELETE for app role). RLS pattern per table: owner full-access predicate + org-delegated predicate where applicable + deny-all default.
- Error contract: 401 unauthenticated, 403 forbidden mutation / 404 private-content-not-found (per §4), `step_up_required` for stale grants, uniform shapes (no per-case messages leaking ownership). Rate limits + Turnstile on anonymous auth/checkout/beacon per P0/S-5; secrets via `wrangler secret` (`.gitignore` guards `.wrangler/`, `.dev.vars*`).

## Acceptance criteria

- IDOR drill (S-1): `userA` session reads/writes/deletes `userB` profile/block/document/product/order/lead/domain → 403 (or 404 for private reads) everywhere; no endpoint trusts client `user_id`.
- RLS drill (S-6): direct PG connection as app role with `userA` context returns zero `userB` rows across all business tables; `audit_log` shows grant/role/domain/impersonation events with actor + subject.
- Gate drill: expired-pro session reproducing a pro-only call (custom domain bind, realtime window, extra profile, AI generation over cap) refused with the upgrade path; re-upgrade restores within the window without data loss.
- Role drill: member sees only assigned profiles; manager invite/remove flows respect the matrix; revoked member's next request denied (≤60s); owner-only actions as member → 403.
- Step-up drill: each sensitive action without fresh SSO re-auth refused; grant older than 10 min refused; Act-as-user outside assignment/time-box refused and logged as denied attempt.
- Baseline: XSS strip + CSP holds on username/display-name surfaces (S-2); Turnstile + WAF throttle anonymous bursts (S-5); avatar prefill 2MB + magic-byte + R2 random key (S-4); no secrets in repo (S-7 migration/build/preview green).

## Out of scope

- Login/SSO flows, linking, sessions lifecycle (see `account/access/sso-login.md`); password mechanics (dead — see `access/password-security.md` as superseded).
- Plan definitions/pricing, checkout/webhook/ledger mechanics (`monetize/`); deletion purge flow (`lifecycle/account-deletion.md` — consumes step-up from here); custom-domain verification (`domains/custom-domains.md`).
- Publishing OAuth vault and fan-out, IG automations, broadcasts (P4 `docs/publish/`, `docs/automate/`, `monetize/marketing/` — operated under this model, never redefined there); agency roster/reporting/billing detail (`docs/agency/` — consumes the role matrix).

## Open questions

- Q1: API 403-vs-404 split (403 mutations / 404 private reads proposed) — confirm, or uniform 404 to minimize oracle further?
- Q2: Membership cache TTL (60s proposed) vs per-request load at 100k-concurrent mix — performance sign-off?
- Q3: Break-glass admin (none in v1 proposed) — accept support-recovery-only posture, or spec a two-person audited break-glass for P5?
