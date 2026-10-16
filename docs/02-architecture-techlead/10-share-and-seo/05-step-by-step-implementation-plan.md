# Share and SEO - Step-by-Step Implementation Plan (feature 10)

Phase 2 TL · Dependency-ordered chain; each task's acceptance goal gates the next. Subsystems: BE, FE, DevOps. Criteria cited from `docs/01-requirements-ba/10-share-and-seo/06-acceptance-criteria.md`.

| Task ID | Subsystem | Step Description | Dependency | Acceptance Goal |
|---|---|---|---|---|
| T-01 | BE | Service skeleton: Boot 4.1.1/Kotlin, gateway routes (precedence table in `01-architecture-and-tech-stack.md`), Redis db 5 wiring, no datasource (build gate) | platform baseline refs | Route-precedence tests green; no JDBC on classpath |
| T-02 | BE | Internal clients (06 resolve + profiles:public, 08 blocks, 09 themes) with 2 s timeouts, deadline plumbing, service JWT | T-01 | Contract tests vs stubs; budget <= 3 calls asserted |
| T-03 | BE | `SsrRenderer` + Thymeleaf fragments (`head`, `blocks`, `seo-tags`) + `<!-- ssr -->` marker | T-02 | Fixture renders green; zero JS in output |
| T-04 | BE | `SeoTagBuilder`: title/meta/canonical/OG/Twitter/JSON-LD, escaped; `/cv` `noindex` + canonical-to-bio | T-03 | AC-01 green; named failure mode tested |
| T-05 | BE | Resolve flow: profile -> [redirect 301] -> blocks -> render; parity 404 for private/missing/retired | T-04 | AC-06, AC-07 shape green |
| T-06 | BE | Cache layer: `ssr:*` keys (60 s), Caffeine L1 (10 s), header matrix (`s-maxage`/SWR/`no-store`/301 policy) | T-05 | Cache drill + AC-02-accompanying header tests green |
| T-07 | BE | Single-flight `render:<slug>` (NX PX 5000; waiters poll <= 3 s, then stale/503) | T-06 | AC-04 green |
| T-08 | BE | `SsrPurgeListener`: `profile.published.v1` + `username.renamed.v1`; old+new slug; sitemap on visibility | T-06 | AC-11 green; lost-event TTL heal <= 2 min |
| T-09 | BE | `CrawlerController`: static robots + paginated sitemap (canonical host, `lastmod`), 1 h cache + purge | T-08 | AC-05 green; zero-public-profile edge case valid |
| T-10 | BE | Preview: `mode=owner` reads (contract-tested), HTML + `SeoHeadModel` JSON, `no-store` + `noindex`, parity 404 for non-owner | T-05 | AC-08, AC-09 green |
| T-11 | BE | `/cv` forward contract: call shape for features 17-21; parity 404 until snapshots exist | T-05 | Absence test green; one call when present |
| T-12 | BE | Security wiring: CSP headers, host allowlist (cache keys per host), public-route WAF/rate rules, escaping re-validated at render | T-09, T-10 | Security drills green (no oracle, no poisoning, no schemes) |
| T-13 | BE | k6 P1 gate scripts + observability (`ssr.*` metrics, alerts per NFR-12) | T-07, T-12 | AC-03 numbers green incl. banner payload |
| T-14 | FE | Preview surfaces in the SPA: sandboxed iframe + badge; PurgeChip reused (no re-implementation); HEAD poll 5 s; `Still publishing` timeout copy | T-10 | AC-08 UI path + coordination drill green |
| T-15 | FE | SeoChecklist + fix deep links; SharePanel + client-side QR (canonical URL only) | T-14 | AC-09, AC-10 UI paths green |
| T-16 | BE | OpenAPI additive diff, folded parity checklist run (AC-12), contract + fragment test suite in CI | T-11, T-13 | AC-12 green; no unowned parity items |
| T-17 | DevOps | New container release gate: digest pin, SBOM, compose slice, blue/green deploy, rollback drill, Podman parity | T-13 | `docker run` + `podman run` cold boot; rollback <= 10 min |

Definition of done: AC-01..AC-13 green, six drills of `02-database-schema-design.md` green, k6 P1 gate green, release gate passes, legacy share-seo + research docs deleted (git history is the archive).
