# Resume Templates - Architecture and Tech Stack (feature 18)

Phase 2 TL · Extends `resume-service` (registry + selection validation) and the SPA (screen/print renderers); zero new services, zero new tables. Implements `docs/01-requirements-ba/18-resume-templates/`. Baseline: `docs/02-architecture-techlead/00-platform-baseline/`.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D-1 | The registry is code in `resume-service` (`TemplateRegistry`: `{id, name, tokens, premium}`), validated at boot: unique ids, every token consumed by both renderers. No template table exists - a row table would let styling drift from the renderer that interprets it. | Legacy doctrine preserved; a registry entry is a code change + review + asset. |
| D-2 | Render split stays three-way: interactive screen tree (SPA components bound to a frozen snapshot), read-only print tree (derived at print time), and cv SSR (public-site text render). All three consume `snapshot_json + registry tokens` read-only. | Edit state and print markup stay separate at every layer; no renderer can mutate data. |
| D-3 | Catalog read `GET /api/v1/resume-templates` is served from the registry; the SPA gallery and feature 17's picker consume it. Catalog and renderers ship in one bundle; a CI consistency gate fails the build on any registry id without renderer/thumbnail. | "New template appears with no client change" cannot hold for SPA-bundled components - the honest equivalent is a build-time consistency guarantee. |
| D-4 | Selection rides feature 17's `PUT /api/v1/documents/{id}` with two additions: registry validation (`422 unknown_template`) and the premium gate (`403 template_forbidden`, server-side, fail-closed). A successful switch emits `resume.cv_changed.v1`. | One write path, one event family; no parallel selection endpoint. |
| D-5 | Print binds the frozen snapshot id at preview-open; a save mid-preview shows the re-open notice; the print button lives on frozen previews only. | Printing drafts is impossible by construction, not by copy. |
| D-6 | No-PDF is structural on both sides: server dependency set greps clean of PDF/headless packages; the client bundle greps clean of jsPDF/html2canvas; the print path issues zero network calls. | Legacy guardrail preserved and extended client-side. |

DEVIATION CARRIED: the legacy "worker SSR imports the same template renderer" (shared Workers-safe module) is superseded by the baseline service split - public-site renders cv text with the template's layout cues, and text parity (not pixel parity) remains the frozen contract.

## Boundaries

This feature owns: registry + boot validation, catalog endpoint, selection validation codes, screen/print renderer contracts, thumbnail pipeline, no-PDF gates. It does not own: document/version tables (feature 17), SSR tags/caching (`/cv` mechanics, feature 10), plan values or claim text (features 03/32-40), share formats (19), share links (20), portability (21).

## Consumers

| Consumer | Contract |
|---|---|
| SPA editor (feature 17 frontend) | Catalog read + picker embed; selection via the document PUT; print tree on frozen previews |
| public-site-service (feature 10) | `templateId` from the published payload used as layout cues for text-parity render (edit landed on 10's `/cv` row) |
| Feature 03 claim registry (forward) | Enabling a premium template requires a claims-registry update first (Q-03); this feature ships the gate, not the claim |
| Feature 21 (forward) | Export round-trip keeps `templateId` as plain text (registry id, no FK semantics) |

## Stack notes

Spring Boot `4.1.1` / Kotlin `2.4.21` unchanged; baseline starters only. Registry validation in `TemplateRegistryTests` (boot test); thumbnails built by a fixture-only pipeline (placeholder inputs typed distinctly - real snapshots cannot be passed); print stylesheets are plain CSS per template under the SPA asset tree, fingerprinted. No new Redis keys; catalog served from memory (registry is deploy-versioned).
