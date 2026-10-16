# Resume Sharing Links - Security, Logging, and Rate Limiting (feature 20)

Phase 2 TL · BA sources: `docs/01-requirements-ba/20-resume-sharing-links/` (BR-01, BR-04, BR-07, BR-08, BR-09, BR-10). Baseline: `docs/02-architecture-techlead/00-platform-baseline/08-security-baseline.md`; gates: `docs/02-architecture-techlead/00-platform-baseline/06-release-gates.md`.

## Controls

| S | Control | Verification |
|---|---|---|
| S-1 | IDOR + RLS: helper and mint scope by server-set `user_id`; foreign ids respond `403` (no state oracle); direct PG as app role denied | AC-05; API + direct-PG probes |
| S-2 | XSS/CSP across five render paths: strings stripped at capture (feature 17) so every consumer renders clean data; CSP `default-src 'self'` on dashboard and cv-family pages | AC-06; hostile-field corpus on all five paths |
| S-3 | Uniform revocation: one resolver rule (`is_public` + `published_version` + profile state) maps to five renderings; no per-type kill switch exists; the matrix test proves one flip covers all | AC-02, AC-12; single-flip drill |
| S-4 | Mint integrity: slug PII-free (random base62); destination = canonical cv URL (`https:` only, blocklist enforced by link-service); idempotent by ref pair; suspended/expired show branded safe-errors, never stale content | AC-03; slug inspection + forced-expiry drill |
| S-5 | Unlimited, abuse-controlled: no quota/credit/gate code path exists for authenticated resume shares (grep); anonymous-path abuse rides owning majors' Turnstile/WAF/rate rules and never touches legitimate use | AC-07; grep + burst drill |
| S-6 | Derivation, not storage: zero share-URL tables/columns anywhere; helper output is computed per request | AC-08; schema + grep proof |
| S-7 | Rename integrity: destination rewrite is event-driven, idempotent, and backstopped by the old-username 301 - no dead-link window beyond one redirect hop | AC-04; rewrite + suppressed-event drill |
| S-8 | Secrets: environment/vault only; `.env` and compose overrides uncommitted; no secrets in logs or bundles. DEVIATION CARRIED: the legacy wrangler-era secret stores (`.wrangler/`, `.dev.vars*`) are superseded by the containerized baseline - the audit's intent (no committed secrets) is preserved. | Secrets audit on the release candidate |
| S-9 | Logging: helper calls and mints log ids/timings only (document id, created, latency, trace); never URLs-with-usernames, never snapshot content; owning features' audit rows cover flips and linkage changes (24-month retention) | Log scan + audit reconstruction |

## Rate envelopes

| Route class | Envelope | On breach |
|---|---|---|
| Helper read | 600/min/user | `429 throttled` + `Retry-After` |
| Shorten mint | 30/h/user (idempotent; effectively <= 1 per document) | `429` |
| Anonymous cv/short/custom paths | Owning majors' envelopes (feature 10 / 29 / 04 rows) | Their `429`/safe-error behavior; legitimate unlimited use unaffected |
| Support matrix reads | static doc | - |

## Degrade matrix

| Condition | Behavior |
|---|---|
| profile/domains by-user read down | Helper `503 share_unavailable` (retry); dialog avoids showing partial rows; published cv links unaffected |
| link-service down | Shorten `503`; shortened row keeps `Mint on first copy`; all other rows work; existing slugs resolve via cache within its TTL, then safe-error |
| Rename event lost | Old-username 301 resolves the short link (one hop); next rewrite pass catches up |
| Purge event lost | Owning TTLs heal within their bounds (60 s cv; shortener's own cache TTL); never stale indefinitely |
| Block linkage read down | `bioLinked` defaults false; toggle retries; page rendering unaffected |
| resume-service down | Dialog helpers unavailable; all five render paths keep serving per their owners (no shared runtime) |

## Gates

- G4: helper fields additive-only; `seoSameAsStandard` permanently true; the link-service rows are additive extensions of feature 29's contract.
- G5: S-1, S-3, S-4, S-6 probes run per release.
- G7: drill set AC-01..AC-12 attaches to the tag; the support matrix (TL 02) ships with the release note.
- Grep gates: zero share-URL storage; zero PDF code both sides (shared with feature 18); no quota code path on resume-share mints.
