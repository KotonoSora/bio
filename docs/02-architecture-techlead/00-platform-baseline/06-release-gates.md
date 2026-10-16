# Platform Baseline - Release Gates

Phase 2 TL · Cross-cutting baseline. One acceptance path per release tag: same digest verified under Docker and Podman, migrated from empty, contract-checked, security-probed, load-gated per phase. No tag reaches staging or production on a partial gate. Containers: `04-containers-and-compose.md`; drills inventory: `05-delivery-phasing-and-scaffold.md`.

## Gates (run in order; all required)

| Gate | Checks | Blocks when |
|---|---|---|
| G1 Build | Clean-checkout `./gradlew build` + `integrationTest` green; Boot `4.1.1` / Kotlin `2.4.21` / Gradle `9.8.1` / JVM 21 asserted; dependency output archived; CycloneDX SBOM scanned | Any high/critical finding (waiver requires a signed, time-boxed release-note entry); wrapper checksum mismatch; test skip |
| G2 Migration | Fresh-apply Flyway replay from empty PG `18.6` for every service; upgrade replay from the previous tag's schema; drift check (no manual SQL) | Replay failure; missing backward-compatibility (previous digest must boot on the new schema) |
| G3 Container pair | Same digests boot under Docker and Podman (rootless); healthchecks green; smoke suite (public page, beacon 202, owner read, login redirect) passes; SIGTERM drain verified | Runtime divergence; health flap; smoke failure |
| G4 Contract | OpenAPI diff additive-only for `/api/v1` and `/internal/v1`; stable `code` strings (no renames); event schemas versioned (`<domain>.<fact>.v<major>`) | Breaking change; removed field; renamed code |
| G5 Security | `08-security-baseline.md` probe set: IDOR probes (cross-user reads/writes denied), rate rules active, no secrets in images/manifests/logs, upload/webhook integrity checks per owning feature | Any probe failure; secret hit |
| G6 Load (per phase) | Seeded k6 per increment floor: reads p95 < 500 ms, checkout p95 < 1 s at the phase's mixed load; queue lag bounded (no unbounded growth over the run) | p95 breach; queue divergence over 10-min sustained |
| G7 Docs drift | Feature drills from the increment's AC set green (spot-checked suite grows per increment); deviation log reconciled | Missing drill result with the tag |

## Promotion rules

- Promotion = digest swap to staging, re-run G3 smoke + G5 probes, then the same digest to production; environments differ only in secrets and scale, never in config keys.
- Release note per tag: feature numbers, images + digests, Flyway versions, drill results (G1-G7), waivers (signed, time-boxed), rollback target (previous digest).
- Rollback: previous digest + current schema (guaranteed by G2's compatibility rule) + cache purge where the owning spec declares one; rollback itself is logged with the touched tags.
- Hotfix lane: G1-G4 mandatory, G5 fast subset (IDOR + secret scan + rate check); G6 deferred only with a written time-box and a follow-up gate run within one week.

## Standing drills

- Quarterly: restore-to-scratch (RTO proof), secret rotation drill (internal JWT dual-key + one provider secret), cache-flush boot.
- Per release: freshness check that the previous two tags' smoke suites still pass against the current schema (regression floor).
- Archive: gate outputs, SBOMs, release notes, and drill logs are archived per tag; support reproduces any report from tag alone.
