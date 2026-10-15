# Custom Domains (verify, bind, serve)

PLAN ref: P2-6 (`custom_domains{hostname, profile_id, verified}` via `_cf-challenge TXT`, serve same SSR). P2 scope — buildable once public SSR ships. Downgrade-detach behavior consumed from `monetize/billing/subscription-lifecycle.md` (DNS untouched, serving stops); this doc owns verification and serving.

## Purpose

Let pro creators serve their page from their own hostname with zero wrong-site risk: cryptographic verification before any binding, same SSR and cache posture as `/:username` once bound, and instant detachment (serving stops, DNS untouched) on downgrade, unlink, or abuse. An unverified hostname must never render another user's page — that invariant is the whole doc.

## Users

- Creator (pro): *As a Creator, I want to add a hostname, set a TXT record, wait for verification, go live, and remove the domain freely so that my page serves from my own hostname with zero wrong-site risk.* Adds a hostname, sets a TXT record, waits for verification, goes live, removes the domain freely.
- Visitor: *As a Visitor, I want to open `creator.com` and get the bound profile with correct SEO tags or a clean error so that verified hosts render and unknown hosts fail cleanly.* Opens `creator.com` and gets the bound profile with correct SEO tags (canonical = custom host) or a clean error for unknown/unverified hosts.
- Abuser (adversarial): points their domain at our IPs, claims another profile, or rides an ex-owner's DNS — and gets nothing.

## Functional requirements

1. Binding flow: owner adds hostname (normalized lowercase, punycode-stored; apex + subdomains allowed, see Q1 for exclusions) → platform issues `_cf-challenge.<host>` TXT expectation → creator sets DNS → verification worker checks TXT (poll with backoff, timeout with retry guidance) → `verified` flips → serving starts. Unverified rows never serve (any request on them gets the unknown-host error, never a profile).
2. Verification integrity: challenge values unguessable per hostname (random, single-use per attempt round); re-verification on every re-bind and periodic re-checks of live bindings (lapsed control — TXT removed or CNAME moved — flips to `unverified` with creator notice and serving halt, never a silent takeover window). Hostnames on deny-lists (platform, provider, and lookalike-abuse lists) rejected at add time.
3. Serving: verified host renders the bound profile's same SSR as `/:username` (title/meta/canonical/OG/JSON-LD with canonical rewritten to the custom host; `/:username/cv` and `/shop` mounted under the same host per profile settings). Cache keys namespaced per hostname (+ same purge-on-publish); KV single-flight honors host+slug. TLS via the platform's managed issuance (creator uploads no certificates in v1).
4. One-host-one-profile invariant: a hostname binds to exactly one profile at a time (unique constraint);Takeover attempts (binding a host bound elsewhere) require proof-of-control re-verification that the current binder cannot satisfy without DNS control — disputes resolve to DNS control, documented in support runbook. Profile transfer between accounts carries no domain rights (bindings drop to re-verify).
5. Downgrade/unlink: pro lapse → serving stops within the entitlement window (DNS untouched — creator's records stay valid for re-upgrade); explicit removal → binding deleted after a short hold (see Q2) with serving halt immediate. All transitions audited; removal/lapse notices state the re-verify path.
6. Abuse: unverified-host requests get a neutral error page (no profile content, no open-redirect); rapid add/verify cycling throttled; phishing-report on a bound host suspends serving pending review (profile stays live on `/:username` — domain suspension never equals account suspension).

## Data / API touches

- Table `custom_domains{hostname UNIQUE lowercase, profile_id, user_id, verified BOOL, challenge_ref, verified_at?, last_recheck_at?}` (standard `user_id` + RLS; unique hostname global — first-verified-wins with dispute path). All PG via `worker/db/*`; verification worker scheduled + on-demand recheck endpoint.
- Endpoints: owner add/remove, `POST /api/v1/domains/:host/verify|recheck` (rate-limited), public serving resolver (hostname → profile lookup on the Worker edge path, cached short-TTL negative for unknown hosts). DNS/query logic uses fetch-only primitives (Workers-safe).
- Edge routing: Host-header-based profile resolution precedes slug routing; unknown/unverified hosts short-circuit to the error page before any profile query (no wasted DB reads, no leakage).

## Acceptance criteria

- Takeover drill: unbound/claimed-elsewhere hostname serves the neutral error (never any profile); successful TXT control transfers binding only through full re-verification with audit. *GWT: Given an unbound or claimed-elsewhere hostname; When it is requested or TXT control changes; Then the neutral error serves and transfers complete only through full re-verification with audit.*
- Lapse drill: TXT removal detected by periodic recheck → `unverified` + serving halt + notice; restoration re-verifies cleanly. *GWT: Given a live binding with TXT removed; When the periodic recheck runs; Then the row flips to `unverified` with serving halt plus notice, and restoration re-verifies cleanly.*
- Downgrade drill: pro lapse stops custom-host serving within the window while `/:username` stays live; DNS records untouched; re-upgrade restores without re-verification inside the hold (see Q2). *GWT: Given a pro lapse; When the entitlement window elapses; Then custom-host serving stops while `/:username` stays live, DNS stays untouched, and re-upgrade inside the hold restores without re-verification.*
- Cache drill: publish purges host-namespaced keys; slug + host matrix green; unknown-host negatives cached (no DB amplification under scan). *GWT: Given a publish or a scan of unknown hosts; When cache and resolver run; Then host-namespaced keys purge, the slug-plus-host matrix stays green, and unknown-host negatives cache with no DB amplification.*
- IDOR drill: user A domain CRUD against user B profiles returns 403; Host-header probing cannot surface private (`is_public=0`) profiles. *GWT: Given user A domain CRUD against user B profiles or Host-header probing; When the request resolves; Then CRUD returns 403 and private profiles never surface.*

## Out of scope

- Plan gating values (see `monetize/plans/` + `plans/tier-presentation.md` in this major); downgrade execution detail (`monetize/billing/subscription-lifecycle.md`); public SSR tag mechanics (see `link-to-bio/public-page/share-seo.md`, consumed with host-aware canonical).
- Creator-uploaded certificates (not in v1); subdomain auto-provisioning per profile (v2 candidate); email hosting on custom domains (never — out of product scope).

## Open questions

- Q1: Hostname policy (apex allowed? wildcard? IDN display rules?) — launch allowlist?
- Q2: Removal/re-upgrade hold window (grace re-bind without re-verification) — length, if any?
