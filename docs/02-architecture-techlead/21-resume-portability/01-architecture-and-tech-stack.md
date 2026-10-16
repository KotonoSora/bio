# Resume Portability - Architecture and Tech Stack (feature 21)

Phase 2 TL · Feature 21 of 53 (resume, final) · Extension of `resume-service` (export, import, adapters) - no new service, no new tables, no new container. Implements `docs/01-requirements-ba/21-resume-portability/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | Portability lands in `resume-service`, which owns documents/snapshots; the legacy monolith argument (import atomicity; adapters reuse the snapshot validator in-process) survives on the resume side. | Extract triggers (independent load, team, cadence) do not fire; an import service would distribute the atomicity this feature exists to guarantee. |
| D-2 | DEVIATION CARRIED: the legacy "one transaction over documents + snapshots + block relink" cannot span `resume_db` and `profile_db` in the baseline split. Imports run two ordered phases: documents + snapshots first (one transaction), block relink last (idempotent, per block, via a new internal create route). Block -> missing document is structurally impossible (relink requires the imported document to exist); a crash between phases leaves private documents and a same-key retry completes the relink without duplicating. Rejections still write zero rows (gates precede all writes; count-asserted). | The forbidden failure mode (orphan block) cannot occur; the residual failure degrades to retry-safe incompleteness, never corruption. |
| D-3 | The envelope is closed and versioned; wire containers stay snake_case (`schema_version`, `exported_at`, `payload_json`); content keys stay owned by their specs - `snapshot_json` per feature 17, resume block payload per feature 08 (`documentId`, `showContact`, `sectionOrder`). Kotlin DTOs are camelCase + Jackson snake_case strategy for envelope classes. | One wire format, no second dialect; content schemas are redefined nowhere. |
| D-4 | Public-landing rule (BA Q-01): honor `is_public` + re-resolve `published_version` by version number only when the target account has no public document; otherwise conflicting documents land private with a `public_conflict` warning. `is_public=false` rows always land private with `published_version=NULL` (feature 17 unpublish invariant). | Fresh-account moves stay lossless; existing accounts never get a silent overwrite or a second public row (`uniq_public_doc` can never fire). |
| D-5 | Adapters are draft-only and tokenless: LinkedIn accepts the export file only (URL tab deferred, BA Q-02); GitHub accepts a handle only. Mappings land `is_public=false` with `review_required` field paths and a provenance banner until the first Save Snapshot. | Adapter output can never reach the public CV except through the standard save + publish path. |
| D-6 | Avatars are out of resume scope entirely (BA Q-03; DEVIATION FLAGGED vs the legacy `avatar_key` reference decision): avatar is a profile asset (feature 09) and a key would not resolve across accounts anyway. | Scope guard wins; a copied key would fail confusingly in the target account. |
| D-7 | Fixture gate: `docs/02-architecture-techlead/21-resume-portability/fixture-resume.json` imports on an empty database every build; the round-trip test (fixture + one real resume) diffs ids/timestamps only. | Migratability is a gate, not a checklist item. |
| D-8 | Audit rides the baseline append-only shape per service (24-month retention): one row per export/import/adapter run (actor, action, target, timestamp; never content). | Exfiltration forensics start at the first download; the legacy single-audit picture maps to the per-service baseline shape. |
| D-9 | Retry reconciliation state is ephemeral Redis only: `resume:import:lock:{userId}` (single-flight) + `resume:import:{userId}:{idempotencyKey}` (run manifest, TTL 24 h) in resume-service's Redis db 8. | Retry-safety without staging tables (BR-05): state that would rot in a table lives and dies in a TTL. |
| D-10 | DEVIATION CARRIED (transport; folded from the legacy R2 delivery plan): the Workers-era adapter fetch + file handling runs as the baseline Boot service's bounded server-side client; the JSON/cookie/`user_id` contract is frozen. Recorded split trigger: sustained adapter-fetch backlog (rate limits, bulk backfills) extracts the fetch client behind a queue at P4 - `fetchBehind` stays an interface so the move is wiring-only. | Baseline stack in force; the extraction is wiring-only when load justifies it. |

## Component map

| Piece | Home | Note |
|---|---|---|
| Envelope types + streaming serializer | `resume-service` | Jackson snake_case; chunked writer with backpressure |
| Export endpoints | `resume-service` | Own rows + feature 08's resume-block read (landed in this change) |
| Import gates + write phase | `resume-service` | Gate pipeline; phase 2 via feature 08's internal create route (landed here) |
| Adapter mapping + fetch client | `resume-service` | Timeout 10 s / redirects 3 / cap 1MB / private-range egress blocked |
| Block rows (read + create) | `profile-service` | Additive internal routes landed in this change (TL 03) |
| Fixture + CI gate | repo + CI | Path above; a broken fixture fails `./gradlew build` |

## Boundaries

Owns: envelope, fixture, gate pipeline, dry-run, write-phase ordering + retry manifest, adapter mapping and SSRF guardrails, portability audit rows. Does not own: snapshot schema/sanitizer (feature 17), block payload validation (feature 08 - reused), templates/print (18), share links (20), purge execution (feature 02; this service's participant handler is unchanged), audit plumbing (baseline).

## Consumers

| Consumer | Contract |
|---|---|
| SPA wizard + adapter cards | TL 03 endpoints |
| CI pipeline | Fixture gate (D-7) |
| Support / forensics | Audit reconstruction (D-8) |

No events are emitted or consumed by this feature: imports land non-public (zero cache impact), adapters write only private drafts, and the fixture gate is build-time.
