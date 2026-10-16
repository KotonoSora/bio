# Platform Baseline - Security Baseline

Phase 2 TL · Cross-cutting baseline. Features keep their own `S-n` control files; this file defines the platform envelope they build on. Topology: `01-architecture-and-service-topology.md`; gates: `06-release-gates.md` (G5 probes).

## Identity and sessions

- SSO-only: Google, Apple, Facebook, LinkedIn + generic OIDC. No passwords, no reset flow exists anywhere.
- Sessions terminate at the gateway (HttpOnly/Secure/SameSite cookie, CSRF double-submit on mutations); services never read session cookies. Lifetimes, revocation bound, and the edge validation fallback are owned by feature 01 (`docs/02-architecture-techlead/01-account-and-access/05-security-logging-and-rate-limiting.md`).
- Gateway signs the internal identity JWT: RS256, `aud=<service>`, 60 s TTL, JWKS at `/.well-known/jwks.json`, dual-key rotation window. Services verify signature + audience + expiry on every request; raw client headers are never trusted.
- Act-as-user identity class (45): short-TTL act-as tokens (claims `{act_as:true, actor, org, profile}`) carry a distinct issuer from login sessions; the gateway passes them as a second identity class; services enforce a per-endpoint-class allowlist and deny account-level classes (password/email/plan/delete/billing/roster) by default. Validators never confuse the classes.
- Deny-by-default Spring Security: every route is authenticated or explicitly public; public lists are enumerated in each service spec (profile page, beacon, redirects, health).

## Authorization and data access

- Ownership scoping is server-side: handlers derive `user_id` from the JWT, never from request bodies; a `profile`/`owner` param that mismatches the session actor returns the feature's stable 403 code (`foreign_profile` family).
- RLS is the second layer: enabled on every business table (`03-data-layer-conventions.md`); app role is RLS-limited; migration role never serves traffic.
- Internal APIs (`/internal/v1/*`) require service JWTs and are unreachable from the public edge (gateway routes internal namespaces only from the mesh).
- Ops actions are audited append-only (actor, action, target, result); support never gets a cross-user read path - disputes resolve through owner-shared sessions or audited tooling.

## Input, output, and webhook safety

- All input validated by Bean Validation + per-endpoint rules; TEXT ids; unknown fields rejected (fail-closed parsing).
- XSS: React escaping + server-side text storage; no HTML from user input anywhere; CSP default-src self enforced at the edge for public pages.
- Uploads (feature 09 avatars): content-type + magic-byte validation, size cap, image re-encode, no SVG; stored privately, served via signed URLs (<= 5 min).
- Webhooks (money rails, features 32-40): signature verification per provider + idempotency keys; replay outside the timestamp window rejected; grants are idempotent by design.

## Abuse and rate control

| Route class | Default envelope (gateway) | Tightening |
|---|---|---|
| Anonymous public reads | 120 req/min/IP | Feature specs may tighten (cache-first paths) |
| Auth/SSO redirects | 20 req/min/IP | Turnstile-class challenge on abuse signals |
| Beacon/telemetry intake | 60 req/min/IP tolerated burst | Feature 12: uniform 202, dedupe window; silent drop, no oracle |
| Owner API reads | 60 req/min/user | Feature 15: CSV 6/min; feature 14: 1/10 s per profile-user |
| Webhooks | provider IP + signature | Replay window per provider spec |
| Internal mesh | service JWT + network policy | No public route exists |

Feature-level numbers win when stricter; loosening requires a flagged amendment. Throttle responses are `429` with `Retry-After`; probing emits stable `422` codes and is counted, not punished.

## Secrets, logging, and supply chain

- Secrets come from environment/vault only; `.env*` and compose overrides are never committed (CI grep gate); rotation per `06-release-gates.md` drills; internal JWT dual-key rollout.
- Logs carry route, status, timing, trace id only - never payloads, PII, money values, or per-profile labels in metrics. Error bodies follow RFC 9457 without stack traces.
- Images ship non-root, minimal, no secrets, SBOM-scanned; provenance archived per tag (`04-containers-and-compose.md`).

## Degrade matrix (security-relevant defaults)

- Internal JWT verification failure: request denied (fail-closed), alert emitted.
- Entitlement/plan snapshot unavailable: fail-closed to the freest tier (features 13-16 pattern).
- Rate limiter unavailable: requests pass with alert but per-service backstops (Redis counters) still hold; if both fail, anonymous classes fail-open with heightened logging while owner writes fail-closed.
- Cache/queue loss: reads degrade to zeros/partial labels per feature honesty rules (never fabricated values); intake already returned 202 stays accepted and un-duplicated.
