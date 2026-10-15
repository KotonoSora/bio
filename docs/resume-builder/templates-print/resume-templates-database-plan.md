# Resume Templates Database Plan (consumed columns + code registry — no new tables)

PLAN ref: P2-1 (token templates; screen + print from one snapshot) + S-2. BA source: `docs/resume-builder/templates-print/resume-templates.md`. Consumed by the templates backend plan (registry + render pipeline; splits next to this file). Document rows live in `docs/resume-builder/documents/resume-snapshot-database-plan.md` (single `CREATE TABLE`s, never altered here). No migration — this slice creates zero tables.

## Purpose

Document the storage this slice touches so developers know exactly where template choice lives and where it does not: one consumed column, a code registry, static assets, and the render contract both screen and SSR share. No `CREATE TABLE` here by design — a template table would let styling drift from the renderer that interprets it.

## Users

- Backend developer: reads `template_id`, renders through the registry in code.
- QA: verifies same-snapshot-identical-text across templates and the no-new-table rule.
- Template contributor (internal): adds a token-based template without touching user-data code.

## Functional requirements

1. Consumed column (defined once in the snapshot database plan): `resume.documents.template_id` (registry id as plain text — deliberately not a DB FK, since templates ship as code; a FK would couple deploys to data). Written only via `PUT /api/v1/documents/:id` (template selection); never by render paths.
2. Template registry lives in code (`com.app.resume.templates.TemplateRegistry`: `{id, name, tokens{font, scale, accent, density}, screenComponent, printStylesheet, thumbnail}`), never as rows — adding a template is a code change + review + thumbnail asset. Switching `template_id` re-renders the same snapshot; it never mutates snapshot rows (render inputs are read-only by construction).
3. Static assets: `templates/<template_id>/` (screen component bundle, print stylesheet, thumbnail image) served immutable + fingerprinted; thumbnails render from the same pipeline with placeholder content only (never a real user's snapshot — the placeholder rule from format-sharing applies to built-ins too).
4. Render inputs (read-only): `snapshot_json + template_id` from the document row; profile display name for the cv header (public path only, `is_public` enforced upstream). Render outputs (screen DOM, print tree, SSR HTML) are never persisted — no rendered-output table, no HTML cache column (edge cache holds SSR renegotiations, owned by the sharing plans).
5. Print pipeline storage: none. `window.print()` runs on the client's frozen-snapshot DOM; no PDF bytes touch server or blob storage in v1 (the no-server-PDF guardrail, structural: no PDF library in the dependency catalog, asserted by grep gate).
6. Pro-gated premium templates (if applied): entitlement checked against the plan ledger at render/selection time (server-side, owned by monetize majors); the gate is a boolean on the registry entry (`premium: true`), not a table.

## Data / API touches

- DDL: none — assertion: `grep -rn "CREATE TABLE" docs/resume-builder/templates-print/` returns only the format-sharing database plan (next to this file). Column consumed: `resume.documents.template_id` (snapshot database plan). Registry: code + fingerprinted static assets.

## Acceptance criteria

- No-table drill: schema diff before/after this slice shows zero tables, zero columns.
- Parity drill: same snapshot across all templates → identical text, layout-only differences (automated text-extract comparison).
- Registry drill: unknown `template_id` on read falls back to `classic` with a metric (never 500s on renamed templates); unknown id on write → 422.
- Asset drill: thumbnails contain placeholder content only (pixel-scan on fixtures, no personal-data keys in the render input).
- Violation drill: any template/persistence table, per-user CSS column, or server-PDF dependency fails review.

## Out of scope

- Registry code and render pipeline (backend plan); gallery/preview/print screens (frontend plan); document/version DDL (snapshot database plan); shared format authoring (format-sharing plans); five-link matrix (sharing plans); backup/adapters (portability plans); AI JD-tailor (P3); publishing/agency (P4); replicas/ISR (P5).

## Open questions

- Q1: v1 template count — 3 classic layouts or single layout + density options (BA Q1)?
- Q2: Cover-letter print — second page of the same template or a separate `template_id` (BA Q2)?
