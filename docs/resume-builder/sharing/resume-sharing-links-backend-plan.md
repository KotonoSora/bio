# Resume Sharing Backend Plan (five-type binding + purge fan-out — server build steps)

PLAN ref: P2-1 sharing (source rule, five types, uniform revocation, unlimited ungated) + S-1/S-2/S-5/S-6/S-7. BA source: `docs/resume-builder/sharing/resume-sharing-links.md`. Phasing per `docs/platform/delivery/execution-overview.md` (E2). Contracts in `docs/resume-builder/sharing/resume-sharing-links-database-plan.md` (source rule, derivation, cache keys, revocation — never redefined here). Share dialog in the sharing frontend plan (splits next to this file).

## Purpose

Give the backend developer an ordered procedure for binding one frozen snapshot to five link types without introducing a sixth system: source resolution, per-type delegation to owning machinery, the read-only share-links helper, purge fan-out wiring, and uniform revocation. Follow steps 1–7 in order. Each type reuses its owning major's machinery — this slice fixes the resume-specific rules so the five never drift apart.

## Users

- Backend developer: executes steps 1–7 in package `com.app.resume.sharing` (thin orchestration over owning services).
- QA: runs parity, privacy, short-link, IDOR, XSS, abuse, and secrets drills.
- Job seeker (via UI): copies any type, prints, revokes by unpublishing.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — modular monolith (orchestration package, no new module). Monolith pros: source resolution + helper derivation + purge fan-out execute in one request scope against owning services in-process; no new RLS surface (reads inherit owners' policies). Microservices cons avoided: a sharing service would duplicate read paths owned by five majors for derivation logic with zero write volume of its own. Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; none fire — this package has no state to extract. Starters: web, validation, security, actuator, micrometer. Load posture: derivation is pure computation (no extra queries beyond the owning reads callers already make); purge fan-out is bounded (5 key families per event); anonymous cv traffic rides the shared cache-first envelope (hit gate owned with the SSR majors).
2. Step 1 — `service/ShareSourceResolver.kt#resolve(documentId)`: load document (owner or public path) → require `is_public` + `published_version != null` + profile `is_public` → return the frozen version row; else `ShareNotPublic` (callers map to 404/safe-error/hidden per type — one decision point, five renderings). Draft edits are unreachable here (no draft row exists to resolve).
3. Step 2 — per-type binding (delegation, not duplication): standard `/:username/cv` → templates SSR with the resolved snapshot (rendering owned by the templates backend plan); SEO → identical URL, tags verified (no separate SEO URL exists — "SEO link" is the standard link passing the tag checklist); shortened → call the shortener mint service (destination = canonical cv URL, `expires_at` per database plan Q2 default persistent; slugs carry no PII; stats ride the url-shortener analytics); custom → verified-host serving via the account domain machinery (unverified hosts never render — enforced upstream); bio-embed → `resume`-block visibility rule (hidden block or unpublished document removes the section, page intact).
4. Step 3 — `GET /api/v1/documents/:id/share-links` (read-only helper, owner-scoped): derives the five URLs for dashboard copy buttons (standard, SEO-verified-same, shortened (mints on first request per document, then returns the stable short URL — idempotent by (document_id) lookup, never a mint loop), custom (if bound), bio-embed anchor). No share-create API exists (URLs derive from selection, not from rows).
5. Step 4 — purge fan-out `SharingPurgeListener`: on snapshot-save / template-select / publish / unpublish / username-change / domain bind-unbind → purge affected keys across families (cv slug keys × host keys × short-link resolve cache (destination re-derivation, not content purge) × block-embed page keys) within the purge SLA. Miss-storm single-flight preserved on cv paths.
6. Step 5 — uniform revocation: the two-flag flip (`is_public`, `published_version`) flows through the resolver (step 1) so every type degrades correctly with zero per-type code: 404 standard/SEO/custom/bio, safe-error shortened. Unlimited views/mints-for-own-resumes/exports — no quotas, credits, or plan gates (abuse controls live on anonymous paths via owning majors' Turnstile + WAF/rate rules, S-5; legitimate bursts unaffected).
7. Step 6 — in-browser A4 PDF view: dashboard preview renders the frozen snapshot in an A4 frame with the print CSS (templates plans); guardrail holds structurally — `window.print()` only, no server PDF engine, no stored PDF bytes (dependency grep gate shared with the templates slice).
8. Step 7 — tests + container + secrets: parity test (five types, curl, no JS, identical text); privacy matrix (private/unlinked → 404/safe-error/hidden; re-publish restores; rename 301s); short-link test (mint resolves canonical; expiry/disabled → safe error; slug PII-free); IDOR/XSS/abuse probes per BA matrix; secrets via env only (`.gitignore` covers `.env`/overrides — the wrangler-era secret stores from the BA text do not apply to the containerized stack). Same image via `docker run` + `podman run` per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`.

## Data / API touches

- Schema: database plan above (zero new tables; reads owned rows). Endpoints: `GET /api/v1/documents/:id/share-links` → `200 {standard, seoSameAsStandard:true, shortened, custom?, bioAnchor}` (owner-scoped; shortened mints-on-first-request, idempotent); all other behavior through owning majors' endpoints (shortener mint/stats, domain verify, block link-document). Cache: per-host+revision keys, fan-out matrix in step 4.

## Acceptance criteria

- Parity drill: five types identical text; layout-only template variance; drafts invisible.
- Privacy drill: full unpublish matrix green; re-publish restores; rename 301s.
- Short-link drill: first helper call mints, second returns stable URL (no mint loop — mint-count asserted); expiry → safe error; slug clean.
- Revocation drill: one flip covers all five within SLA (single-resolver proof).
- Abuse drill: anonymous cv/short/custom bursts throttled via owning majors; legitimate unlimited use unaffected.
- Container drill: digest pair boots, compose green, rollback green, contract diff clean.

## Out of scope

- Source/cache contracts (database plan above); share dialog UI (frontend plan); versioning internals (snapshot backend plan); SSR/tag mechanics (templates + share-seo backend plans); shortener/QR/stats mechanics (url-shortener plans); domain verification (account major); block rendering (link-blocks plans); plan gates/AI (P3); publishing/agency (P4); replicas/ISR (P5).

## Open questions

- Q1: Single public snapshot (decided single — see snapshot plans)?
- Q2: Persistent short links default (decided persistent — see database plan Q2)?
- Q3: Custom-host cv path (open — needs account-major input)?
