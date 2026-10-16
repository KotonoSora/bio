# Custom Domains - Context and Scope (feature 04)

Phase 1 BA · Feature 04 of 53 (account) · Implemented by `docs/02-architecture-techlead/04-custom-domains/` (5 files); extends `profile-service` (the username/namespace authority). Consumers: gateway + public-site-service (host routing), abuse tooling, support runbook; entitlement via feature 01 snapshot; lapse signal from feature 36.

## Purpose

Let pro creators serve their page from their own hostname with zero wrong-site risk: cryptographic verification before any binding, the same SSR and cache posture as `/:username` once bound, and instant detachment (serving stops, DNS untouched) on downgrade, unlink, or abuse. An unverified hostname must never render another user's page - that invariant is the whole feature.

## Objectives

- O-1 Verify before bind: a random single-use TXT challenge proven by DNS control; unverified rows never serve, under any request.
- O-2 Serve as first-class: verified hosts render the bound profile with the same tags/SEO as `/:username`, canonical rewritten to the custom host.
- O-3 One host, one profile: unique binding, takeover only through full re-verification; disputes resolve to DNS control and are audited.
- O-4 Lapse honesty: entitlement lapse stops custom-host serving (DNS untouched) with a 30-day restore hold; explicit removal deletes immediately.
- O-5 No wrong-site surface under abuse: neutral error for unknown/unverified hosts, denylist at add and recheck, phishing suspension that never equals account suspension.

## Personas and RBAC

| Role | CRUD Permissions | Description |
|---|---|---|
| Creator (pro) | Full CRUD on own hostnames (add, verify, recheck, remove); read status | Adds a hostname, sets the TXT record, waits, goes live, removes freely. |
| Visitor | Read (anonymous): bound profile on verified host, neutral error otherwise | Opens `creator.com` and gets the bound profile with correct SEO tags, or a clean error. |
| Abuser (adversarial) | none | Points their domain at the platform, claims another profile, rides an ex-owner's DNS - and gets nothing. |
| Support/ops | Create a reclaim round after dispute evidence; read status; audited | Resolves DNS-control disputes without ever serving as anyone. |
| Abuse reviewer | Update: suspend serving pending review | Phishing on a bound host stops serving; the profile stays live on `/:username`. |

## Resolved open questions

| Q | Decision |
|---|---|
| Q-01 Hostname policy | Apex and subdomains allowed; wildcards never (each hostname verified individually). IDN stored as punycode, displayed in original form. Denylist check at add AND every recheck: platform-owned hosts, identity-provider domains, maintained lookalike/brand list. No launch allowlist - denylist-driven. |
| Q-02 Removal / re-upgrade hold | Explicit removal: binding deleted immediately, serving halts at once, re-bind is a full re-verify (no hold). Entitlement lapse: binding held `suspended` for 30 days - re-upgrade inside the hold with a passing recheck restores serving without re-verification; beyond the hold, a fresh challenge is required. `unverified` rows with failing control for 14 days auto-release (no squatting). |
| Q-03 TLS | Platform-managed issuance on verification (creator uploads no certificates in v1); issuance failure means no serving (fail-safe), never plaintext. |
| Q-04 Serving stop bound | Entitlement lapse stops custom-host serving within `<= 60 s` end-to-end (30 s snapshot bound + 30 s resolve cache); `/:username` stays live throughout. |

## In scope

- Binding lifecycle: add (normalize + denylist + plan gate), TXT challenge, verification worker (polls + on-demand), rechecks, auto-release, reclaim path.
- Serving contract: host-resolve internal API, neutral error for unknown/unverified, canonical/tag behavior, host-namespaced cache keys and purge tags.
- Lapse/removal semantics and notices; audit coverage; abuse paths (denylist, phishing suspension, cycling throttles).

## Out of scope

- Plan gating values and subscription lifecycles (features 32-40 supply the lapse signal; this feature consumes the feature 01 snapshot).
- Base SSR tag mechanics (feature 10 owns them; this feature adds only the host-aware canonical case).
- Creator-uploaded certificates (not in v1), per-profile automatic subdomains (v2 candidate), email hosting on custom domains (never out of product scope).
- Account deletion interplay: bindings are deleted by the feature 02 purge participant contract - no separate path here.

Related: rules in `docs/01-requirements-ba/04-custom-domains/02-user-stories-and-business-rules.md`; contracts in `docs/02-architecture-techlead/04-custom-domains/03-api-contracts-and-payloads.md`; error surface in `docs/01-requirements-ba/04-custom-domains/05-exception-handling-and-errors.md`.
