# Custom Domains - Architecture and Tech Stack (feature 04)

Phase 2 TL · Service: `profile-service` extension (V3 migration; no new service, no new container). Implements `docs/01-requirements-ba/04-custom-domains/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | The domain registry lives in `profile-service` (`profile_db`): hostname -> profile is a second namespace, and the service already owns `username` resolution and the profile-owner predicate. | Namespace authority stays in one service; the serving path reuses the existing internal-resolve pattern instead of adding a hop. |
| D-2 | Binding state is a status enum (`pending`, `verified`, `unverified`, `suspended`), not the legacy `verified BOOL`. | Control loss, lapse, and never-verified need distinct transitions; serving reads one state machine. |
| D-3 | Serving is decided at host resolve: `status = verified` AND `plan = pro` from the 30 s-cached feature 01 snapshot (fail-closed on miss). | One server-side gate on the hot path; no client flag, no per-request account call. |
| D-4 | The verification worker runs in `profile-service` (scheduled sweep + on-demand routes); DNS lookups use DoH/fetch-only primitives; challenges are stored hashed and single-use per round. | No new infra; no plaintext secrets; workers-safe with the platform's edge model. |
| D-5 | State changes emit `domain.state_changed.v1` for cache purge and tooling; publish purges ride the existing username tag. | Lost-event self-healing via cache TTLs; purge stays one mechanism (baseline). |
| D-6 | Disputes resolve through an ops-only reclaim round (fresh challenge forced on the disputed row; audited transfer) plus auto-release of failing `unverified` rows after 14 days. | DNS control is the only proof; ops never binds or serves as a user. |

## Boundaries

`profile-service` owns: `custom_domains` + `domain_denylist` rows, normalization, challenges, worker, resolve API, state events, notices. It does not own: TLS issuance execution (platform edge job consumes the verified event), SSR tag rendering (feature 10 adds the host-aware canonical), plan values (features 32-40; this feature consumes the snapshot), deletion purge (feature 02 contract - the profile purge handler deletes `custom_domains` rows).

## Edge integration

- Gateway routing order: platform hosts -> normal route table; any other Host -> `GET /internal/v1/domains/{host}` before slug routing; `result=profile` routes to public-site SSR with the custom host context; `blocked`/`unknown` short-circuit to the neutral error page (no profile query, no redirect).
- Edge caches: positive resolve 30 s, negative 60 s (feature 10's cache layer namespace); binding state changes purge the host's keys immediately via `domain.state_changed.v1` - TTLs are the backstop, never the primary path.
- TLS: managed issuance is triggered by the `verified` transition (edge job); issuance/renewal failure withholds serving (fail-safe) and pages ops.

## Consumers

| Consumer | Contract |
|---|---|
| Gateway + public-site-service | `GET /internal/v1/domains/{host}` resolve; host-aware SSR canonical (feature 10 row updated in this change) |
| Abuse tooling | Denylist route + `domain.state_changed.v1` streams; phishing suspension |
| Support runbook | Reclaim round (`POST /internal/v1/domains/{host}/reclaim`), audited |
| Feature 02 | Profile purge handler deletes `custom_domains` + `domain_denylist` rows for the owner (participant contract) |
| Feature 03 | Presents the pro gate; no domain claims beyond it |

## Stack notes

Spring Boot `4.1.1` / Kotlin `2.4.21` stack unchanged; new internals: `HostnameNormalizer` (IDNA2008 punycode), `ChallengeService` (hashing, rounds), `DomainVerifier` (DoH TXT checks), `DomainScheduler` (poll + 24 h staggered recheck, single-flight lock in Redis db 4).
