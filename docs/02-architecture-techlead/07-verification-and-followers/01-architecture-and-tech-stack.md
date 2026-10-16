# Verification Badge and Follower Display - Architecture and Tech Stack (feature 07)

Phase 2 TL · Extension of `profile-service` (module `com.app.profile.trust`), same database `profile_db`. Implements `docs/01-requirements-ba/07-verification-and-followers/`. Platform pin set: `docs/02-architecture-techlead/00-platform-baseline/`.

## Service boundary (microservices)

| Owns | Excludes (owner -> contract) |
|---|---|
| `verification_requests` + `follower_count_history`, the badge state machine, the ops review queue, follower totals cache + scheduled refresh, the near-match advisory flag | OAuth token vault, posting, automations -> never built in v1 (grep gate); SSR badge/total rendering + purge -> feature 10; social deep-link behavior -> feature 11; blocks embedding of socials -> feature 08; export mechanics -> feature 21 |

Rationale for staying in `profile-service`: badge transitions must share the profile transaction (a rename hides the badge atomically), the refresh job reads the same RLS baseline, and the public path reads cached totals from the profile row. Extracting trust would split badge state from profile state for an ops-low-volume path - no split pressure in v1. Same argument as `docs/02-architecture-techlead/06-public-profile/01-architecture-and-tech-stack.md`.

Cross-service rules: no new database, no new container; SSR (feature 10) consumes the additive fields on the internal resolve payload; evidence blobs live in MinIO under random keys (platform object store); no cross-service table access.

## Runtime delta (compose slice unchanged from feature 06)

```yaml
  profile-service:                # same service; new env only
    environment:
      APP_FOLLOWERS_REFRESH_CRON: "0 0 */24 * * *"
      APP_EVIDENCE_BUCKET: "profile-evidence"
      APP_FETCH_BUDGET_PER_NETWORK: "1"
```

No new containers. MinIO and Redis come from the platform compose; the evidence bucket is provisioned by the platform job. Postgres digest pinned in CI; Podman parity (`podman compose up`) is release acceptance.

## build.gradle.kts delta (no new dependencies)

The stack is unchanged versus `docs/02-architecture-techlead/06-public-profile/01-architecture-and-tech-stack.md`: Boot 4.1.1, Kotlin 2.4.21, Gradle 9.8.1 wrapper. `@Scheduled` (spring-context) and `RestClient` (spring-web) are built-in; blob access reuses the platform `BlobPort`. No OAuth, no scheduler library, no HTTP client library added - the grep gate in T-12 fails the build on vault-shaped dependencies.

## React.js 19.3 integration topology

Two routes in the platform SPA: `/app/trust` (creator cards; shares the `VerifiedBadge` SVG component with the SSR renderer) and `/app/ops/trust` (role-gated queue, invisible to creators; 403 body carries no queue data). Field rules in `docs/01-requirements-ba/07-verification-and-followers/04-ui-data-dictionary.md`; error map in `03-api-contracts-and-payloads.md`. No evidence bytes in client state (keys/URLs only, grep-gated).

## Key decisions

| Decision | v1 | Trigger to revisit |
|---|---|---|
| Manual review only | no auto-award; near-match is advisory | ops volume > 100 applications/week (Q-01) |
| Follower history storage | new `follower_count_history` table in `profile_db` (V2 migration) | write volume makes history noisy -> cap per profile |
| Purge signaling | reuse `profile.published.v1` (no new event) | trust-only consumers need separation |
| Public DTO | additive extension of `PublicProfileResponse` (feature 06) | breaking change required (never in v1) |
| Token vault | never built in v1 | P4 program approval |
