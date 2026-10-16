# Custom Domains - User Stories and Business Rules (feature 04)

Phase 1 BA · Enforced by `docs/02-architecture-techlead/04-custom-domains/`. Scope: `docs/01-requirements-ba/04-custom-domains/01-document-context-and-scope.md`.

## User stories

| Story ID | As a | I want to | So that | Priority |
|---|---|---|---|---|
| US-01 | Creator (pro) | add a hostname, set the TXT record, wait for verification, go live, and remove the domain freely | my page serves from my own hostname with zero wrong-site risk | High |
| US-02 | Visitor | open `creator.com` and get the bound profile with correct SEO tags, or a clean error | verified hosts render; unknown hosts fail neutrally | High |
| US-03 | Abuser (adversarial) | - (gets nothing) | unverified/unowned hosts never render anyone's page | High |
| US-04 | Creator (pro) | keep DNS untouched through a lapse and restore without re-verifying inside the hold | a billing hiccup never means DNS edits | Medium |
| US-05 | Support/ops | resolve a DNS-control dispute through an audited reclaim round | the true DNS owner always wins | Medium |

## Business rules

- **BR-01 Verify before bind.** A binding serves only in `verified` state. Challenge: random 32-byte token in `_rb-challenge.<host>` TXT (`rb-verify=<token>`), shown once to the owner, stored hashed, single-use per attempt round, 7-day window; regeneration issues a fresh round. Unverified rows never serve - any request on them gets the neutral error page, never a profile, never a redirect.
- **BR-02 Normalization + denylist.** Lowercase, trimmed, trailing-dot stripped; IDN to punycode (IDNA2008) with original form displayed; total length `<= 253`, labels `1-63`; wildcards rejected (`422 hostname_invalid`). Denylist (platform hosts, identity-provider domains, lookalike/brand list) rejects at add (`422 domain_denied`) and is re-evaluated on every recheck; a denylist hit on a live binding suspends serving pending review.
- **BR-03 One host, one profile.** Hostname is globally unique; a taken host requires the full re-verify path (proof of DNS control), audited transfer with notice to the prior holder; disputes resolve to DNS control via the ops reclaim round (BR-12). Profile transfer between accounts carries no domain rights (bindings drop to re-verify; account deletion purges bindings per feature 02).
- **BR-04 Serving parity.** A verified host renders the bound profile's same SSR as `/:username` (title/meta/OG/JSON-LD), canonical rewritten to the custom host; `/cv` and `/shop` mount under the same host per profile settings; `/:username/cv` keeps its `noindex` + canonical-to-bio. Unknown/unverified hosts serve the neutral error page (`404`, no content, no open redirect); the negative resolve result is edge-cached 60 s (no DB amplification under scans).
- **BR-05 Cache posture.** SSR keys are namespaced per hostname (`ssr:{host}:{path}`) and tagged with the profile username, so publish purges flush `/:username` and custom-host entries together; binding state changes purge the host's keys immediately.
- **BR-06 Plan gate.** Add/verify and serving require `plan = pro` from the feature 01 snapshot (fail-closed: snapshot miss denies). Serving stops within `<= 60 s` of a lapse; `/:username` stays live.
- **BR-07 Lapse hold.** On lapse the binding moves to `suspended` (serving halts, DNS untouched, notice with restore path); a passing recheck inside 30 days restores `verified` without re-verification; beyond 30 days the binding becomes `unverified` and needs a fresh challenge. Explicit removal deletes immediately with no hold.
- **BR-08 Control loss detection.** Live bindings recheck every 24 h (staggered): TXT removed or moved flips to `unverified` with serving halt and notice - detection within 24 h, never a silent takeover window. `unverified` rows with failing control for 14 days auto-release (hostname becomes addable again).
- **BR-09 Abuse handling.** Rapid add/verify cycling is throttled; phishing report on a bound host suspends serving pending review; domain suspension never equals account suspension (profile stays live on `/:username`).
- **BR-10 TLS.** Platform-managed issuance on verification, auto-renewed; no creator-uploaded certificates in v1; issuance failure means no serving (fail-safe, never plaintext).
- **BR-11 IDOR and privacy.** Domain CRUD is owner-scoped (`403` on foreign targets, `404` on unknown hostnames); Host-header probing cannot surface private (`is_public=0`) profiles or suspended accounts - both resolve as the neutral error.
- **BR-12 Reclaim path.** Support/ops can force a fresh challenge round on a disputed binding after documented evidence; success transfers ownership in one audited transaction (notice to both parties); the route requires ops scope and is audited. Ops can never bind or serve as a user.

## Constraints carried

- Entitlement snapshot, fail-closed semantics, and step-up are consumed from `docs/02-architecture-techlead/01-account-and-access/03-api-contracts-and-payloads.md`; nothing is re-decided.
- Events follow the platform rule (after-commit, at-least-once, idempotent consumers; lost events self-heal via cache TTLs).
- No fee/pricing values appear here; the only money-adjacent fact is the `pro` requirement itself (feature 03 presents it).
