# Platform Baseline - Architecture and Service Topology

Phase 2 TL · Cross-cutting baseline for all features. This file supersedes the legacy monolith-first architecture decision: DEVIATION FLAGGED (in force since migration start, per the mandated stack in `AGENTS.md`) - the platform is microservices: gateway-terminated sessions, database-per-service, API/event-only cross-service truth. The legacy split-trigger rubric is retained below as the extraction doctrine. Stack pins: `docs/02-architecture-techlead/00-platform-baseline/02-stack-and-build-pins.md`.

## Edge topology

| Element | Responsibility |
|---|---|
| Nginx edge | Serves the React SPA bundle + `/media/*` (read-only volumes); edge cache for public reads; security headers + `limit_req`; reverse-proxies `/api/*`, `/go/*`, `/:username` to the gateway (`09-local-storage-and-nginx-spec.md`) |
| Gateway (API edge) | Terminates SSO sessions (Google, Apple, Facebook, LinkedIn, generic OIDC); enforces route-class rate rules; issues the internal identity JWT (`RS256`, `aud=<service>`, 60 s TTL, JWKS at `/.well-known/jwks.json`); routes `/api/v1/*` (session), `/internal/v1/*` (service-to-service), `/go/*` and `/:username` (public SSR) |
| Services | Trust only the signed internal JWT - raw client headers are never trusted; deny-by-default Spring Security |
| Public paths | `/:username`, `/go/*` are served by services behind the gateway; static assets and `/media/*` are served by the Nginx edge; no direct database access from clients or beacons exists; anonymous write surfaces are the beacon endpoint (`POST /api/t/:username`, feature 12), lead-form submits (`POST /api/t/leads/submit/{blockId}`, Turnstile-class + throttled, queue-mediated, feature 27), partner callbacks (`POST /api/merch/partner-callback`, signature-verified, queue-mediated, feature 23), rail callbacks (`/webhooks/*`, signature/token-verified per rail, feature 40), publish OAuth callbacks (`GET /api/v1/social/{network}/callback`, single-use state + PKCE, feature 41), provider signals (`POST /webhooks/social/{network}`, signature-verified, features 41-42), and the IG comment webhook (`POST /webhooks/instagram`, signature-verified, queue-mediated, feature 44) |

## Service map (registered; extended additively as majors migrate)

| Service | Features | Host port | Database | Redis db | Role |
|---|---|---|---|---|---|
| gateway | all (edge) | 8080 (internal; public edge is Nginx 443) | - | 1 (counters) | Session termination, identity JWT, rate rules |
| account-service | 01-03, 05, 45 | 8081 | account_db | 7 | SSO login + linked accounts; sessions + step-up; entitlement snapshot; deletion orchestration; concierge data; agency roster + Act-as sessions (45); manages edge session keys (db 2) |
| profile-service | 04, 06, 07, 08, 09, 27, 28 | 8082 | profile_db | 4 | Username namespace authority; blocks; themes/avatar; leads capture + contact workspace; internal read APIs |
| link-service | 29-30 | 8083 | links_db | 3 | Single redirector; short links + QR renders; consumes profile-service namespace rules |
| public-site-service | 10, 11 | 8084 | stateless | 5 | SSR public pages; escape banner + micro-script; PWA shell; cache + purge |
| analytics-service | 12-15, 31, 47 | 8085 | analytics_db | 6 | Beacon + link-hit intake (queues), hourly rollup, realtime window, short-link stats, owner reads, agency reporting |
| resume-service | 17-21 | 8086 | resume_db | 8 | Resume documents + immutable snapshot versions; file archive (local disk); `/cv` public read; AI JD-tailor endpoint (35) (18-21 extend this service) |
| shop-service | 22-26 | 8087 | shop_db | 9 | Uniform product catalog (7 kinds); storefront reads; fulfillment/courses/tips/fees extend this service |
| billing-service | 32-40, 46 | 8088 | billing_db | 10 | Money kernel: orders + checkout front door; verified grants; plan ledger; lifecycle jobs; receipts; refunds; promos; eight PSP rails; notification pipeline (transactional); agency seat billing |
| publish-service | 41-44 | 8089 | publish_db | 11 | Connections vault + OAuth; composer + calendar + dispatch fan-out; automations + contacts + DM delivery; in-service worker pools behind `DispatchPort`/`WatchPort` |

Redis logical dbs 0-2 are reserved for edge/platform infrastructure (db 1: rate counters; db 2: edge session keys, contract in `docs/02-architecture-techlead/01-account-and-access/03-api-contracts-and-payloads.md`); services take sequential dbs from 3 up. New rows are added here in the same change that starts a new service - no unregistered service exists.

## Service rules (all features)

- Database-per-service: only the owning service connects to its database; no cross-service tables, joins, or foreign keys. Cross-service truth moves via internal REST (`/internal/v1/*`, service JWT) or events only.
- Stateless replicas behind the gateway; session state lives at the edge, not in services.
- Events: `<domain>.<fact>.v<major>` (e.g. `profile.published.v1` (`username, updatedAt`), `username.renamed.v1`), emitted after commit, at-least-once, idempotent consumers; lost events self-heal via cache TTLs (60 s default bound).
- Internal REST: JSON, additive-only, RFC 9457 errors, TEXT ids, ISO8601 timestamps; per-service budget for internal routes.
- Resilience: per-source timeout (2 s default for fan-out), circuit breaker per remote dependency, degrade matrix per service file; no retry storms - bounded backoff only.
- Observability: W3C `traceparent` gateway -> service -> queue; Micrometer/OTel export; append-only audit log with 24-month retention (schema baseline in `03-data-layer-conventions.md`).

## Extraction doctrine (when a capability gets its own service)

Score all four triggers per use case, recorded in the proposing spec; a new service requires >=2 triggers plus gate numbers:

| Trigger | Threshold |
|---|---|
| Team ownership | >=2 independent teams hitting merge contention on the same deployable |
| Failure isolation | A critical path (grants/webhooks) must survive content-deploy faults |
| Scaling asymmetry | Hot path differs >=10x from its host service at recorded load gates |
| Data gravity | Write volume (beacon/click streams) cannot share the host's database budget |

Precedent: features 12-16 extended their host service in-service because no pair of triggers fired (dual-lag/dual-replay semantics for read-only projections, in-process validators for assist, no >=10x asymmetry). Async, bursty, independently scalable paths (beacon ingest, publish fan-out, later reporting read-models) are the recognized first extraction candidates; extraction requires a spec amendment.
