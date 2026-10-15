# Resume Templates Backend Plan (registry + screen/print render — server build steps)

PLAN ref: P2-1 (token templates, screen interactivity, `@media print` A4, `window.print()` only, no server PDF) + P1c-1/P1c-2 via sharing (cv SSR tags + cache envelope) + S-2/S-7. BA source: `docs/resume-builder/templates-print/resume-templates.md`. Phasing per `docs/platform/delivery/execution-overview.md` (E2). Storage in `docs/resume-builder/templates-print/resume-templates-database-plan.md` (consumed column + code registry; zero new tables). Snapshot rows in `docs/resume-builder/documents/resume-snapshot-database-plan.md`. Gallery/print screens in the templates frontend plan (splits next to this file).

## Purpose

Give the backend developer an ordered procedure for resume rendering: the token-only template registry, the screen/print render split, the SSR reuse that guarantees text parity, and the proof that no server PDF engine exists. Follow steps 1–7 in order. Edit state and print markup stay separate at every layer.

## Users

- Backend developer: executes steps 1–7 in package `com.app.resume.templates`.
- QA: runs text-parity, print-fidelity, injection, and dependency-gate drills.
- Template contributor (internal): adds templates without touching user-data code.

## Functional requirements

1. Stack and topology (mandatory): PG `18.6` + Boot `4.1.1` + Kotlin `2.4.21` + Gradle `9.8.1` wrapper-only, per `docs/platform/stack/spring-boot-baseline.md`, `docs/platform/stack/postgres-baseline.md`, `docs/platform/stack/kotlin-baseline.md`, `docs/platform/stack/gradle-build.md`. Architecture verdict — modular monolith (`resume` module). Monolith pros: template selection rides the existing document update path; SSR rendering reads snapshot + registry in one request scope; one release train. Microservices cons avoided: a render service would duplicate the snapshot-read contract and double cache coordination for CPU-cheap template expansion. Split needs ≥2 triggers from `docs/platform/architecture/architecture-decision.md`; none fire. Starters: web, validation, data-jpa, security, actuator, micrometer (+ thymeleaf fragments for cv SSR, same flagged addition pattern as the bio SSR slice).
2. Step 1 — `templates/TemplateRegistry.kt`: hardcoded map `{id, name, tokens{font, scale, accent, density}, premium: Boolean}` validated at boot (ids unique; every token consumed by both screen and print renderers — an unused token fails the registry test, preventing dead options). Unknown `template_id` on read falls back to `classic` with metric `template.fallback`; on write → `422 unknown_template`.
3. Step 2 — render split: screen renderer (interactive: section collapse, zoom, density toggle — dashboard SPA components bound to the frozen snapshot, never to draft state) and print renderer (separate read-only tree derived from the snapshot at print time: single-column A4 flow, `print-color-adjust: exact`, page-break rules that avoid splitting experience entries, zero dashboard chrome). Both consume `snapshot_json + tokens` and nothing else — user fields can never carry inline `style` (renderer escapes all strings; style attributes from data are dropped, asserted per template).
4. Step 3 — `PUT /api/v1/documents/:id {template_id}`: validate registry id → premium check against the plan ledger server-side (`403 template_forbidden` on free — UI hiding is not the gate) → update column → audit → publish purge event (cv SSR re-renders). Snapshots untouched (selection is metadata, never a version).
5. Step 4 — cv SSR reuse: `GET /:username/cv` renders server-side through the SAME template + published snapshot the dashboard preview uses (shared fragment functions, not duplicated markup) so curl-visible HTML matches printed text (text parity, not pixel parity). Tags (title/meta/canonical/OG/Twitter + JSON-LD `Person`) per the sharing plans' cache envelope; private/unpublished → 404.
6. Step 5 — no-server-PDF guardrail (structural): PDF library set is empty — no Puppeteer, headless-browser, or LibreOffice packages in the dependency catalog (grep gate in CI, not a policy note); export is `window.print()` chosen by the user (Save-as-PDF). Any PR adding a PDF dependency fails the build.
7. Step 6 — abuse and edge wiring: Turnstile + rate limits on template-selection writes (S-5, anonymous-adjacent via dashboard session expiry); S-2 strip + `default-src 'self'` CSP on dashboard preview and cv SSR; secrets via env only.
8. Step 7 — tests + container: text-parity test (same snapshot × all templates → identical extracted text); print test (A4 single-column, no chrome, colors exact — Chrome + Safari print-preview fixtures); injection test (script/iframe/event-handler in every resume field → stripped on screen, print, and SSR); fallback test (renamed template id reads `classic` + metric); dependency gate (no PDF packages); same image via `docker run` + `podman run` per `docs/platform/containers/container-lifecycle.md`, gated per `docs/platform/delivery/release-gates.md`; k6 relevance: cv SSR rides the shared cache-first envelope (hit gate owned by the sharing plans).

## Data / API touches

- Schema: database plan above (reads `snapshot_json + template_id`; writes `template_id` only). Endpoint: `PUT /api/v1/documents/:id {template_id}` → `200` / `404 document_not_found` / `422 unknown_template` / `403 template_forbidden`. SSR: `GET /:username/cv` (published version via the snapshot backend plan's resolution; private → 404).

## Acceptance criteria

- Parity drill: text-extract identical across templates; layout-only diffs.
- Print drill: A4 flow, no chrome, exact colors, unbroken experience entries (Chrome + Safari fixtures).
- Print-source drill: dashboard `window.print()` emits the frozen snapshot, never unsaved drafts.
- Curl drill: cold `curl /:username/cv` contains resume text + full tag set + JSON-LD.
- Injection drill: hostile fields stripped on all three surfaces; CSP holds.
- Gate drill: dependency grep clean; free direct premium-template PUT → 403.
- Container drill: digest pair boots, compose green, rollback green, contract diff clean.

## Out of scope

- Registry/storage contracts (database plan above); gallery/preview screens (frontend plan); versioning and publish pointers (snapshot backend plan); five-link matrix and cache mechanics (sharing plans); backup/adapters (portability plans); shared format authoring (format-sharing plans); AI JD-tailor (P3); publishing/agency (P4); replicas/ISR (P5).

## Open questions

- Q1: v1 count — 3 layouts or single + density (see database plan Q1)?
- Q2: Cover-letter print — second page vs separate template (see database plan Q2)?
