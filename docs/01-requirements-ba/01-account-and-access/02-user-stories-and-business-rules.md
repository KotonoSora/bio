# Account and Access - User Stories and Business Rules (feature 01)

Phase 1 BA · Stories and rules enforced by `docs/02-architecture-techlead/01-account-and-access/` (architecture, schema, contracts, plan, security files). Scope: `docs/01-requirements-ba/01-account-and-access/01-document-context-and-scope.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Creator/visitor | sign up or log in with one provider button | access is one click and passwordless | High |
| US-02 | Returning user with changed IdP email | still reach the same account via stable provider `sub` | a mutable email never locks me out or forks my account | High |
| US-03 | Creator | link extra providers and unlink safely | I keep access even if one provider fails | High |
| US-04 | Creator | review and revoke sessions, including all others | a lost or shared device stops being a way in | High |
| US-05 | Creator | re-authenticate fresh only when an action is sensitive | routine work stays frictionless, damage stays gated | High |
| US-06 | Locked-out Creator | recover via a support-issued one-time re-link | I regain access without anyone setting a secret | High |
| US-07 | Creator | have my plan honored in every feature within seconds | upgrades and downgrades feel immediate and honest | High |
| US-08 | Collaborator | act inside exactly my org role | I gain only what was explicitly granted | Medium |
| US-09 | Auditor | reconstruct link/unlink/recovery/role/plan events | grants are provable from the audit log alone | High |
| US-10 | Support/ops | rotate provider secrets without deploys and never see secrets | recovery and rotation stay safe and audited | Medium |
| US-11 | Attacker (adversarial) | - (blocked on every vector) | the front door has no usable target | High |

## Business rules

- **BR-01 SSO-only (hard).** Login/signup exist only through the closed provider set (Google, Apple, Facebook, LinkedIn + generic OIDC adapter as config). No email/password field, endpoint, or fallback exists anywhere; probes return `404 auth_disabled`; any legacy verifier rows are ignored and flagged for purge.
- **BR-02 Stable identity.** Accounts resolve by `(provider_id, provider_sub)` only; email never merges accounts. A collision surfaces a notice; it never merges, blocks, or forks.
- **BR-03 Email rules.** IdP-verified email syncs to the account; unverified/absent (Facebook deny, Apple relay) still creates the account. Contact email is user-set and code-verified (6 digits, 15 min TTL, 5 attempts); a contact email already verified on another account is refused (`409 email_conflict`). Deliverable-email signal feeds the checkout gate (features 32-40).
- **BR-04 Callback security.** `state` + `nonce` + PKCE are single-use, 10-minute TTL, validated server-side; `callbackURL` is same-origin allowlist only; every failure returns the generic `auth_failed` with no existence oracle.
- **BR-05 Linking.** Linking requires a fresh step-up; unlinking requires fresh step-up and leaves at least one provider linked (`409 last_link_forbidden` otherwise). Both logins resolve to one account.
- **BR-06 Sessions.** Server-side registry plus edge validation; idle 7 days sliding, absolute 90 days; list shows coarse device/ip-class/last-active; per-session revoke and revoke-all-others; logout revokes the session and clears the cookie on all tabs.
- **BR-07 Step-up.** Billing change, username change, provider unlink, account deletion, Act-as-user start, and billing-contact transfer require re-auth `≤ 10 min` old; otherwise `403 step_up_required` with the re-auth path — never a silent weaker check.
- **BR-08 Deny-by-default.** `user_id` is server-set only; app scoping and RLS both enforce; forbidden mutations on known-owned targets `403`; private reads `404`; no v1 admin bypass.
- **BR-09 Entitlements.** Plan gates read only the entitlement snapshot (staleness `≤ 30 s`, fail-closed to free on any miss); client flags and stale plan strings are never trusted. Features 06, 09, 13-16 consume this contract.
- **BR-10 Org roles.** Matrix `owner > manager > member`, assigned per profile, effective `≤ 60 s`; unassigned profiles invisible; defined here, mechanics land with features 45-47.
- **BR-11 Audit.** Append-only rows for link/unlink/recovery/role/plan/username/domain/impersonation events; an action outside the audit surface did not happen.
- **BR-12 Recovery.**  Locked-out users get a short-lived (`15 min`), single-use, signed re-link invitation after documented identity evidence; support never sets or views credentials; every invitation is audited.
- **BR-13 Abuse.** Auth responses are uniform (wording and timing class); per-IP and per-provider exponential backoff; challenge-on-signal (Turnstile class); replayed code/state fails closed.
- **BR-14 First-login handoff.** An authenticated account without a profile is routed to the choose-username screen; namespace rules (reserved slugs, 3-30 `^[a-z0-9_]+$`, race-safe create) are owned by feature 06 and only consumed here.
- **BR-15 Prefill safety.** IdP-supplied name/avatar are stripped (no markup/scripts) and avatar prefill applies feature 09's rules (server-side re-fetch, 2 MB, magic-byte check, random storage key); IdP URLs are never hotlinked.
- **BR-16 Act-as-user (consumed).** Agency operators act only via scoped, time-boxed, step-up-gated sessions stamped with `actor_id` + `subject_user_id`; mechanics land with features 45-47.

## Constraints carried

- Session registry is durable truth; edge validation state is the cache — losing the cache never fabricates a grant and never needs a schema change to recover.
- One throttle code family (`throttled`, `429` + `Retry-After`); probing emits stable codes and is counted, not punished.
- No money values exist in this feature; fee/pricing claims are features 32-40's and are never restated here.
