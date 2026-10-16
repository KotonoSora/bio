# Resume Sharing Links - Architecture and Tech Stack (feature 20)

Phase 2 TL · Orchestration: `resume-service` extension (helper endpoints; no new service, no new tables, no new container). The five types render through their owning paths. Implements `docs/01-requirements-ba/20-resume-sharing-links/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | Orchestration lands in `resume-service` - the service that owns the source document - behind two owner-scoped helper endpoints. DEVIATION CARRIED: the legacy modular-monolith orchestration package is superseded by the baseline microservices (in force), but the legacy verdict survives in spirit: pure derivation + thin orchestration justify no sharing service of their own. | Zero write volume, zero state to extract: the split triggers (independent load, team, release cadence) do not fire. |
| D-2 | Binding by delegation: standard/SEO render through feature 10's `/cv` (feeding on feature 17's `published` read); shortened through link-service; custom through feature 04's verified-host serving; bio embed through feature 08's block pipeline. This feature owns only the resume-specific rules for each. | Five owning systems already exist; the failure mode to prevent is a sixth system that drifts. |
| D-3 | Derivation, not storage: helper output is computed from (document flags + username + verified hosts + slug + block state); no share-URL table/column exists anywhere (grep assertion in TL 02). | A stored URL goes stale on rename/republish; derivation cannot. |
| D-4 | One resolver rule: `ShareSourceResolver` checks `is_public` + `published_version != null` (+ profile state on render paths) and returns the frozen version; callers map one decision to five renderings. | Uniform revocation is a single code path, not five switches; the matrix test proves it. |
| D-5 | Idempotent mint: link-service gains resume-ref columns (`V2__resume_refs.sql`, landed in this change) + `POST /internal/v1/links/resume-share` (mint-or-get by `(userId, documentId)`) + a `username.renamed.v1` consumer that rewrites destinations (slug never re-minted; the old-username 301 is the lost-event backstop). | One row per document, rename-proof links, no quota-loop surface. |
| D-6 | The A4 PDF view reuses feature 18's print pipeline verbatim (frozen-bind + `window.print()` + no-PDF greps); the bio toggle reuses feature 17/08's linkage routes. This feature adds no renderer and no local PDF code. | One print path, one linkage path - no parallel implementations to drift. |
| D-7 | Cross-additive rows landed in this change: feature 04's `GET /internal/v1/domains/by-user/{user_id}` (share derivation needs the owner's verified host) and the feature 29 rows above. | The helper must not query other services' databases; internals are the only path. |

## Purge fan-out ownership (who purges what on which event)

| Event | Keys purged | Owner |
|---|---|---|
| `resume.cv_changed.v1` (publish/unpublish/template switch) | `ssr:*:{slug}:cv` all hosts | public-site (landed: features 17-18 rows) |
| `username.renamed.v1` | old + new slug keys; link-service rewrites resume-share destinations + purges `links:code:{code}` | public-site (landed 10) + link-service (lands this change) |
| `domain.state_changed.v1` (bind/suspend/remove) | `ssr:{host}:*` keys | public-site (landed 04) |
| `profile.published.v1` (visibility/block changes) | slug keys incl. bio page | public-site (landed 06-10) |

TTLs are the backstop, never the primary path; the revocation SLA (`<= 60 s`) rides here.

## Boundaries

This feature owns: the resolver rule, the two helper endpoints, the resume-ref extension semantics (columns + route + rename consumer contracts), the derivation/support matrices, and the share-dialog/A4/bio FE contracts. It does not own: SSR/tags/cache (10), shortener resolve/QR/stats (29-31), domain verification/serving (04), block rendering/validation (08), snapshot versioning (17), templates/print (18).

## Consumers

| Consumer | Contract |
|---|---|
| SPA dialog / A4 view / bio toggle | Helper endpoints + feature 17/08 linkage routes + feature 18 print pipeline |
| Support tooling | The support matrix (TL 02) as the single answer sheet |
| link-service (new inbound) | `username.renamed.v1` rewrite rule (registered in this change) |

## Stack notes

Spring Boot `4.1.1` / Kotlin `2.4.21` unchanged; baseline starters only; no new Redis keys (derivation is cacheless; the helper rides the internal reads' own caches). `ShareSourceResolver` and the URL composer are pure functions, table-tested. No new container; no config beyond the default-host value (already present for feature 10).
