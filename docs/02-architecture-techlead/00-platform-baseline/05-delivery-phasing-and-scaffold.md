# Platform Baseline - Delivery Phasing and Scaffold

Phase 2 TL · Cross-cutting baseline. Turns the numbered spec tree into one buildable sequence. Every increment ships as independently runnable container images against PG `18.6`; no increment is done until its images pass the release gate (`06-release-gates.md`). Build order for docs migration: `MIGRATION.md`.

## Target repo layout (product workspace)

| Path | Contents |
|---|---|
| `services/<service>/` | One Gradle build per service (wrapper, catalog, Dockerfile, tests); no shared source tree between services - shared code is copied or becomes a versioned internal library |
| `web/` | React 19.3 SPA workspace (lockfile committed); built to static assets |
| `gateway/` | Edge gateway config + JWKS + rate rules |
| `deploy/` | Compose slice, manifests, runbooks, `.env.example`, drill scripts |
| `docs/` | This spec tree (numbered two-phase); code never lands without an approved spec |

Scaffold (E0) stands up this layout with the pinned toolchain, empty-boot services (health endpoints, Flyway baseline per service), gateway identity wiring, compose slice, CI build + SBOM. E0 exits with a clean-checkout compose boot green under both Docker and Podman and no domain tables beyond service baselines - the smallest artifact the gate accepts.

## Increment map (feature register - `MIGRATION.md`)

| Increment | Features | Contents | Exit criteria |
|---|---|---|---|
| E0 scaffold | - | Repo, toolchain pins, gateway, compose, CI, release gates | Compose boot pair green; gate dry-run on empty services |
| E1 identity + link-to-bio | 01-16 | Account/session/tier baseline + the full link-to-bio surface (public profile, blocks, themes/avatar, share/SEO, PWA, analytics 12-15, gated assist 16) | Account drills green; link-to-bio drills green (features 06-16 AC sets); beacon pipeline load smoke |
| E2 resume + shop + leads + shortener | 17-31 | Resume builder/print/share, shop catalog/flows, leads capture, url-shortener 29-31 | Their AC drills green; checkout/link gates per feature |
| E3 monetize | 32-40 | Plans, rails (eight equal), grants, payouts, ledger reads | Money-path drills green (idempotent grants, explicit `provider`); 100k-mixed load gate |
| E4 publish + automate + agency | 41-47 | Publishing/automation + agency teams/white-label | Their drills green; first extraction evaluations reviewed against the doctrine trigger table |
| E5 scale-out | - | Replicas, read/write split, pre-render, durability drills | 1M read-heavy + 100k mixed k6 green; logged restore (`07-scale-out.md`) |

Gated backlog 48-53 (marketing, affiliate, advertising, sales, data-analysis, user-behavior) stays design-only until its own amendment; feature 16 assist stays gated per its own files.

## Increment rules

- Every increment cites its BA set + TL set; no increment contradicts an approved spec without `DEVIATION FLAGGED` in the proposing file.
- Traceability: feature number -> images (tags) -> Flyway versions -> drill results archived with the release note; support can answer "which build shipped feature X" from the tag alone.
- Increments are additive: earlier increments keep passing their drills (regression suite per increment grows, never shrinks).
- Environment parity: dev/staging/prod run the same digests; staging mirrors production config shape (secrets differ, never config keys).
- Data migration between increments is forward-only via Flyway; no manual SQL against live databases exists in any runbook.
- Scope gates: an increment may not pull scope forward from a later increment or the gated backlog; sequencing changes are amendments.

## Phase-0 exit checklist (E0)

- Clean checkout -> `./gradlew build` green with pinned versions asserted; SBOM archived.
- Compose boot green under Docker and Podman; healthchecks pass; ports per `04-containers-and-compose.md`.
- Gateway issues internal JWTs (RS256, 60 s TTL, JWKS published); a sample service verifies audience correctly.
- Migrations: fresh-apply replay green; restore-to-scratch drill logged once.
- CI gates wired: wrapper checksum, SBOM scan, OpenAPI diff (empty baseline), repo secret grep.
